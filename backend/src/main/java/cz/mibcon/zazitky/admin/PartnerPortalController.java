package cz.mibcon.zazitky.admin;

import java.util.List;

import cz.mibcon.zazitky.accesscode.AccessCodeAdminService;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.StockCountRequest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.Summary;
import cz.mibcon.zazitky.admin.PartnerUserDtos.ChangeOwnPasswordRequest;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.ShareDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overview for castle staff (role PARTNER). Everything is limited to the partner of the logged-in
 * account — the partner id comes from the login, never from the request.
 */
@RestController
@RequestMapping("/api/v1/partner")
public class PartnerPortalController {

    private final AccessCodeAdminService accessCodes;
    private final PartnerUserService partnerUsers;
    private final PartnerAdminService partners;

    public PartnerPortalController(AccessCodeAdminService accessCodes, PartnerUserService partnerUsers,
            PartnerAdminService partners) {
        this.accessCodes = accessCodes;
        this.partnerUsers = partnerUsers;
        this.partners = partners;
    }

    @GetMapping("/reports/monthly-activations")
    public List<MonthlyActivation> monthlyActivations(@AuthenticationPrincipal PartnerUserDetails partner,
            @RequestParam @Min(2020) @Max(2100) int year) {
        return accessCodes.monthlyActivations(year, partner.partnerId());
    }

    /**
     * Deliveries of cards to the castle: only handed-over batches (cards still in production are not
     * the castle's yet); our development batches (multi-session) are left out as in the report.
     */
    @GetMapping("/batches")
    public List<BatchSummary> batches(@AuthenticationPrincipal PartnerUserDetails partner) {
        return accessCodes.listBatches(partner.partnerId(), true).stream()
                .filter(batch -> !batch.multiSession())
                .toList();
    }

    /** Own key figures: cards in stock and delivered, activations and money; without the operator's profit. */
    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal PartnerUserDetails partner,
            @RequestParam @Min(2020) @Max(2100) int year) {
        return accessCodes.summary(year, partner.partnerId()).withoutProfit();
    }

    /** Own count of the cards in stock per game (at most our number); returns the updated key figures. */
    @PostMapping("/stock-count")
    public Summary stockCount(@AuthenticationPrincipal PartnerUserDetails partner,
            @RequestParam @Min(2020) @Max(2100) int year, @Valid @RequestBody StockCountRequest request) {
        return accessCodes.recordStockCount(partner.partnerId(), partner.userId(), request.games(), year);
    }

    /** Own share history including planned changes. */
    @GetMapping("/shares")
    public List<ShareDto> shares(@AuthenticationPrincipal PartnerUserDetails partner) {
        return partners.listShares(partner.partnerId());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal PartnerUserDetails partner,
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        partnerUsers.changeOwnPassword(partner.userId(), request.currentPassword(), request.newPassword());
    }
}
