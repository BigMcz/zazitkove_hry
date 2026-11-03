package cz.mibcon.zazitky.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Invoicing of a castle for one month (docs/02-architektura.md, "Fakturace"). */
public final class BillingDtos {

    private BillingDtos() {
    }

    /**
     * @param ended             the month is over, so it can be invoiced
     * @param games             main games of the castle, also without activations in the month
     * @param total             all games together; {@code operatorCzk} is the amount to invoice
     * @param invoice           null = not marked as invoiced yet
     * @param uninvoicedMonths  ended months with activations that are not marked as invoiced, oldest first
     */
    public record MonthBilling(
            YearMonth month,
            boolean ended,
            List<GameBilling> games,
            Amounts total,
            Invoice invoice,
            List<YearMonth> uninvoicedMonths) {
    }

    /**
     * @param prices prices of the activated codes (more than one when the price changed during the month); when
     *               nothing was activated, the price valid at the end of the month; empty = no price set
     */
    public record GameBilling(UUID gameId, String gameTitle, List<Integer> prices, Amounts amounts) {
    }

    /**
     * Each code with the price of its game and the castle's share valid on the day of its activation.
     *
     * @param revenueCzk      sum of the prices
     * @param partnerShareCzk the castle's share of the revenue (no share set = 0)
     * @param operatorCzk     revenue minus the castle's share: what the operator invoices to the castle
     */
    public record Amounts(long activatedCount, long revenueCzk, BigDecimal partnerShareCzk, BigDecimal operatorCzk) {
    }

    /** @param amountCzk the amount to invoice when the month was marked */
    public record Invoice(LocalDate invoicedOn, BigDecimal amountCzk) {
    }

    public record MarkInvoicedRequest(@NotNull LocalDate invoicedOn) {
    }
}
