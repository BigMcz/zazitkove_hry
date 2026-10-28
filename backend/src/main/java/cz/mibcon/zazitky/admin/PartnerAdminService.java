package cz.mibcon.zazitky.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.admin.PartnerAdminDtos.PartnerDto;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.PartnerRequest;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.SetShareRequest;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.ShareDto;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.PartnerRepository;
import cz.mibcon.zazitky.game.PartnerShareRepository;
import cz.mibcon.zazitky.game.domain.Partner;
import cz.mibcon.zazitky.game.domain.PartnerShare;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Partners (castles): company details and the history of their share of the revenue. */
@Service
@Transactional
public class PartnerAdminService {

    /** Shares are valid from a calendar day in the castles' local time. */
    static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    private final PartnerRepository partners;
    private final PartnerShareRepository shares;
    private final Clock clock;

    public PartnerAdminService(PartnerRepository partners, PartnerShareRepository shares, Clock clock) {
        this.partners = partners;
        this.shares = shares;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PartnerDto> listPartners() {
        Map<UUID, List<PartnerShare>> sharesByPartner = shares.findAllByOrderByValidFrom().stream()
                .collect(Collectors.groupingBy(share -> share.getPartner().getId()));
        return partners.findAll(Sort.by("name")).stream()
                .map(partner -> toDto(partner, sharesByPartner.getOrDefault(partner.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerDto getPartner(UUID partnerId) {
        return toDto(findPartner(partnerId));
    }

    /** A new partner has no share yet — it is set with {@link #setShare}. */
    public PartnerDto createPartner(PartnerRequest request) {
        Partner partner = new Partner();
        apply(partner, request);
        partners.saveAndFlush(partner);
        return toDto(partner);
    }

    public PartnerDto updatePartner(UUID partnerId, PartnerRequest request) {
        Partner partner = findPartner(partnerId);
        apply(partner, request);
        partners.flush();
        return toDto(partner);
    }

    @Transactional(readOnly = true)
    public List<ShareDto> listShares(UUID partnerId) {
        return shares.findByPartnerIdOrderByValidFrom(partnerId).stream().map(PartnerAdminService::toDto).toList();
    }

    /**
     * Share valid from a date until the next entry; a share for an existing date is replaced.
     * A date in the past changes the settlement of activations that may already be invoiced.
     */
    public PartnerDto setShare(UUID partnerId, SetShareRequest request) {
        Partner partner = findPartner(partnerId);
        PartnerShare share = shares.findByPartnerIdAndValidFrom(partnerId, request.validFrom())
                .orElseGet(() -> {
                    PartnerShare created = new PartnerShare();
                    created.setPartner(partner);
                    created.setValidFrom(request.validFrom());
                    return created;
                });
        share.setSharePct(request.sharePct());
        shares.saveAndFlush(share);
        return toDto(partner);
    }

    public PartnerDto deleteShare(UUID partnerId, UUID shareId) {
        Partner partner = findPartner(partnerId);
        PartnerShare share = shares.findByIdAndPartnerId(shareId, partnerId)
                .orElseThrow(() -> new NotFoundException("Podíl nebyl nalezen."));
        shares.delete(share);
        shares.flush();
        return toDto(partner);
    }

    private void apply(Partner partner, PartnerRequest request) {
        String companyId = blankToNull(request.companyId());
        // "id <> null" would match nothing, so a new partner is checked without the id condition
        boolean taken = companyId != null && (partner.getId() == null
                ? partners.existsByCompanyId(companyId)
                : partners.existsByCompanyIdAndIdNot(companyId, partner.getId()));
        if (taken) {
            throw new BadRequestException("Partner s IČ " + companyId + " už existuje.");
        }
        partner.setName(request.name().strip());
        partner.setCompanyId(companyId);
        String vatId = blankToNull(request.vatId());
        partner.setVatId(vatId == null ? null : vatId.toUpperCase(Locale.ROOT));
        partner.setContactEmail(blankToNull(request.contactEmail()));
        partner.setContactPerson(blankToNull(request.contactPerson()));
        partner.setPhone(blankToNull(request.phone()));
    }

    private Partner findPartner(UUID partnerId) {
        return partners.findById(partnerId).orElseThrow(() -> new NotFoundException("Partner nebyl nalezen."));
    }

    private PartnerDto toDto(Partner partner) {
        return toDto(partner, partner.getId() == null ? List.of() : shares.findByPartnerIdOrderByValidFrom(partner.getId()));
    }

    private PartnerDto toDto(Partner partner, List<PartnerShare> partnerShares) {
        LocalDate today = LocalDate.now(clock.withZone(BILLING_ZONE));
        BigDecimal current = partnerShares.stream()
                .filter(share -> !share.getValidFrom().isAfter(today))
                .reduce((first, second) -> second)
                .map(PartnerShare::getSharePct)
                .orElse(null);
        return new PartnerDto(partner.getId(), partner.getName(), partner.getCompanyId(), partner.getVatId(),
                partner.getContactEmail(), partner.getContactPerson(), partner.getPhone(), current,
                partnerShares.stream().map(PartnerAdminService::toDto).toList());
    }

    private static ShareDto toDto(PartnerShare share) {
        return new ShareDto(share.getId(), share.getValidFrom(), share.getSharePct());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
