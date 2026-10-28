package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AccessCodeApiTest {

    static final UUID DEMO_GAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccessCodeAdminService adminService;

    @Test
    void newCodeIsActivatedByStartingAGameAndValidForFiveHours() throws Exception {
        String code = newCode();
        String typed = code.toLowerCase().replace("-", " ");

        check(typed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.game.title").value("Tajemství katova klíče"))
                .andExpect(jsonPath("$.game.stationCount").value(5))
                .andExpect(jsonPath("$.resumesGame").value(false))
                .andExpect(jsonPath("$.validUntil").isEmpty());
        assertThat(codeRow(code)).containsEntry("status", "NEW");

        start(typed, "Kuchtovi")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.teamName").value("Kuchtovi"));

        Map<String, Object> row = codeRow(code);
        assertThat(row).containsEntry("status", "ACTIVATED");
        Instant activatedAt = ((Timestamp) row.get("activated_at")).toInstant();
        Instant validUntil = ((Timestamp) row.get("valid_until")).toInstant();
        assertThat(Duration.between(activatedAt, validUntil)).isEqualTo(Duration.ofHours(5));
        assertThat(row.get("sold_at")).as("sold = activated, no separate sale").isNull();
        Instant sessionExpires = jdbc.queryForObject(
                "SELECT s.expires_at FROM game_session s JOIN access_code c ON c.id = s.access_code_id WHERE c.code = ?",
                Timestamp.class, AccessCodeFormat.normalize(code)).toInstant();
        assertThat(sessionExpires).as("the game ends with the code at the latest").isEqualTo(validUntil);
    }

    @Test
    void sameCodeContinuesTheSameGameOnAnotherDevice() throws Exception {
        String code = newCode();
        String token = token(start(code, null).andExpect(status().isCreated()));

        check(code).andExpect(jsonPath("$.resumesGame").value(true)).andExpect(jsonPath("$.validUntil").isString());
        String again = token(start(code, "Jiný tým").andExpect(status().isOk()));

        assertThat(again).isEqualTo(token);
        assertThat(sessionCount(code)).isOne();
    }

    @Test
    void abandonedGameCanBeStartedAgainWhileTheCodeIsValid() throws Exception {
        String code = newCode();
        String first = token(start(code, null));
        mvc.perform(post("/api/v1/sessions/current/abandon").header("X-Session-Token", first))
                .andExpect(status().isNoContent());

        String second = token(start(code, null).andExpect(status().isCreated()));

        assertThat(second).isNotEqualTo(first);
        assertThat(sessionCount(code)).isEqualTo(2);
    }

    @Test
    void expiredCodeIsGoneAndMarkedExpired() throws Exception {
        String code = newCode();
        start(code, null);
        jdbc.update("UPDATE access_code SET valid_until = now() - interval '1 minute' WHERE code = ?",
                AccessCodeFormat.normalize(code));

        check(code)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("/problems/code-expired"));
        start(code, null).andExpect(status().isGone());

        assertThat(codeRow(code)).containsEntry("status", "EXPIRED");
    }

    @Test
    void unknownOrCancelledCodeIsInvalid() throws Exception {
        check("ZZZZ-ZZZZ")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/invalid-code"));

        String code = newCode();
        jdbc.update("UPDATE access_code SET status = 'CANCELLED' WHERE code = ?", AccessCodeFormat.normalize(code));
        check(code).andExpect(status().isNotFound());
        start(code, null).andExpect(status().isNotFound());
    }

    @Test
    void developmentCodeNeverExpiresAndStartsANewGameEveryTime() throws Exception {
        check(IntegrationTest.DEV_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumesGame").value(false));

        String first = token(start("demokata", null).andExpect(status().isCreated()));
        String second = token(start(IntegrationTest.DEV_CODE, null).andExpect(status().isCreated()));

        assertThat(second).isNotEqualTo(first);
        assertThat(codeRow(IntegrationTest.DEV_CODE).get("valid_until")).isNull();
    }

    @Test
    void tooManyAttemptsFromOneAddressAreRejected() throws Exception {
        String ip = "10.20.30.40";
        for (int i = 0; i < IntegrationTest.CODE_ATTEMPTS_PER_MINUTE; i++) {
            check("ZZZZ-ZZZZ", ip).andExpect(status().isNotFound());
        }

        check(IntegrationTest.DEV_CODE, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("/problems/too-many-requests"));
        check(IntegrationTest.DEV_CODE, "10.20.30.41").andExpect(status().isOk());
    }

    private String newCode() {
        UUID batchId = adminService.createBatch(new CreateBatchRequest(DEMO_GAME_ID, 1, 5, "test", null)).id();
        String code = jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ?", String.class, batchId);
        return AccessCodeFormat.format(code);
    }

    private Map<String, Object> codeRow(String code) {
        return jdbc.queryForMap("SELECT status, activated_at, valid_until, sold_at FROM access_code WHERE code = ?",
                AccessCodeFormat.normalize(code));
    }

    private int sessionCount(String code) {
        return jdbc.queryForObject("SELECT count(*) FROM game_session s JOIN access_code c ON c.id = s.access_code_id"
                + " WHERE c.code = ?", Integer.class, AccessCodeFormat.normalize(code));
    }

    private ResultActions check(String code) throws Exception {
        return check(code, "127.0.0.1");
    }

    private ResultActions check(String code, String ip) throws Exception {
        return mvc.perform(post("/api/v1/access-codes/check")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions start(String code, String teamName) throws Exception {
        String team = teamName == null ? "null" : "\"" + teamName + "\"";
        return mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"code\":\"" + code + "\",\"teamName\":" + team + "}").getBytes(StandardCharsets.UTF_8)));
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }
}
