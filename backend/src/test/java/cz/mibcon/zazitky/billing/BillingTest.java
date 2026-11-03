package cz.mibcon.zazitky.billing;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** What the operator invoices to a castle for a month, and marking the month as invoiced. */
@IntegrationTest
class BillingTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID partnerId;
    UUID gameId;

    @BeforeEach
    void castleWithTwoGames() {
        partnerId = insert("INSERT INTO partner (name) VALUES ('Fakturační hrad') RETURNING id");
        jdbc.update("INSERT INTO partner_share (partner_id, valid_from, share_pct) VALUES (?, DATE '2020-01-01', 25)",
                partnerId);
        UUID location = insert("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id", partnerId);
        gameId = game(location, "A hra");
        UUID quietGame = game(location, "B hra");
        jdbc.update("INSERT INTO game_price (game_id, valid_from, price_czk) VALUES (?, DATE '2020-01-01', 100),"
                + " (?, DATE '2026-03-15', 120), (?, DATE '2020-01-01', 50)", gameId, gameId, quietGame);
        // A language version shares the cards and the price of its main game, it has no row of its own
        jdbc.update("INSERT INTO game (location_id, main_game_id, slug, title, intro_text, outro_text, language)"
                + " VALUES (?, ?, ?, 'Das Spiel', 'I', 'O', 'de')", location, gameId, "billing-de-" + UUID.randomUUID());

        UUID sold = batch(false);
        activate(sold, "2026-03-10T10:00:00Z");
        activate(sold, "2026-03-20T10:00:00Z");
        activate(sold, "2026-03-31T22:30:00Z"); // 00:30 on 1 April in Prague → April
        activate(batch(true), "2026-03-12T10:00:00Z"); // development code, not invoiced
    }

    @Test
    void monthWithThePriceAndShareOfEachCode() throws Exception {
        billing("2026-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-03"))
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.games.length()").value(2))
                .andExpect(jsonPath("$.games[0].gameTitle").value("A hra"))
                .andExpect(jsonPath("$.games[0].prices").value(contains(100, 120)))
                .andExpect(jsonPath("$.games[0].amounts.activatedCount").value(2))
                .andExpect(jsonPath("$.games[0].amounts.revenueCzk").value(220))
                .andExpect(jsonPath("$.games[0].amounts.partnerShareCzk").value(55.0))
                .andExpect(jsonPath("$.games[0].amounts.operatorCzk").value(165.0))
                // Nothing activated: the price valid at the end of the month
                .andExpect(jsonPath("$.games[1].prices").value(contains(50)))
                .andExpect(jsonPath("$.games[1].amounts.activatedCount").value(0))
                .andExpect(jsonPath("$.games[1].amounts.revenueCzk").value(0))
                .andExpect(jsonPath("$.total.activatedCount").value(2))
                .andExpect(jsonPath("$.total.operatorCzk").value(165.0))
                .andExpect(jsonPath("$.invoice").isEmpty())
                .andExpect(jsonPath("$.uninvoicedMonths").value(contains("2026-03", "2026-04")));
        billing("2026-04").andExpect(jsonPath("$.total.revenueCzk").value(120));
    }

    @Test
    void marksAnEndedMonthAsInvoicedAndBack() throws Exception {
        invoice("2026-03", "2026-04-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.invoicedOn").value("2026-04-05"))
                .andExpect(jsonPath("$.invoice.amountCzk").value(165.0))
                .andExpect(jsonPath("$.uninvoicedMonths").value(contains("2026-04")));
        // Marking again changes the day
        invoice("2026-03", "2026-04-07").andExpect(jsonPath("$.invoice.invoicedOn").value("2026-04-07"));

        mvc.perform(delete("/api/v1/admin/partners/{id}/billing/2026-03/invoice", partnerId).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice").isEmpty())
                .andExpect(jsonPath("$.uninvoicedMonths").value(contains("2026-03", "2026-04")));
    }

    @Test
    void invoicesOnlyAfterTheMonthEnded() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Prague"));
        invoice("2026-03", "2026-03-31").andExpect(status().isBadRequest());
        invoice("2026-03", today.plusDays(1).toString()).andExpect(status().isBadRequest());
        invoice(YearMonth.from(today).toString(), today.toString()).andExpect(status().isBadRequest());
        billing(YearMonth.from(today).toString()).andExpect(jsonPath("$.ended").value(false));
    }

    @Test
    void onlyForTheAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/partners/{id}/billing/2026-03", partnerId).with(user("hrad").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    private ResultActions billing(String month) throws Exception {
        return mvc.perform(get("/api/v1/admin/partners/{id}/billing/{month}", partnerId, month).with(admin()));
    }

    private ResultActions invoice(String month, String invoicedOn) throws Exception {
        return mvc.perform(put("/api/v1/admin/partners/{id}/billing/{month}/invoice", partnerId, month)
                .with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoicedOn\":\"" + invoicedOn + "\"}"));
    }

    private UUID game(UUID location, String title) {
        return insert("INSERT INTO game (location_id, slug, title, intro_text, outro_text) VALUES (?, ?, ?, 'I', 'O')"
                + " RETURNING id", location, "billing-" + UUID.randomUUID(), title);
    }

    private UUID batch(boolean development) {
        return insert("INSERT INTO access_code_batch (partner_id, game_id, quantity, multi_session, handed_over_at)"
                + " VALUES (?, ?, 10, ?, now()) RETURNING id", partnerId, gameId, development);
    }

    private void activate(UUID batchId, String at) {
        jdbc.update("INSERT INTO access_code (batch_id, code, status, activated_at) VALUES (?, ?, 'ACTIVATED', ?)",
                batchId, UUID.randomUUID().toString().substring(0, 20), Timestamp.from(Instant.parse(at)));
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
