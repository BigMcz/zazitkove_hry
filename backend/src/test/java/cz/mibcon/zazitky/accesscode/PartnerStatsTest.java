package cz.mibcon.zazitky.accesscode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** GET /admin/reports/partners: all castles side by side, with partners of their own. */
@IntegrationTest
class PartnerStatsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void comparesThePartnersAndEstimatesHowLongTheCardsLast() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID selling = partner("Prodávající hrad " + suffix);
        UUID quiet = partner("Tichý hrad " + suffix);
        jdbc.update("INSERT INTO partner_share (partner_id, valid_from, share_pct) VALUES (?, DATE '2020-01-01', 20)",
                selling);
        UUID game = game(selling);
        UUID delivered = batch(selling, game, 10, true, false);
        batch(selling, game, 5, false, false);
        batch(selling, game, 3, true, true);
        jdbc.update("""
                UPDATE access_code SET status = 'ACTIVATED', activated_at = now() - interval '1 minute'
                WHERE id IN (SELECT id FROM access_code WHERE batch_id = ? LIMIT 4)
                """, delivered);

        int year = Year.now(ZoneId.of("Europe/Prague")).getValue();
        String row = "$[?(@.partnerId == '%s')].";
        mvc.perform(get("/api/v1/admin/reports/partners").param("year", String.valueOf(year))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row.formatted(selling) + "activatedInYear").value(4))
                .andExpect(jsonPath(row.formatted(selling) + "revenueCzk").value(400))
                .andExpect(jsonPath(row.formatted(selling) + "partnerShareCzk").value(80.0))
                .andExpect(jsonPath(row.formatted(selling) + "profitCzk").value(320.0))
                .andExpect(jsonPath(row.formatted(selling) + "cardsDelivered").value(10))
                .andExpect(jsonPath(row.formatted(selling) + "cardsInStock").value(6))
                .andExpect(jsonPath(row.formatted(selling) + "cardsInProduction").value(5))
                .andExpect(jsonPath(row.formatted(selling) + "activatedLast28Days").value(4))
                .andExpect(jsonPath(row.formatted(selling) + "stockDays").value(42))
                .andExpect(jsonPath(row.formatted(quiet) + "activatedInYear").value(0))
                .andExpect(jsonPath(row.formatted(quiet) + "revenueCzk").value((Object) null))
                .andExpect(jsonPath(row.formatted(quiet) + "cardsDelivered").value(0))
                .andExpect(jsonPath(row.formatted(quiet) + "stockDays").value((Object) null));
        mvc.perform(get("/api/v1/admin/reports/partners").param("year", String.valueOf(year))
                        .with(user("hrad").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    private UUID partner(String name) {
        return jdbc.queryForObject("INSERT INTO partner (name) VALUES (?) RETURNING id", UUID.class, name);
    }

    private UUID game(UUID partnerId) {
        UUID location = jdbc.queryForObject("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id",
                UUID.class, partnerId);
        return jdbc.queryForObject("""
                INSERT INTO game (location_id, slug, title, intro_text, outro_text)
                VALUES (?, ?, 'Hra', 'Úvod', 'Závěr') RETURNING id
                """, UUID.class, location, "partner-stats-" + UUID.randomUUID());
    }

    private UUID batch(UUID partnerId, UUID gameId, int quantity, boolean handedOver, boolean development) {
        UUID batchId = jdbc.queryForObject("""
                INSERT INTO access_code_batch (partner_id, game_id, quantity, unit_price_czk, multi_session, handed_over_at)
                VALUES (?, ?, ?, 100, ?, CASE WHEN ? THEN now() END) RETURNING id
                """, UUID.class, partnerId, gameId, quantity, development, handedOver);
        for (int i = 0; i < quantity; i++) {
            jdbc.update("INSERT INTO access_code (batch_id, code) VALUES (?, ?)", batchId,
                    UUID.randomUUID().toString().substring(0, 20));
        }
        return batchId;
    }
}
