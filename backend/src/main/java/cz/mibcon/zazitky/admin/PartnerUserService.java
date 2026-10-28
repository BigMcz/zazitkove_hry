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

import cz.mibcon.zazitky.admin.PartnerUserDtos.CreatePartnerUserRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerUserDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.SetShareRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.ShareDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.UpdatePartnerRequest;
import cz.mibcon.zazitky.admin.domain.PartnerUser;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.PartnerRepository;
import cz.mibcon.zazitky.game.PartnerShareRepository;
import cz.mibcon.zazitky.game.domain.Partner;
import cz.mibcon.zazitky.game.domain.PartnerShare;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Partners (castles) with their share history and the accounts of their staff, managed by the admin. */
@Service
@Transactional
public class PartnerUserService {

    /** Shares are valid from a calendar day in the castles' local time. */
    static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    private final PartnerUserRepository users;
    private final PartnerRepository partners;
    private final PartnerShareRepository shares;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String adminUsername;

    public PartnerUserService(PartnerUserRepository users, PartnerRepository partners, PartnerShareRepository shares,
            PasswordEncoder passwordEncoder, Clock clock, @Value("${app.admin.username:admin}") String adminUsername) {
        this.users = users;
        this.partners = partners;
        this.shares = shares;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminUsername = adminUsername;
    }

    /** For Spring Security's login. */
    @Transactional(readOnly = true)
    public PartnerUserDetails loadForLogin(String username) {
        PartnerUser user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown partner user"));
        return new PartnerUserDetails(user.getId(), user.getPartner().getId(), user.getPartner().getName(),
                user.getUsername(), user.getPasswordHash(), user.isEnabled());
    }

    public void recordLogin(UUID userId) {
        users.findById(userId).ifPresent(user -> user.setLastLoginAt(clock.instant()));
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
    public List<ShareDto> listShares(UUID partnerId) {
        return shares.findByPartnerIdOrderByValidFrom(partnerId).stream().map(PartnerUserService::toDto).toList();
    }

    public PartnerDto updatePartner(UUID partnerId, UpdatePartnerRequest request) {
        Partner partner = findPartner(partnerId);
        partner.setName(request.name().strip());
        partner.setContactEmail(request.contactEmail() == null || request.contactEmail().isBlank()
                ? null
                : request.contactEmail().strip());
        return toDto(partner, shares.findByPartnerIdOrderByValidFrom(partnerId));
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
        shares.save(share);
        return toDto(partner, shares.findByPartnerIdOrderByValidFrom(partnerId));
    }

    public PartnerDto deleteShare(UUID partnerId, UUID shareId) {
        Partner partner = findPartner(partnerId);
        PartnerShare share = shares.findByIdAndPartnerId(shareId, partnerId)
                .orElseThrow(() -> new NotFoundException("Podíl nebyl nalezen."));
        shares.delete(share);
        shares.flush();
        return toDto(partner, shares.findByPartnerIdOrderByValidFrom(partnerId));
    }

    private Partner findPartner(UUID partnerId) {
        return partners.findById(partnerId).orElseThrow(() -> new NotFoundException("Partner nebyl nalezen."));
    }

    @Transactional(readOnly = true)
    public List<PartnerUserDto> listUsers() {
        return users.findAllWithPartner().stream().map(PartnerUserService::toDto).toList();
    }

    public PartnerUserDto create(CreatePartnerUserRequest request) {
        Partner partner = partners.findById(request.partnerId())
                .orElseThrow(() -> new NotFoundException("Partner nebyl nalezen."));
        String username = normalize(request.username());
        if (username.equals(normalize(adminUsername)) || users.existsByUsername(username)) {
            throw new BadRequestException("Uživatelské jméno " + username + " už existuje.");
        }

        PartnerUser user = new PartnerUser();
        user.setPartner(partner);
        user.setUsername(username);
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? null
                : request.displayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        users.save(user);
        return toDto(user);
    }

    public void setPassword(UUID userId, String password) {
        find(userId).setPasswordHash(passwordEncoder.encode(password));
    }

    /** A disabled account cannot log in; an already open session lasts until it expires or logs out. */
    public void setEnabled(UUID userId, boolean enabled) {
        find(userId).setEnabled(enabled);
    }

    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        PartnerUser user = find(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Současné heslo nesouhlasí.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private PartnerUser find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Účet nebyl nalezen."));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private PartnerDto toDto(Partner partner, List<PartnerShare> partnerShares) {
        LocalDate today = LocalDate.now(clock.withZone(BILLING_ZONE));
        BigDecimal current = partnerShares.stream()
                .filter(share -> !share.getValidFrom().isAfter(today))
                .reduce((first, second) -> second)
                .map(PartnerShare::getSharePct)
                .orElse(null);
        return new PartnerDto(partner.getId(), partner.getName(), partner.getContactEmail(), current,
                partnerShares.stream().map(PartnerUserService::toDto).toList());
    }

    private static ShareDto toDto(PartnerShare share) {
        return new ShareDto(share.getId(), share.getValidFrom(), share.getSharePct());
    }

    private static PartnerUserDto toDto(PartnerUser user) {
        return new PartnerUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getPartner().getId(),
                user.getPartner().getName(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
