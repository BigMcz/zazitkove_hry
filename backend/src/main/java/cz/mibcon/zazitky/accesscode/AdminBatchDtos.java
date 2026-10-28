package cz.mibcon.zazitky.accesscode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the access code administration. */
public final class AdminBatchDtos {

    private AdminBatchDtos() {
    }

    public record CreateBatchRequest(
            @NotNull UUID gameId,
            @NotNull @Min(1) @Max(5000) Integer quantity,
            /** Hours of validity from activation. */
            @NotNull @Min(1) @Max(24) Integer validityHours,
            @Size(max = 300) String note,
            @Min(0) Integer unitPriceCzk) {
    }

    public record BatchSummary(
            UUID id,
            String gameTitle,
            String partnerName,
            int quantity,
            /** Null = codes never expire (development batch). */
            Integer validityHours,
            boolean multiSession,
            String note,
            Integer unitPriceCzk,
            Instant createdAt,
            long activatedCount) {
    }

    /** Activated codes of one game in one month (Europe/Prague), basis for invoicing the partner. */
    public record MonthlyActivation(
            String month,
            String partnerName,
            String gameTitle,
            long activatedCount,
            /** Sum of the unit prices of the activated codes; null when the batches have no price. */
            Long revenueCzk,
            /** Partner's share of the revenue by {@code partner.revenue_share_pct}. */
            BigDecimal partnerShareCzk) {
    }
}
