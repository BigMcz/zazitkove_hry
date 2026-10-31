package cz.mibcon.zazitky.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** POST /partner/stock-count: the castle lowers the stock of each game to the cards it really has. */
@IntegrationTest
class StockCountTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    final int year = Year.now(ZoneId.of("Europe/Prague")).getValue();
    UUID partnerId;
    UUID location;
    /** 10 cards, 2 activated. */
    UUID gameA;
    /** 5 cards, none activated. */
    UUID gameB;
    PartnerUserDetails staff;

    @BeforeEach
    void partnerWithTwoGames() {
        partnerId = jdbc.queryForObject("INSERT INTO partner (name) VALUES (?) RETURNING id", UUID.class,
                "Inventurní hrad " + UUID.randomUUID().toString().substring(0, 8));
        location = jdbc.queryForObject("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id",
                UUID.class, partnerId);
        gameA = game("Hra A");
        gameB = game("Hra B");
        activate(batch(gameA, 10, "now()"), 2, "now() - interval '1 hour'");
        batch(gameB, 5, "now()");
        UUID userId = jdbc.queryForObject("""
                INSERT INTO partner_user (partner_id, username, password_hash) VALUES (?, ?, '{noop}x') RETURNING id
                """, UUID.class, partnerId, "inventura-" + UUID.randomUUID());
        staff = new PartnerUserDetails(userId, partnerId, "Inventurní hrad", "inventura", "{noop}x", true);
    }

    @Test
    void lowersTheStockOfEachGameButNeverAboveOurNumberAndKeepsTheMoney() throws Exception {
        count(Map.of(gameA, 9, gameB, 5)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Hra Hra A: na skladě můžete mít nejvýš 8 kartiček, "
                        + "tolik převzatých kartiček zatím nikdo neaktivoval."));

        count(Map.of(gameA, 5, gameB, 4)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cardsInStock").value(9))
                .andExpect(jsonPath("$.cardsInStockComputed").value(13))
                .andExpect(jsonPath("$.stockCountedAt").isNotEmpty())
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra A')].cardsInStock").value(5))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra A')].cardsInStockComputed").value(8))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra B')].cardsInStock").value(4))
                .andExpect(jsonPath("$.activatedInYear").value(2))
                .andExpect(jsonPath("$.revenueCzk").value(200))
                .andExpect(jsonPath("$.profitCzk").doesNotExist());

        // Since the count: a new delivery of 4 cards of game A and 1 activation of it
        activate(batch(gameA, 4, "now() + interval '2 seconds'"), 1, "now() + interval '3 seconds'");
        summary().andExpect(jsonPath("$.cardsInStock").value(12))
                .andExpect(jsonPath("$.cardsInStockComputed").value(16))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra A')].cardsInStock").value(8))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra B')].cardsInStock").value(4));
        String row = "$[?(@.partnerId == '" + partnerId + "')].";
        mvc.perform(get("/api/v1/admin/reports/partners").param("year", String.valueOf(year))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath(row + "cardsInStock").value(12))
                .andExpect(jsonPath(row + "cardsInStockComputed").value(16))
                .andExpect(jsonPath(row + "games.length()").value(2));
        mvc.perform(get("/api/v1/admin/dashboard").param("year", String.valueOf(year))
                        .param("partnerId", partnerId.toString()).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.cardsInStock").value(12));
    }

    @Test
    void countsOnlyTheGamesEntered() throws Exception {
        count(Map.of(gameB, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra A')].cardsInStock").value(8))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra A')].stockCountedAt").value((Object) null))
                .andExpect(jsonPath("$.games[?(@.gameTitle == 'Hra B')].cardsInStock").value(1))
                .andExpect(jsonPath("$.cardsInStock").value(9));
    }

    @Test
    void acceptsOnlyItsOwnGamesAndAValidNumber() throws Exception {
        UUID notDelivered = game("Hra bez kartiček");
        count(Map.of(notDelivered, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Kartičky této hry jste zatím nepřevzali."));
        count(Map.of(gameA, -1)).andExpect(status().isBadRequest());
        String body = "{\"games\":[{\"gameId\":\"" + gameA + "\",\"cardsInStock\":1}]}";
        mvc.perform(post("/api/v1/partner/stock-count").param("year", String.valueOf(year)).with(partner())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/partner/stock-count").param("year", String.valueOf(year)).with(csrf())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private ResultActions count(Map<UUID, Integer> cards) throws Exception {
        String games = cards.entrySet().stream()
                .map(entry -> "{\"gameId\":\"" + entry.getKey() + "\",\"cardsInStock\":" + entry.getValue() + "}")
                .collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/partner/stock-count").param("year", String.valueOf(year))
                .with(partner()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"games\":[" + games + "]}"));
    }

    private ResultActions summary() throws Exception {
        return mvc.perform(get("/api/v1/partner/summary").param("year", String.valueOf(year)).with(partner()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor partner() {
        return user(staff);
    }

    private UUID game(String title) {
        return jdbc.queryForObject("""
                INSERT INTO game (location_id, slug, title, intro_text, outro_text)
                VALUES (?, ?, ?, 'Úvod', 'Závěr') RETURNING id
                """, UUID.class, location, "stock-" + UUID.randomUUID(), title);
    }

    private UUID batch(UUID gameId, int quantity, String handedOverAt) {
        UUID batchId = jdbc.queryForObject("""
                INSERT INTO access_code_batch (partner_id, game_id, quantity, unit_price_czk, handed_over_at)
                VALUES (?, ?, ?, 100, %s) RETURNING id
                """.formatted(handedOverAt), UUID.class, partnerId, gameId, quantity);
        for (int i = 0; i < quantity; i++) {
            jdbc.update("INSERT INTO access_code (batch_id, code) VALUES (?, ?)", batchId,
                    UUID.randomUUID().toString().substring(0, 20));
        }
        return batchId;
    }

    private void activate(UUID batchId, int count, String activatedAt) {
        jdbc.update("""
                UPDATE access_code SET status = 'ACTIVATED', activated_at = %s
                WHERE id IN (SELECT id FROM access_code WHERE batch_id = ? AND status = 'NEW' LIMIT ?)
                """.formatted(activatedAt), batchId, count);
    }
}
