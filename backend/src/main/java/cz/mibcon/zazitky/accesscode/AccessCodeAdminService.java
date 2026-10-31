package cz.mibcon.zazitky.accesscode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.GameCount;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.GameStock;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.PartnerStats;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.Summary;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeStatus;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.InvalidStateException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.domain.Game;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batches of codes (generate → print → hand over to the partner), the monthly activation report
 * for invoicing and key figures for the dashboards.
 */
@Service
@Transactional
public class AccessCodeAdminService {

    /** Months of the report and invoicing follow the local time of the castles. */
    static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    private final AccessCodeBatchRepository batches;
    private final AccessCodeRepository codes;
    private final GameRepository games;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccessCodeAdminService(AccessCodeBatchRepository batches, AccessCodeRepository codes,
            GameRepository games, JdbcTemplate jdbc, Clock clock) {
        this.batches = batches;
        this.codes = codes;
        this.games = games;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param partnerId      only batches of this partner; null = all
     * @param handedOverOnly only batches already handed over (what the partner may see)
     */
    @Transactional(readOnly = true)
    public List<BatchSummary> listBatches(UUID partnerId, boolean handedOverOnly) {
        return batches.findSummaries(partnerId, handedOverOnly);
    }

    /**
     * New codes are NEW and the batch is "in production": the codes do not work until the printed
     * cards are handed over to the partner ({@link #handOver}). A code counts as sold when activated.
     */
    public BatchSummary createBatch(CreateBatchRequest request) {
        Game game = games.findById(request.gameId())
                .orElseThrow(() -> new NotFoundException("Hra nebyla nalezena."));

        AccessCodeBatch batch = new AccessCodeBatch();
        batch.setGame(game);
        batch.setPartner(game.getLocation().getPartner());
        batch.setQuantity(request.quantity());
        batch.setValidityHours(request.validityHours());
        batch.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        batch.setUnitPriceCzk(request.unitPriceCzk());
        batches.save(batch);

        List<AccessCode> newCodes = new ArrayList<>(request.quantity());
        for (String value : uniqueNewCodes(request.quantity())) {
            AccessCode code = new AccessCode();
            code.setBatch(batch);
            code.setCode(value);
            newCodes.add(code);
        }
        codes.saveAll(newCodes);
        codes.flush();

        return summaryOf(batch.getId());
    }

    /** The printed cards were handed over to the partner: from now on the codes work and the partner sees them. */
    public BatchSummary handOver(UUID batchId) {
        AccessCodeBatch batch = findBatch(batchId);
        if (batch.getHandedOverAt() == null) {
            batch.setHandedOverAt(clock.instant());
            batches.flush();
        }
        return summaryOf(batchId);
    }

    /** Undo a handover marked by mistake — only while no code of the batch has been used. */
    public BatchSummary undoHandOver(UUID batchId) {
        AccessCodeBatch batch = findBatch(batchId);
        if (codes.existsByBatchIdAndStatusNot(batchId, AccessCodeStatus.NEW)) {
            throw new InvalidStateException("Z dávky už hráči použili kódy, předání nejde vrátit.");
        }
        batch.setHandedOverAt(null);
        batches.flush();
        return summaryOf(batchId);
    }

    /**
     * Key figures for a year: activations and money from the monthly report, cards by state.
     *
     * @param partnerId only this partner (with the stock of each of its games); null = all
     */
    @Transactional(readOnly = true)
    public Summary summary(int year, UUID partnerId) {
        List<MonthlyActivation> months = monthlyActivations(year, partnerId);
        YearMonth now = YearMonth.now(clock.withZone(BILLING_ZONE));
        // This month is the current one also when another year is chosen
        Totals month = Totals.of((now.getYear() == year ? months : monthlyActivations(now.getYear(), partnerId)).stream()
                .filter(row -> row.month().equals(now.toString()))
                .toList());
        Totals totals = Totals.of(months);
        List<GameStock> games = stocks(partnerId).stream().flatMap(stock -> stock.games().stream()).toList();
        return new Summary(year, month.activated(), totals.activated(), totals.revenue(), totals.share(),
                totals.profit(), month.revenue(), month.share(), month.profit(),
                games.stream().mapToLong(GameStock::cardsInStock).sum(),
                games.stream().mapToLong(GameStock::cardsInStockComputed).sum(),
                partnerId == null ? null : latestCount(games),
                games.stream().mapToLong(GameStock::cardsInProduction).sum(),
                games.stream().mapToLong(GameStock::cardsDelivered).sum(),
                partnerId == null ? List.of() : games);
    }

    /** Days of sales the pace for the stock estimate is taken from. */
    static final int PACE_DAYS = 28;

    /**
     * All partners side by side for a year: activations and money (as in the monthly report), cards by
     * state and how long the cards in stock of each game last at the pace of the last {@value #PACE_DAYS} days.
     */
    @Transactional(readOnly = true)
    public List<PartnerStats> partnerStats(int year) {
        Map<UUID, List<MonthlyActivation>> months = monthlyActivations(year, null).stream()
                .collect(Collectors.groupingBy(MonthlyActivation::partnerId));
        return stocks(null).stream()
                .map(stock -> {
                    Totals totals = Totals.of(months.getOrDefault(stock.partnerId(), List.of()));
                    List<GameStock> games = stock.games();
                    return new PartnerStats(stock.partnerId(), stock.partnerName(), totals.activated(),
                            totals.revenue(), totals.share(), totals.profit(),
                            games.stream().mapToLong(GameStock::cardsDelivered).sum(),
                            games.stream().mapToLong(GameStock::cardsInStock).sum(),
                            games.stream().mapToLong(GameStock::cardsInStockComputed).sum(),
                            latestCount(games),
                            games.stream().mapToLong(GameStock::cardsInProduction).sum(),
                            games.stream().mapToLong(GameStock::activatedLast28Days).sum(),
                            games.stream().map(GameStock::stockDays).filter(Objects::nonNull)
                                    .min(Integer::compare).orElse(null),
                            games);
                })
                .toList();
    }

    /**
     * The castle's own count of the cards in stock of each game: it may lower our number (cards sold but
     * not activated), never raise it. Money is not affected, only activated cards are invoiced.
     */
    public Summary recordStockCount(UUID partnerId, UUID partnerUserId, List<GameCount> counts, int year) {
        Map<UUID, GameStock> games = stocks(partnerId).stream()
                .flatMap(stock -> stock.games().stream())
                .collect(Collectors.toMap(GameStock::gameId, game -> game));
        Timestamp countedAt = Timestamp.from(clock.instant());
        for (GameCount count : counts) {
            GameStock game = games.get(count.gameId());
            if (game == null) {
                throw new BadRequestException("Kartičky této hry jste zatím nepřevzali.");
            }
            if (count.cardsInStock() > game.cardsInStockComputed()) {
                throw new BadRequestException("Hra " + game.gameTitle() + ": na skladě můžete mít nejvýš "
                        + game.cardsInStockComputed() + " kartiček, tolik převzatých kartiček zatím nikdo neaktivoval.");
            }
        }
        for (GameCount count : counts) {
            jdbc.update("""
                    INSERT INTO partner_stock_count (partner_id, game_id, counted_at, cards_in_stock, computed_in_stock,
                                                     partner_user_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, partnerId, count.gameId(), countedAt, count.cardsInStock(),
                    games.get(count.gameId()).cardsInStockComputed(), partnerUserId);
        }
        return summary(year, partnerId).withoutProfit();
    }

    private static Instant latestCount(List<GameStock> games) {
        return games.stream().map(GameStock::stockCountedAt).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(null);
    }

    /** Partner with the stock of each game it has cards of (development batches are left out). */
    private record Stock(UUID partnerId, String partnerName, List<GameStock> games) {
    }

    /**
     * Cards of one partner (or of each partner) by game and state. In stock is our number (handed over
     * and not activated) unless the castle counted the cards of the game: then the count plus the cards
     * handed over since, minus the cards activated since, at most our number.
     */
    private List<Stock> stocks(UUID partnerId) {
        Timestamp paceFrom = Timestamp.from(clock.instant().minus(Duration.ofDays(PACE_DAYS)));
        Map<UUID, Stock> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id AS partner_id, p.name AS partner_name, g.id AS game_id, g.title AS game_title,
                       l.counted_at, l.cards_in_stock AS counted,
                       count(c.id) FILTER (WHERE b.handed_over_at IS NOT NULL) AS delivered,
                       count(c.id) FILTER (WHERE b.handed_over_at IS NOT NULL AND c.status = 'NEW') AS in_stock,
                       count(c.id) FILTER (WHERE b.handed_over_at IS NULL AND c.status = 'NEW') AS in_production,
                       count(c.id) FILTER (WHERE c.activated_at >= ?) AS recent,
                       count(c.id) FILTER (WHERE b.handed_over_at > l.counted_at) AS delivered_since,
                       count(c.id) FILTER (WHERE c.activated_at > l.counted_at) AS activated_since
                FROM partner p
                LEFT JOIN access_code_batch b ON b.partner_id = p.id AND NOT b.multi_session
                LEFT JOIN game g ON g.id = b.game_id
                LEFT JOIN access_code c ON c.batch_id = b.id
                LEFT JOIN LATERAL (
                    SELECT s.counted_at, s.cards_in_stock
                    FROM partner_stock_count s
                    WHERE s.partner_id = p.id AND s.game_id = g.id
                    ORDER BY s.counted_at DESC
                    LIMIT 1
                ) l ON TRUE
                WHERE CAST(? AS uuid) IS NULL OR p.id = ?
                GROUP BY p.id, p.name, g.id, g.title, l.counted_at, l.cards_in_stock
                ORDER BY p.name, g.title
                """,
                (RowCallbackHandler) rs -> {
                    UUID id = rs.getObject("partner_id", UUID.class);
                    String name = rs.getString("partner_name");
                    Stock stock = result.computeIfAbsent(id, key -> new Stock(key, name, new ArrayList<>()));
                    UUID gameId = rs.getObject("game_id", UUID.class);
                    if (gameId == null) {
                        return;
                    }
                    long computed = rs.getLong("in_stock");
                    Timestamp countedAt = rs.getTimestamp("counted_at");
                    long inStock = countedAt == null ? computed
                            : Math.max(0, Math.min(computed, rs.getLong("counted") + rs.getLong("delivered_since")
                                    - rs.getLong("activated_since")));
                    long recent = rs.getLong("recent");
                    stock.games().add(new GameStock(gameId, rs.getString("game_title"), rs.getLong("delivered"),
                            inStock, computed, countedAt == null ? null : countedAt.toInstant(),
                            rs.getLong("in_production"), recent,
                            recent == 0 ? null : (int) (inStock * PACE_DAYS / recent)));
                },
                paceFrom, partnerId, partnerId);
        return List.copyOf(result.values());
    }

    /** Sums of monthly report rows; money is null when no activated code has a price. */
    private record Totals(long activated, Long revenue, BigDecimal share, BigDecimal profit) {

        static Totals of(List<MonthlyActivation> months) {
            long activated = months.stream().mapToLong(MonthlyActivation::activatedCount).sum();
            Long revenue = months.stream().anyMatch(row -> row.revenueCzk() != null)
                    ? months.stream().map(MonthlyActivation::revenueCzk).filter(Objects::nonNull).mapToLong(Long::longValue).sum()
                    : null;
            BigDecimal share = months.stream()
                    .map(MonthlyActivation::partnerShareCzk)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal profit = revenue == null ? null : BigDecimal.valueOf(revenue).subtract(share);
            return new Totals(activated, revenue, revenue == null && share.signum() == 0 ? null : share, profit);
        }
    }

    private AccessCodeBatch findBatch(UUID batchId) {
        return batches.findById(batchId).orElseThrow(() -> new NotFoundException("Dávka nebyla nalezena."));
    }

    private BatchSummary summaryOf(UUID batchId) {
        return batches.findSummaries(null, false).stream()
                .filter(summary -> summary.id().equals(batchId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Dávka nebyla nalezena."));
    }

    /** CSV for the card printer (UTF-8 with BOM, ";" as in Czech Excel): code and a link with the code prefilled (for a QR code). */
    @Transactional(readOnly = true)
    public String exportCsv(UUID batchId, String publicUrl) {
        findBatch(batchId);
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;

        StringBuilder csv = new StringBuilder("﻿kod;odkaz\r\n");
        for (AccessCode code : codes.findByBatchIdOrderByCode(batchId)) {
            String printed = AccessCodeFormat.format(code.getCode());
            csv.append(printed).append(';').append(base).append("/?kod=").append(printed).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Activated codes per month, partner and game; development (multi-session) batches are left out.
     * The partner's share of each code is the one valid on the day of its activation (partner_share).
     *
     * @param partnerId only this partner; null = all
     */
    @Transactional(readOnly = true)
    public List<MonthlyActivation> monthlyActivations(int year, UUID partnerId) {
        Timestamp from = Timestamp.from(LocalDate.of(year, 1, 1).atStartOfDay(BILLING_ZONE).toInstant());
        Timestamp to = Timestamp.from(LocalDate.of(year + 1, 1, 1).atStartOfDay(BILLING_ZONE).toInstant());
        return jdbc.query("""
                SELECT to_char(c.activated_at AT TIME ZONE 'Europe/Prague', 'YYYY-MM') AS month,
                       p.id AS partner_id,
                       p.name AS partner_name,
                       g.title AS game_title,
                       count(*) AS activated_count,
                       sum(b.unit_price_czk) AS revenue_czk,
                       round(sum(b.unit_price_czk * s.share_pct / 100), 2) AS partner_share_czk
                FROM access_code c
                JOIN access_code_batch b ON b.id = c.batch_id
                JOIN partner p ON p.id = b.partner_id
                JOIN game g ON g.id = b.game_id
                LEFT JOIN LATERAL (
                    SELECT ps.share_pct
                    FROM partner_share ps
                    WHERE ps.partner_id = b.partner_id
                      AND ps.valid_from <= (c.activated_at AT TIME ZONE 'Europe/Prague')::date
                    ORDER BY ps.valid_from DESC
                    LIMIT 1
                ) s ON TRUE
                WHERE c.activated_at >= ? AND c.activated_at < ? AND NOT b.multi_session
                  AND (CAST(? AS uuid) IS NULL OR p.id = ?)
                GROUP BY month, p.id, p.name, g.title
                ORDER BY month, p.name, g.title
                """,
                (rs, row) -> new MonthlyActivation(
                        rs.getString("month"),
                        rs.getObject("partner_id", UUID.class),
                        rs.getString("partner_name"),
                        rs.getString("game_title"),
                        rs.getLong("activated_count"),
                        rs.getObject("revenue_czk", Long.class),
                        rs.getBigDecimal("partner_share_czk")),
                from, to, partnerId, partnerId);
    }

    private Set<String> uniqueNewCodes(int quantity) {
        Set<String> result = new HashSet<>();
        while (result.size() < quantity) {
            Set<String> candidates = new HashSet<>();
            while (candidates.size() < quantity - result.size()) {
                String candidate = AccessCodeFormat.generate();
                if (!result.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
            codes.findExistingCodes(candidates).forEach(candidates::remove);
            result.addAll(candidates);
        }
        return result;
    }
}
