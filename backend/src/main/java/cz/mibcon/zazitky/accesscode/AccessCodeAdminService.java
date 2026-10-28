package cz.mibcon.zazitky.accesscode;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.MonthlyActivation;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.domain.Game;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generating batches of codes for printing on cards and the monthly activation report for invoicing. */
@Service
@Transactional
public class AccessCodeAdminService {

    /** Months of the report and invoicing follow the local time of the castles. */
    static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    private final AccessCodeBatchRepository batches;
    private final AccessCodeRepository codes;
    private final GameRepository games;
    private final JdbcTemplate jdbc;

    public AccessCodeAdminService(AccessCodeBatchRepository batches, AccessCodeRepository codes,
            GameRepository games, JdbcTemplate jdbc) {
        this.batches = batches;
        this.codes = codes;
        this.games = games;
        this.jdbc = jdbc;
    }

    /** @param partnerId only batches of this partner; null = all */
    @Transactional(readOnly = true)
    public List<BatchSummary> listBatches(UUID partnerId) {
        return batches.findSummaries(partnerId);
    }

    /** New codes are NEW; printing them does not mean they are sold — a code counts as sold when activated. */
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
        // Fixed now, so that a later change of the partner's share does not rewrite invoiced months.
        batch.setRevenueSharePct(batch.getPartner().getRevenueSharePct());
        batches.save(batch);

        List<AccessCode> newCodes = new ArrayList<>(request.quantity());
        for (String value : uniqueNewCodes(request.quantity())) {
            AccessCode code = new AccessCode();
            code.setBatch(batch);
            code.setCode(value);
            newCodes.add(code);
        }
        codes.saveAll(newCodes);

        return new BatchSummary(batch.getId(), game.getTitle(), batch.getPartner().getName(), batch.getQuantity(),
                batch.getValidityHours(), batch.isMultiSession(), batch.getNote(), batch.getUnitPriceCzk(),
                batch.getRevenueSharePct(), batch.getCreatedAt(), 0);
    }

    /** CSV for the card printer (UTF-8 with BOM, ";" as in Czech Excel): code and a link with the code prefilled (for a QR code). */
    @Transactional(readOnly = true)
    public String exportCsv(UUID batchId, String publicUrl) {
        batches.findById(batchId).orElseThrow(() -> new NotFoundException("Dávka nebyla nalezena."));
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
                       round(sum(b.unit_price_czk * b.revenue_share_pct / 100), 2) AS partner_share_czk
                FROM access_code c
                JOIN access_code_batch b ON b.id = c.batch_id
                JOIN partner p ON p.id = b.partner_id
                JOIN game g ON g.id = b.game_id
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
