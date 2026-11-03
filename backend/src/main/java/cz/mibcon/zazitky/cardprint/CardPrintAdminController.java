package cz.mibcon.zazitky.cardprint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.util.UUID;

import cz.mibcon.zazitky.cardprint.CardPrintDtos.BindRequest;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.ExportRequest;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Preview;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.PrintStatus;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Side;
import cz.mibcon.zazitky.cardprint.CardPrintService.ExportKind;
import cz.mibcon.zazitky.cardprint.CardPrintService.PrintFile;
import cz.mibcon.zazitky.common.PublicUrl;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Printing a batch of codes (docs/07-tisk-karticek.md), only for the admin. Previews and exports are POST: an export
 * ties the batch to a template version and writes the audit.
 */
@RestController
@RequestMapping("/api/v1/admin/batches/{batchId}/print")
public class CardPrintAdminController {

    private final CardPrintService print;
    private final PublicUrl publicUrl;

    public CardPrintAdminController(CardPrintService print, PublicUrl publicUrl) {
        this.print = print;
        this.publicUrl = publicUrl;
    }

    @GetMapping
    public PrintStatus status(@PathVariable UUID batchId) {
        return print.status(batchId);
    }

    @PostMapping("/template")
    public PrintStatus bind(@PathVariable UUID batchId, @Valid @RequestBody BindRequest request) {
        return print.bind(batchId, request.templateId());
    }

    @PostMapping("/preview")
    public Preview preview(@PathVariable UUID batchId, @RequestParam Side side) {
        return print.preview(batchId, side, publicUrl.base());
    }

    @PostMapping("/front.pdf")
    public ResponseEntity<Resource> front(@PathVariable UUID batchId, @Valid @RequestBody ExportRequest request,
            Principal principal) {
        return download(print.export(batchId, ExportKind.FRONT, request, principal.getName(), publicUrl.base()));
    }

    @PostMapping("/back.pdf")
    public ResponseEntity<Resource> back(@PathVariable UUID batchId, @Valid @RequestBody ExportRequest request,
            Principal principal) {
        return download(print.export(batchId, ExportKind.BACK, request, principal.getName(), publicUrl.base()));
    }

    @PostMapping("/all.zip")
    public ResponseEntity<Resource> all(@PathVariable UUID batchId, @Valid @RequestBody ExportRequest request,
            Principal principal) {
        return download(print.export(batchId, ExportKind.ZIP, request, principal.getName(), publicUrl.base()));
    }

    /** The file with its length, never cached (the back sides hold codes); the temporary file goes after sending. */
    static ResponseEntity<Resource> download(PrintFile file) {
        TempFileResource resource = new TempFileResource(file.path());
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .contentLength(resource.contentLength())
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(file.fileName()).build().toString())
                    .body(resource);
        } catch (IOException e) {
            CardPrintService.deleteQuietly(file.path());
            throw new UncheckedIOException(e);
        }
    }
}
