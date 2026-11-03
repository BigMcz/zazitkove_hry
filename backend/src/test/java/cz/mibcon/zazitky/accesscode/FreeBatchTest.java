package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Cards given away: free single-use codes out of the money, and a shared demo code on many cards. */
@IntegrationTest
class FreeBatchTest {

    static final int YEAR = Year.now(ZoneId.of("Europe/Prague")).getValue();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID partnerId;
    UUID gameId;

    @BeforeEach
    void castleWithAGame() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        partnerId = insert("INSERT INTO partner (name) VALUES (?) RETURNING id", "Dárkový hrad " + suffix);
        jdbc.update("INSERT INTO partner_share (partner_id, valid_from, share_pct) VALUES (?, DATE '2020-01-01', 25)",
                partnerId);
        UUID location = insert("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id", partnerId);
        gameId = insert("INSERT INTO game (location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, 'Hra', 'I', 'O', 'PUBLISHED') RETURNING id", location, "zdarma-" + suffix);
        jdbc.update("INSERT INTO game_price (game_id, valid_from, price_czk) VALUES (?, DATE '2020-01-01', 200)", gameId);
        jdbc.update("INSERT INTO station (game_id, order_no, title, story_text, task_text, task_type, reward_text)"
                + " VALUES (?, 1, 'S', 'Příběh', 'Úkol', 'TEXT', 'Odměna')", gameId);
    }

    @Test
    void freeCodesArePlayedButNotInTheMoneyOrStock() throws Exception {
        String body = create("{\"gameId\":\"" + gameId + "\",\"kind\":\"FREE\",\"quantity\":3,\"validityHours\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.free").value(true))
                .andExpect(jsonPath("$.multiSession").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID batch = UUID.fromString(JsonPath.read(body, "$.id"));
        handOver(batch);
        start(jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ? AND serial_no = 1", String.class, batch))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/admin/reports/monthly-activations").param("year", String.valueOf(YEAR))
                        .param("partnerId", partnerId.toString()).with(admin()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/admin/dashboard").param("year", String.valueOf(YEAR))
                        .param("partnerId", partnerId.toString()).with(admin()))
                .andExpect(jsonPath("$.activatedInYear").value(0))
                .andExpect(jsonPath("$.cardsInStock").value(0))
                .andExpect(jsonPath("$.cardsDelivered").value(0));
    }

    @Test
    void aDemoCodeIsSharedNeverExpiresAndStartsANewGameEveryTime() throws Exception {
        String code = "DM" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String body = create("{\"gameId\":\"" + gameId + "\",\"kind\":\"DEMO\",\"code\":\""
                        + AccessCodeFormat.format(code).toLowerCase() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.multiSession").value(true))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.validityHours").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID batch = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ?", String.class, batch))
                .isEqualTo(code);

        create("{\"gameId\":\"" + gameId + "\",\"kind\":\"DEMO\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest());
        create("{\"gameId\":\"" + gameId + "\",\"kind\":\"DEMO\",\"code\":\"KRATKY\"}")
                .andExpect(status().isBadRequest());

        handOver(batch);
        String first = JsonPath.read(start(code).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.token");
        String second = JsonPath.read(start(code).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.token");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void aSaleBatchNeedsQuantityAndValidity() throws Exception {
        create("{\"gameId\":\"" + gameId + "\",\"quantity\":3}").andExpect(status().isBadRequest());
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void handOver(UUID batch) throws Exception {
        mvc.perform(post("/api/v1/admin/batches/{id}/handover", batch).with(admin()).with(csrf()))
                .andExpect(status().isOk());
    }

    private ResultActions start(String code) throws Exception {
        return mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
