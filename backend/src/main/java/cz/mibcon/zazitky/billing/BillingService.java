package cz.mibcon.zazitky.billing;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.billing.BillingDtos.Amounts;
import cz.mibcon.zazitky.billing.BillingDtos.GameBilling;
import cz.mibcon.zazitky.billing.BillingDtos.Invoice;
import cz.mibcon.zazitky.billing.BillingDtos.MonthBilling;
import cz.mibcon.zazitky.billing.domain.PartnerInvoice;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.PartnerRepository;
import cz.mibcon.zazitky.game.domain.Partner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the operator invoices to a castle for a month: the castle sells the cards and keeps its share, the rest of the
 * revenue is the operator's. Each activated code counts with the price of its game and the castle's share valid on
 * the day of its activation (Europe/Prague); development batches are left out, as in the monthly report.
 */
@Service
@Transactional
public class BillingService {

    private static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    /** Activated codes of the partner's games in [from, to) with their price and share. */
    private static final String CODES = """
            WITH code AS (
                SELECT b.game_id, gp.price_czk, coalesce(s.share_pct, 0) AS share_pct
                FROM access_code c
                JOIN access_code_batch b ON b.id = c.batch_id
                LEFT JOIN LATERAL (
                    SELECT price.price_czk FROM game_price price
                    WHERE price.game_id = b.game_id
                      AND price.valid_from <= (c.activated_at AT TIME ZONE 'Europe/Prague')::date
                    ORDER BY price.valid_from DESC LIMIT 1
                ) gp ON TRUE
                LEFT JOIN LATERAL (
                    SELECT ps.share_pct FROM partner_share ps
                    WHERE ps.partner_id = b.partner_id
                      AND ps.valid_from <= (c.activated_at AT TIME ZONE 'Europe/Prague')::date
                    ORDER BY ps.valid_from DESC LIMIT 1
                ) s ON TRUE
                WHERE b.partner_id = :partnerId AND NOT b.multi_session AND NOT b.free
                  AND c.activated_at >= :from AND c.activated_at < :to
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PartnerRepository partners;
    private final PartnerInvoiceRepository invoices;
    private final Clock clock;

    public BillingService(NamedParameterJdbcTemplate jdbc, PartnerRepository partners,
            PartnerInvoiceRepository invoices, Clock clock) {
        this.jdbc = jdbc;
        this.partners = partners;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthBilling month(UUID partnerId, YearMonth month) {
        findPartner(partnerId);
        LocalDate first = month.atDay(1);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("partnerId", partnerId)
                .addValue("from", start(month))
                .addValue("to", start(month.plusMonths(1)))
                .addValue("monthEnd", month.atEndOfMonth());
        List<GameBilling> games = jdbc.query(CODES + """
                SELECT g.id, g.title,
                       count(code.game_id) AS activated,
                       array_remove(array_agg(DISTINCT code.price_czk ORDER BY code.price_czk), NULL) AS prices,
                       (SELECT price.price_czk FROM game_price price
                        WHERE price.game_id = g.id AND price.valid_from <= :monthEnd
                        ORDER BY price.valid_from DESC LIMIT 1) AS month_price,
                       coalesce(sum(code.price_czk), 0) AS revenue,
                       coalesce(round(sum(code.price_czk * code.share_pct / 100), 2), 0) AS share
                FROM game g
                JOIN location l ON l.id = g.location_id
                LEFT JOIN code ON code.game_id = g.id
                WHERE l.partner_id = :partnerId AND g.main_game_id IS NULL
                GROUP BY g.id, g.title
                ORDER BY g.title
                """, params, (rs, row) -> {
                    long revenue = rs.getLong("revenue");
                    BigDecimal share = rs.getBigDecimal("share");
                    List<Integer> prices = integers(rs.getArray("prices"));
                    if (prices.isEmpty() && rs.getObject("month_price") != null) {
                        prices = List.of(rs.getInt("month_price"));
                    }
                    return new GameBilling(rs.getObject("id", UUID.class), rs.getString("title"), prices,
                            new Amounts(rs.getLong("activated"), revenue, share, BigDecimal.valueOf(revenue).subtract(share)));
                });

        Amounts total = games.stream().map(GameBilling::amounts).reduce(
                new Amounts(0, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                (a, b) -> new Amounts(a.activatedCount() + b.activatedCount(), a.revenueCzk() + b.revenueCzk(),
                        a.partnerShareCzk().add(b.partnerShareCzk()), a.operatorCzk().add(b.operatorCzk())));
        Invoice invoice = invoices.findByPartnerIdAndMonth(partnerId, first)
                .map(found -> new Invoice(found.getInvoicedOn(), found.getAmountCzk()))
                .orElse(null);
        return new MonthBilling(month, isEnded(month), games, total, invoice, uninvoicedMonths(partnerId));
    }

    /** Marks an ended month as invoiced on a day after it; marking it again changes the day. */
    public MonthBilling markInvoiced(UUID partnerId, YearMonth month, LocalDate invoicedOn) {
        Partner partner = findPartner(partnerId);
        if (!isEnded(month)) {
            throw new BadRequestException("Měsíc ještě neskončil, fakturovat půjde od " + format(month.plusMonths(1).atDay(1)) + ".");
        }
        if (invoicedOn.isBefore(month.plusMonths(1).atDay(1))) {
            throw new BadRequestException("Datum fakturace musí být až po skončení měsíce.");
        }
        if (invoicedOn.isAfter(today())) {
            throw new BadRequestException("Datum fakturace nemůže být v budoucnosti.");
        }
        PartnerInvoice invoice = invoices.findByPartnerIdAndMonth(partnerId, month.atDay(1)).orElseGet(() -> {
            PartnerInvoice created = new PartnerInvoice();
            created.setPartner(partner);
            created.setMonth(month.atDay(1));
            return created;
        });
        invoice.setInvoicedOn(invoicedOn);
        invoice.setAmountCzk(month(partnerId, month).total().operatorCzk());
        invoices.saveAndFlush(invoice);
        return month(partnerId, month);
    }

    public MonthBilling unmarkInvoiced(UUID partnerId, YearMonth month) {
        findPartner(partnerId);
        invoices.findByPartnerIdAndMonth(partnerId, month.atDay(1)).ifPresent(invoice -> {
            invoices.delete(invoice);
            invoices.flush();
        });
        return month(partnerId, month);
    }

    private List<YearMonth> uninvoicedMonths(UUID partnerId) {
        Set<YearMonth> invoiced = invoices.findByPartnerId(partnerId).stream()
                .map(invoice -> YearMonth.from(invoice.getMonth()))
                .collect(Collectors.toSet());
        List<String> activeMonths = jdbc.queryForList("""
                SELECT DISTINCT to_char(c.activated_at AT TIME ZONE 'Europe/Prague', 'YYYY-MM') AS month
                FROM access_code c
                JOIN access_code_batch b ON b.id = c.batch_id
                WHERE b.partner_id = :partnerId AND NOT b.multi_session AND NOT b.free AND c.activated_at < :to
                ORDER BY month
                """, new MapSqlParameterSource()
                        .addValue("partnerId", partnerId)
                        .addValue("to", start(YearMonth.now(clock.withZone(BILLING_ZONE)))), String.class);
        return activeMonths.stream().map(YearMonth::parse).filter(month -> !invoiced.contains(month)).toList();
    }

    private boolean isEnded(YearMonth month) {
        return month.isBefore(YearMonth.from(today()));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BILLING_ZONE));
    }

    private static Timestamp start(YearMonth month) {
        return Timestamp.from(month.atDay(1).atStartOfDay(BILLING_ZONE).toInstant());
    }

    private static String format(LocalDate date) {
        return date.getDayOfMonth() + ". " + date.getMonthValue() + ". " + date.getYear();
    }

    private static List<Integer> integers(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return new ArrayList<>(Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).intValue()).toList());
    }

    private Partner findPartner(UUID partnerId) {
        return partners.findById(partnerId).orElseThrow(() -> new NotFoundException("Partner nebyl nalezen."));
    }
}
