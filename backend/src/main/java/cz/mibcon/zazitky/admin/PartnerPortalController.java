package cz.mibcon.zazitky.admin;

import java.util.List;

import cz.mibcon.zazitky.accesscode.AccessCodeAdminService;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.admin.PartnerUserDtos.ChangeOwnPasswordRequest;
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

    public PartnerPortalController(AccessCodeAdminService accessCodes, PartnerUserService partnerUsers) {
        this.accessCodes = accessCodes;
        this.partnerUsers = partnerUsers;
    }

    @GetMapping("/reports/monthly-activations")
    public List<MonthlyActivation> monthlyActivations(@AuthenticationPrincipal PartnerUserDetails partner,
            @RequestParam @Min(2020) @Max(2100) int year) {
        return accessCodes.monthlyActivations(year, partner.partnerId());
    }

    /** Printed codes of the castle; our development batches (multi-session) are left out as in the report. */
    @GetMapping("/batches")
    public List<BatchSummary> batches(@AuthenticationPrincipal PartnerUserDetails partner) {
        return accessCodes.listBatches(partner.partnerId()).stream()
                .filter(batch -> !batch.multiSession())
                .toList();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal PartnerUserDetails partner,
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        partnerUsers.changeOwnPassword(partner.userId(), request.currentPassword(), request.newPassword());
    }
}
