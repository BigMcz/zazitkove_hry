package cz.mibcon.zazitky.accesscode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the access code administration. */
public final class AdminBatchDtos {

    private AdminBatchDtos() {
    }

    /** What the cards of a batch are for. */
    public enum BatchKind {
        /** Sold at the castle: unique single-use codes, counted in the revenue and the stock. */
        SALE,
        /** Given away (presents, contests): like SALE, but not in the revenue, invoicing or stock. */
        FREE,
        /** One shared code on any number of cards (DEMO-KATA): never expires, every entry starts a new game. */
        DEMO
    }

    /**
     * @param kind          null = SALE
     * @param quantity      SALE and FREE only
     * @param validityHours hours of validity from activation, SALE and FREE only
     * @param code          DEMO only: own code of 8 letters or digits (DEMO-KATA); null = generated
     */
    public record CreateBatchRequest(
            @NotNull UUID gameId,
            BatchKind kind,
            @Min(1) @Max(5000) Integer quantity,
            @Min(1) @Max(24) Integer validityHours,
            @Size(max = 300) String note,
            @Size(max = 20) String code) {

        /** A batch for sale. */
        public CreateBatchRequest(UUID gameId, Integer quantity, Integer validityHours, String note) {
            this(gameId, BatchKind.SALE, quantity, validityHours, note, null);
        }

        public BatchKind kindOrSale() {
            return kind == null ? BatchKind.SALE : kind;
        }
    }

    public record BatchSummary(
            UUID id,
            /** Short number for people, D12. */
            int batchNo,
            UUID partnerId,
            UUID gameId,
            String gameTitle,
            String partnerName,
            int quantity,
            /** Null = codes never expire (development batch). */
            Integer validityHours,
            /** One shared code, every entry a new game (demo code); not in the revenue, stock or stats. */
            boolean multiSession,
            /** Cards given away: not in the revenue, invoicing or stock. */
            boolean free,
            String note,
            Instant createdAt,
            /** Null = still in production, the codes do not work yet. */
            Instant handedOverAt,
            long activatedCount,
            /** Cards not used yet (NEW) — for a handed-over batch the cards the partner should still have. */
            long notActivatedCount,
            /** First export of the back sides; null = not printed yet. */
            Instant printedAt,
            /** Version of the card template the batch is printed with; null = not tied yet. */
            Integer templateVersion) {
    }

    /** Activated codes of one game in one month (Europe/Prague), basis for invoicing the partner. */
    public record MonthlyActivation(
            String month,
            UUID partnerId,
            String partnerName,
            String gameTitle,
            long activatedCount,
            /** Sum of the prices of the game valid on the days of the activations; null when the game has no price. */
            Long revenueCzk,
            /** Partner's share of the revenue; each code by the share valid on the day of its activation. */
            BigDecimal partnerShareCzk) {
    }

    /**
     * Key figures for the dashboard (all partners or one partner) for a year; development batches are
     * left out. Money is null when no activated code has a price.
     *
     * @param profitCzk         revenue minus the partners' share — the operator's part; null for the partner view
     * @param cardsInStock         cards the partner(s) should still have: from the latest stock count of the
     *                             castle when there is one, otherwise handed over and not activated
     * @param cardsInStockComputed handed over and not activated, regardless of the stock counts
     * @param stockCountedAt       latest stock count of the partner; null without one or for all partners
     * @param games                stock of each game of the partner; empty for all partners
     * @param cardsInProduction    generated but not handed over yet
     * @param cardsDelivered    all cards ever handed over
     */
    public record Summary(
            int year,
            long activatedThisMonth,
            long activatedInYear,
            Long revenueCzk,
            BigDecimal partnerShareCzk,
            BigDecimal profitCzk,
            /** This month (Europe/Prague) regardless of the chosen year, like activatedThisMonth. */
            Long revenueThisMonthCzk,
            BigDecimal partnerShareThisMonthCzk,
            BigDecimal profitThisMonthCzk,
            long cardsInStock,
            long cardsInStockComputed,
            Instant stockCountedAt,
            long cardsInProduction,
            long cardsDelivered,
            List<GameStock> games) {

        public Summary withoutProfit() {
            return new Summary(year, activatedThisMonth, activatedInYear, revenueCzk, partnerShareCzk, null,
                    revenueThisMonthCzk, partnerShareThisMonthCzk, null, cardsInStock, cardsInStockComputed,
                    stockCountedAt, cardsInProduction, cardsDelivered, games);
        }
    }

    /**
     * One partner in the comparison of all castles for a year; development batches are left out.
     *
     * @param cardsInStock        cards the partner should still have, from its latest stock count when there is one
     * @param cardsInStockComputed handed over and not activated, regardless of the stock counts
     * @param activatedLast28Days pace of selling for the stock estimate (regardless of the chosen year)
     * @param stockDays           the game whose cards run out first: days its cards last at that pace; null without sales
     * @param games               the same per game
     */
    public record PartnerStats(
            UUID partnerId,
            String partnerName,
            long activatedInYear,
            Long revenueCzk,
            BigDecimal partnerShareCzk,
            BigDecimal profitCzk,
            long cardsDelivered,
            long cardsInStock,
            long cardsInStockComputed,
            Instant stockCountedAt,
            long cardsInProduction,
            long activatedLast28Days,
            Integer stockDays,
            List<GameStock> games) {
    }

    /**
     * Cards of one game at one partner; development batches are left out.
     *
     * @param cardsInStock         from the castle's latest stock count of the game when there is one, otherwise
     *                             handed over and not activated
     * @param cardsInStockComputed handed over and not activated, regardless of the stock counts
     * @param activatedLast28Days  pace of selling for the stock estimate
     * @param stockDays            how many days the cards in stock last at that pace; null without sales in that time
     */
    public record GameStock(
            UUID gameId,
            String gameTitle,
            long cardsDelivered,
            long cardsInStock,
            long cardsInStockComputed,
            Instant stockCountedAt,
            long cardsInProduction,
            long activatedLast28Days,
            Integer stockDays) {
    }

    /** Stock count entered by the castle: how many cards of each game it really has (at most our number). */
    public record StockCountRequest(@NotNull @Size(min = 1, max = 50) List<@Valid @NotNull GameCount> games) {
    }

    public record GameCount(@NotNull UUID gameId, @NotNull @Min(0) @Max(100_000) Integer cardsInStock) {
    }
}
