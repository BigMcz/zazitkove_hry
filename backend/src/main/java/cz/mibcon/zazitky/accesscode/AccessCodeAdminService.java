package cz.mibcon.zazitky.accesscode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.Summary;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeStatus;
import cz.mibcon.zazitky.common.InvalidStateException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.domain.Game;
import org.springframework.jdbc.core.JdbcTemplate;
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
     * @param partnerId only this partner; null = all
     */
    @Transactional(readOnly = true)
    public Summary summary(int year, UUID partnerId) {
        List<MonthlyActivation> months = monthlyActivations(year, partnerId);
        String thisMonth = YearMonth.now(clock.withZone(BILLING_ZONE)).toString();
        long activatedThisMonth = months.stream()
                .filter(row -> row.month().equals(thisMonth))
                .mapToLong(MonthlyActivation::activatedCount)
                .sum();
        long activatedInYear = months.stream().mapToLong(MonthlyActivation::activatedCount).sum();
        Long revenue = months.stream().anyMatch(row -> row.revenueCzk() != null)
                ? months.stream().map(MonthlyActivation::revenueCzk).filter(Objects::nonNull).mapToLong(Long::longValue).sum()
                : null;
        BigDecimal share = months.stream()
                .map(MonthlyActivation::partnerShareCzk)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = revenue == null ? null : BigDecimal.valueOf(revenue).subtract(share);

        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE b.handed_over_at IS NOT NULL AND c.status = 'NEW') AS in_stock,
                       count(*) FILTER (WHERE b.handed_over_at IS NULL AND c.status = 'NEW') AS in_production,
                       count(*) FILTER (WHERE b.handed_over_at IS NOT NULL) AS delivered
                FROM access_code c
                JOIN access_code_batch b ON b.id = c.batch_id
                WHERE NOT b.multi_session AND (CAST(? AS uuid) IS NULL OR b.partner_id = ?)
                """,
                (rs, row) -> new Summary(year, activatedThisMonth, activatedInYear, revenue,
                        revenue == null && share.signum() == 0 ? null : share, profit,
                        rs.getLong("in_stock"), rs.getLong("in_production"), rs.getLong("delivered")),
                partnerId, partnerId);
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
                GROUP BY month, p.name, g.title
                ORDER BY month, p.name, g.title
                """,
                (rs, row) -> new MonthlyActivation(
                        rs.getString("month"),
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
