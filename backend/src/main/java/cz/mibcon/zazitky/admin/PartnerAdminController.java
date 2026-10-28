package cz.mibcon.zazitky.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.admin.PartnerAdminDtos.PartnerDto;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.PartnerRequest;
import cz.mibcon.zazitky.admin.PartnerAdminDtos.SetShareRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Partners (castles) — company details and share history; only for the admin. */
@RestController
@RequestMapping("/api/v1/admin/partners")
public class PartnerAdminController {

    private final PartnerAdminService partners;

    public PartnerAdminController(PartnerAdminService partners) {
        this.partners = partners;
    }

    @GetMapping
    public List<PartnerDto> list() {
        return partners.listPartners();
    }

    @GetMapping("/{partnerId}")
    public PartnerDto get(@PathVariable UUID partnerId) {
        return partners.getPartner(partnerId);
    }

    @PostMapping
    public ResponseEntity<PartnerDto> create(@Valid @RequestBody PartnerRequest request) {
        PartnerDto partner = partners.createPartner(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/partners/" + partner.id())).body(partner);
    }

    @PutMapping("/{partnerId}")
    public PartnerDto update(@PathVariable UUID partnerId, @Valid @RequestBody PartnerRequest request) {
        return partners.updatePartner(partnerId, request);
    }

    @PostMapping("/{partnerId}/shares")
    public PartnerDto setShare(@PathVariable UUID partnerId, @Valid @RequestBody SetShareRequest request) {
        return partners.setShare(partnerId, request);
    }

    @DeleteMapping("/{partnerId}/shares/{shareId}")
    public PartnerDto deleteShare(@PathVariable UUID partnerId, @PathVariable UUID shareId) {
        return partners.deleteShare(partnerId, shareId);
    }
}
