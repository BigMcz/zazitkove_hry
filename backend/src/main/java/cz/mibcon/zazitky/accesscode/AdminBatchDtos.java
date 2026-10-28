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
            UUID partnerId,
            String gameTitle,
            String partnerName,
            int quantity,
            /** Null = codes never expire (development batch). */
            Integer validityHours,
            boolean multiSession,
            String note,
            Integer unitPriceCzk,
            Instant createdAt,
            /** Null = still in production, the codes do not work yet. */
            Instant handedOverAt,
            long activatedCount,
            /** Cards not used yet (NEW) — for a handed-over batch the cards the partner should still have. */
            long notActivatedCount) {
    }

    /** Activated codes of one game in one month (Europe/Prague), basis for invoicing the partner. */
    public record MonthlyActivation(
            String month,
            String partnerName,
            String gameTitle,
            long activatedCount,
            /** Sum of the unit prices of the activated codes; null when the batches have no price. */
            Long revenueCzk,
            /** Partner's share of the revenue; each code by the share valid on the day of its activation. */
            BigDecimal partnerShareCzk) {
    }

    /**
     * Key figures for the dashboard (all partners or one partner) for a year; development batches are
     * left out. Money is null when no activated code has a price.
     *
     * @param profitCzk         revenue minus the partners' share — the operator's part; null for the partner view
     * @param cardsInStock      handed over and not activated: the cards the partner(s) should still have
     * @param cardsInProduction generated but not handed over yet
     * @param cardsDelivered    all cards ever handed over
     */
    public record Summary(
            int year,
            long activatedThisMonth,
            long activatedInYear,
            Long revenueCzk,
            BigDecimal partnerShareCzk,
            BigDecimal profitCzk,
            long cardsInStock,
            long cardsInProduction,
            long cardsDelivered) {

        public Summary withoutProfit() {
            return new Summary(year, activatedThisMonth, activatedInYear, revenueCzk, partnerShareCzk, null,
                    cardsInStock, cardsInProduction, cardsDelivered);
        }
    }
}
