package cz.mibcon.zazitky.cardprint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the card templates and of printing a batch. */
public final class CardPrintDtos {

    private CardPrintDtos() {
    }

    public enum Side {
        FRONT,
        BACK
    }

    /** An uploaded graphic, without its content. */
    public record FileDto(UUID id, String fileName, String contentType, int sizeBytes) {
    }

    /**
     * One version of the card template of a game.
     *
     * @param batchCount batches printed with this version
     */
    public record TemplateDto(
            UUID id,
            UUID gameId,
            int version,
            CardLayout layout,
            FileDto front,
            FileDto back,
            String note,
            Instant createdAt,
            long batchCount) {
    }

    /**
     * A new version of the template, or the state of the editor for a preview.
     *
     * @param frontFileId uploaded graphic of the front; null = the one of the previous version
     * @param backFileId  the same for the back
     */
    public record TemplateRequest(
            @NotNull @Valid CardLayout layout,
            UUID frontFileId,
            UUID backFileId,
            @Size(max = 300) String note) {
    }

    /** @param warnings what the admin should check, the version is saved anyway */
    public record SavedTemplate(TemplateDto template, List<String> warnings) {
    }

    /** @param image PNG as a data URL */
    public record Preview(String image, List<String> warnings) {
    }

    /**
     * Printing of a batch.
     *
     * @param template       version the batch is printed with; null = not printed yet, the first export takes the latest
     * @param latestTemplate latest version of the game; null = the game has no template yet
     */
    public record PrintStatus(
            UUID batchId,
            int batchNo,
            UUID gameId,
            String gameTitle,
            UUID partnerId,
            String partnerName,
            int quantity,
            /** Demo batch: one shared code printed on any number of cards. */
            boolean multiSession,
            boolean free,
            /** The shared code of a demo batch (ABCD-2345); null for other batches. */
            String sharedCode,
            Instant handedOverAt,
            Instant printedAt,
            TemplateDto template,
            TemplateDto latestTemplate,
            List<ExportDto> exports) {
    }

    /** One download of print files (audit). */
    public record ExportDto(
            String kind,
            /** Reprint selection, null = the whole batch. */
            String serials,
            int codesCount,
            boolean cropMarks,
            String username,
            int templateVersion,
            Instant createdAt) {
    }

    /**
     * @param serials reprint, e.g. "42, 57, 100-110"; null or empty = the whole batch (only for the back side)
     * @param copies  demo batch only: on how many cards its shared code is printed; null = 1
     */
    public record ExportRequest(Boolean cropMarks, @Size(max = 1000) String serials, @Min(1) @Max(1000) Integer copies) {

        public boolean withCropMarks() {
            return Boolean.TRUE.equals(cropMarks);
        }
    }

    public record BindRequest(@NotNull UUID templateId) {
    }
}
