package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.admin.PartnerUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Generate → print → hand over: codes of a batch in production must not work. */
@IntegrationTest
class BatchHandoverTest {

    static final int YEAR = Year.now(ZoneId.of("Europe/Prague")).getValue();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void codesOfABatchInProductionDoNotWorkUntilHandedOver() throws Exception {
        Castle castle = createCastle();
        UUID batch = createBatch(castle.gameId, 3);
        String code = AccessCodeFormat.format(firstCode(batch));

        mvc.perform(get("/api/v1/admin/batches").param("partnerId", castle.partnerId.toString()).with(admin()))
                .andExpect(jsonPath("$[0].handedOverAt").isEmpty())
                .andExpect(jsonPath("$[0].notActivatedCount").value(3));
        check(code)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/invalid-code"));
        startGame(code).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handedOverAt").isString());

        check(code).andExpect(status().isOk());
        startGame(code).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/batches").param("partnerId", castle.partnerId.toString()).with(admin()))
                .andExpect(jsonPath("$[0].activatedCount").value(1))
                .andExpect(jsonPath("$[0].notActivatedCount").value(2));
    }

    @Test
    void handoverCanBeUndoneOnlyWhileNoCodeWasUsed() throws Exception {
        Castle castle = createCastle();
        UUID batch = createBatch(castle.gameId, 2);
        String code = AccessCodeFormat.format(firstCode(batch));

        handOver(batch);
        mvc.perform(delete("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handedOverAt").isEmpty());
        check(code).andExpect(status().isNotFound());

        handOver(batch);
        startGame(code).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
    }

    @Test
    void castleSeesOnlyHandedOverCardsAndItsStock() throws Exception {
        Castle castle = createCastle();
        UUID delivered = createBatch(castle.gameId, 10);
        UUID inProduction = createBatch(castle.gameId, 5);
        handOver(delivered);
        startGame(AccessCodeFormat.format(firstCode(delivered))).andExpect(status().isCreated());

        MockHttpSession session = loginAsCastle(castle);
        String batches = body(mvc.perform(get("/api/v1/partner/batches").session(session)).andExpect(status().isOk()));
        List<String> ids = JsonPath.read(batches, "$[*].id");
        assertThat(ids).containsExactly(delivered.toString()).doesNotContain(inProduction.toString());

        mvc.perform(get("/api/v1/partner/summary").param("year", String.valueOf(YEAR)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardsInStock").value(9))
                .andExpect(jsonPath("$.cardsDelivered").value(10))
                .andExpect(jsonPath("$.cardsInProduction").value(5))
                .andExpect(jsonPath("$.activatedInYear").value(1))
                .andExpect(jsonPath("$.activatedThisMonth").value(1))
                .andExpect(jsonPath("$.revenueCzk").value(200))
                .andExpect(jsonPath("$.partnerShareCzk").value(50.0))
                .andExpect(jsonPath("$.profitCzk").doesNotExist());
    }

    @Test
    void dashboardShowsTotalsAndProfitForOnePartnerAndAll() throws Exception {
        Castle castle = createCastle();
        UUID batch = createBatch(castle.gameId, 4);
        handOver(batch);
        List<String> codes = jdbc.queryForList("SELECT code FROM access_code WHERE batch_id = ?", String.class, batch);
        startGame(codes.get(0)).andExpect(status().isCreated());
        startGame(codes.get(1)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/admin/dashboard").param("year", String.valueOf(YEAR))
                        .param("partnerId", castle.partnerId.toString()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activatedInYear").value(2))
                .andExpect(jsonPath("$.revenueCzk").value(400))
                .andExpect(jsonPath("$.partnerShareCzk").value(100.0)) // 25 %
                .andExpect(jsonPath("$.profitCzk").value(300.0))
                .andExpect(jsonPath("$.cardsInStock").value(2));

        String all = body(mvc.perform(get("/api/v1/admin/dashboard").param("year", String.valueOf(YEAR)).with(admin()))
                .andExpect(status().isOk()));
        assertThat(JsonPath.<Number>read(all, "$.activatedInYear").longValue()).isGreaterThanOrEqualTo(2);
        assertThat(JsonPath.<Number>read(all, "$.cardsInStock").longValue()).isGreaterThanOrEqualTo(2);
        mvc.perform(get("/api/v1/admin/dashboard").param("year", String.valueOf(YEAR))
                        .with(user("hrad").roles(PartnerUserDetails.ROLE)))
                .andExpect(status().isForbidden());
    }

    record Castle(UUID partnerId, UUID gameId, String username) {
    }

    /** Castle with a published game (one station), a share of 25 % and an account. */
    private Castle createCastle() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID partner = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        jdbc.update("INSERT INTO partner (id, name) VALUES (?, ?)", partner, "Hrad " + suffix);
        jdbc.update("INSERT INTO partner_share (partner_id, valid_from, share_pct) VALUES (?, DATE '2020-01-01', ?)",
                partner, new BigDecimal("25"));
        jdbc.update("INSERT INTO location (id, partner_id, name) VALUES (?, ?, 'Hrad')", location, partner);
        jdbc.update("INSERT INTO game (id, location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, ?, 'Hra', 'Intro', 'Outro', 'PUBLISHED')", game, location, "hra-" + suffix);
        jdbc.update("INSERT INTO station (game_id, order_no, title, story_text, task_text, task_type, reward_text)"
                + " VALUES (?, 1, 'S', 'Příběh', 'Úkol', 'TEXT', 'Odměna')", game);
        String username = "pokladna-" + suffix;
        jdbc.update("INSERT INTO partner_user (partner_id, username, password_hash) VALUES (?, ?, ?)", partner,
                username, "{noop}heslo-pokladny");
        return new Castle(partner, game, username);
    }

    private UUID createBatch(UUID gameId, int quantity) throws Exception {
        String body = body(mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + gameId + "\",\"quantity\":" + quantity
                                + ",\"validityHours\":5,\"unitPriceCzk\":200}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handedOverAt").isEmpty()));
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void handOver(UUID batch) throws Exception {
        mvc.perform(post("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()))
                .andExpect(status().isOk());
    }

    private String firstCode(UUID batch) {
        return jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ? ORDER BY code LIMIT 1",
                String.class, batch);
    }

    private MockHttpSession loginAsCastle(Castle castle) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/admin/login")
                        .param("username", castle.username).param("password", "heslo-pokladny").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    private ResultActions check(String code) throws Exception {
        return mvc.perform(post("/api/v1/access-codes/check").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions startGame(String code) throws Exception {
        return mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
