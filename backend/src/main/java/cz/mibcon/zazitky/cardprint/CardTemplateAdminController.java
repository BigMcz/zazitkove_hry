package cz.mibcon.zazitky.cardprint;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.cardprint.CardPrintDtos.FileDto;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Preview;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.SavedTemplate;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.Side;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.TemplateDto;
import cz.mibcon.zazitky.cardprint.CardPrintDtos.TemplateRequest;
import cz.mibcon.zazitky.cardprint.domain.CardTemplateFile;
import cz.mibcon.zazitky.common.PublicUrl;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Card templates of the games (docs/07-tisk-karticek.md), only for the admin (see AdminSecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class CardTemplateAdminController {

    private final CardTemplateService templates;
    private final CardPrintService print;
    private final PublicUrl publicUrl;

    public CardTemplateAdminController(CardTemplateService templates, CardPrintService print, PublicUrl publicUrl) {
        this.templates = templates;
        this.print = print;
        this.publicUrl = publicUrl;
    }

    /** A graphic of a card side for the editor; the template refers to it by its id. */
    @PostMapping(path = "/card-template-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto upload(@RequestPart("file") MultipartFile file) {
        return templates.upload(file);
    }

    @GetMapping("/games/{gameId}/card-template")
    public TemplateDto latest(@PathVariable UUID gameId) {
        return templates.latest(gameId);
    }

    @GetMapping("/games/{gameId}/card-template/versions")
    public List<TemplateDto> versions(@PathVariable UUID gameId) {
        return templates.versions(gameId);
    }

    @PostMapping("/games/{gameId}/card-template")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedTemplate create(@PathVariable UUID gameId, @Valid @RequestBody TemplateRequest request) {
        return templates.create(gameId, request);
    }

    @PostMapping("/games/{gameId}/card-template/preview")
    public Preview preview(@PathVariable UUID gameId, @RequestParam Side side,
            @Valid @RequestBody TemplateRequest request) {
        return templates.preview(gameId, request, side, publicUrl.base());
    }

    @GetMapping("/card-templates/{templateId}/files/{side}")
    public ResponseEntity<byte[]> file(@PathVariable UUID templateId, @PathVariable Side side) {
        CardTemplateFile file = templates.file(templateId, side);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName()).build().toString())
                .body(file.getContent());
    }

    /** No codes from the database, hence GET without an audit. */
    @GetMapping("/card-templates/{templateId}/test-print.pdf")
    public ResponseEntity<Resource> testPrint(@PathVariable UUID templateId) {
        return CardPrintAdminController.download(print.testPrint(templateId, publicUrl.base()));
    }
}
