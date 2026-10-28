package cz.mibcon.zazitky.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.admin.PartnerUserDtos.CreatePartnerUserRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerUserDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.SetEnabledRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.SetPasswordRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.SetShareRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.UpdatePartnerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Managing partners (castles) — contact and share history — and the accounts of their staff; only for the admin. */
@RestController
@RequestMapping("/api/v1/admin")
public class PartnerUserAdminController {

    private final PartnerUserService partnerUsers;

    public PartnerUserAdminController(PartnerUserService partnerUsers) {
        this.partnerUsers = partnerUsers;
    }

    @GetMapping("/partners")
    public List<PartnerDto> partners() {
        return partnerUsers.listPartners();
    }

    @PutMapping("/partners/{partnerId}")
    public PartnerDto updatePartner(@PathVariable UUID partnerId, @Valid @RequestBody UpdatePartnerRequest request) {
        return partnerUsers.updatePartner(partnerId, request);
    }

    @PostMapping("/partners/{partnerId}/shares")
    public PartnerDto setShare(@PathVariable UUID partnerId, @Valid @RequestBody SetShareRequest request) {
        return partnerUsers.setShare(partnerId, request);
    }

    @DeleteMapping("/partners/{partnerId}/shares/{shareId}")
    public PartnerDto deleteShare(@PathVariable UUID partnerId, @PathVariable UUID shareId) {
        return partnerUsers.deleteShare(partnerId, shareId);
    }

    @GetMapping("/partner-users")
    public List<PartnerUserDto> users() {
        return partnerUsers.listUsers();
    }

    @PostMapping("/partner-users")
    public ResponseEntity<PartnerUserDto> create(@Valid @RequestBody CreatePartnerUserRequest request) {
        PartnerUserDto user = partnerUsers.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/partner-users/" + user.id())).body(user);
    }

    @PostMapping("/partner-users/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable UUID userId, @Valid @RequestBody SetPasswordRequest request) {
        partnerUsers.setPassword(userId, request.password());
    }

    @PostMapping("/partner-users/{userId}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setEnabled(@PathVariable UUID userId, @Valid @RequestBody SetEnabledRequest request) {
        partnerUsers.setEnabled(userId, request.enabled());
    }
}
