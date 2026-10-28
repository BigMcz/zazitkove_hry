package cz.mibcon.zazitky.accesscode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.Summary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Access code administration, only for the admin (see AdminSecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class AccessCodeAdminController {

    private final AccessCodeAdminService adminService;
    private final String publicUrl;

    public AccessCodeAdminController(AccessCodeAdminService adminService,
            @Value("${app.public-url:}") String publicUrl) {
        this.adminService = adminService;
        this.publicUrl = publicUrl;
    }

    /** @param partnerId only batches of this partner; all when missing */
    @GetMapping("/batches")
    public List<BatchSummary> batches(@RequestParam(required = false) UUID partnerId) {
        return adminService.listBatches(partnerId, false);
    }

    /** The printed cards were handed over to the partner — from now on the codes work. */
    @PostMapping("/batches/{batchId}/handover")
    public BatchSummary handOver(@PathVariable UUID batchId) {
        return adminService.handOver(batchId);
    }

    /** Undo a handover marked by mistake (only while no code has been used). */
    @DeleteMapping("/batches/{batchId}/handover")
    public BatchSummary undoHandOver(@PathVariable UUID batchId) {
        return adminService.undoHandOver(batchId);
    }

    @PostMapping("/batches")
    public ResponseEntity<BatchSummary> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        BatchSummary batch = adminService.createBatch(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/batches/" + batch.id())).body(batch);
    }

    @GetMapping("/batches/{batchId}/codes.csv")
    public ResponseEntity<byte[]> exportCodes(@PathVariable UUID batchId) {
        // Without a configured public URL (local development) the links point to the server the admin uses.
        String base = publicUrl.isBlank() ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : publicUrl;
        byte[] csv = adminService.exportCsv(batchId, base).getBytes(StandardCharsets.UTF_8);
        String fileName = "kody-" + LocalDate.now(AccessCodeAdminService.BILLING_ZONE) + "-"
                + batchId.toString().substring(0, 8) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(csv);
    }

    @GetMapping("/reports/monthly-activations")
    public List<MonthlyActivation> monthlyActivations(@RequestParam @Min(2020) @Max(2100) int year,
            @RequestParam(required = false) UUID partnerId) {
        return adminService.monthlyActivations(year, partnerId);
    }

    /** Key figures of all partners (partnerId missing) or one partner, incl. the operator's profit. */
    @GetMapping("/dashboard")
    public Summary dashboard(@RequestParam @Min(2020) @Max(2100) int year,
            @RequestParam(required = false) UUID partnerId) {
        return adminService.summary(year, partnerId);
    }
}
