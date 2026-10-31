package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** POST /sessions/current/sync: actions played in the phone without a signal, sent later. */
@IntegrationTest
class SessionSyncTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    String token;
    /** The game started an hour ago, so that the actions can be played in the past. */
    Instant startedAt;

    @BeforeEach
    void startGameAnHourAgo() throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = json.readTree(body).get("token").asString();
        startedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        jdbc.update("UPDATE game_session SET started_at = ? WHERE token = ?::uuid", Timestamp.from(startedAt), token);
    }

    @Test
    void appliesTheActionsAtTheTimeTheyWerePlayed() throws Exception {
        sync(List.of(
                answer(SessionApiTest.STATION_1, "Tvar hradní věže", 5),
                hint(SessionApiTest.STATION_1, 6),
                answer(SessionApiTest.STATION_1, SessionApiTest.STATION_1_ANSWER, 7),
                answer(SessionApiTest.STATION_2, "Věznice", 15)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.solved").value(2))
                .andExpect(jsonPath("$.penaltySeconds").value(60))
                .andExpect(jsonPath("$.currentStation.id").value(SessionApiTest.STATION_3));

        assertThat(jdbc.queryForList("SELECT created_at FROM answer_attempt WHERE session_id = ? ORDER BY created_at",
                Timestamp.class, sessionId()).stream().map(Timestamp::toInstant))
                .containsExactly(minute(5), minute(7), minute(15));
        assertThat(jdbc.queryForObject("SELECT solved_at FROM session_progress WHERE session_id = ? AND station_id = ?::uuid",
                Timestamp.class, sessionId(), SessionApiTest.STATION_1).toInstant()).isEqualTo(minute(7));
    }

    @Test
    void appliesAnActionSentTwiceOnlyOnce() throws Exception {
        List<Map<String, Object>> played = List.of(
                answer(SessionApiTest.STATION_1, "Tvar hradní věže", 5), hint(SessionApiTest.STATION_1, 6));

        sync(played).andExpect(status().isOk());
        sync(played).andExpect(status().isOk()).andExpect(jsonPath("$.penaltySeconds").value(60));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ?", Integer.class,
                sessionId())).isEqualTo(1);
    }

    @Test
    void keepsPhoneTimesWithinTheGame() throws Exception {
        Map<String, Object> beforeStart = answer(SessionApiTest.STATION_1, "Tvar hradní věže", -30);
        Map<String, Object> inFuture = answer(SessionApiTest.STATION_1, "Jméno prvního majitele", 24 * 60);

        sync(List.of(beforeStart, inFuture)).andExpect(status().isOk());

        List<Instant> times = jdbc.queryForList("SELECT created_at FROM answer_attempt WHERE session_id = ? "
                + "ORDER BY created_at", Timestamp.class, sessionId()).stream().map(Timestamp::toInstant).toList();
        assertThat(times.getFirst()).isEqualTo(startedAt);
        assertThat(times.getLast()).isBefore(Instant.now().plusSeconds(1)).isAfter(minute(59));
    }

    @Test
    void countsActionsPlayedBeforeTheCodeExpiredEvenWhenTheyArriveLater() throws Exception {
        jdbc.update("UPDATE game_session SET expires_at = ? WHERE token = ?::uuid", Timestamp.from(minute(30)), token);

        sync(List.of(
                answer(SessionApiTest.STATION_1, SessionApiTest.STATION_1_ANSWER, 10),
                answer(SessionApiTest.STATION_2, "Věznice", 40)))
                .andExpect(status().isGone());

        assertThat(jdbc.queryForList("SELECT s.order_no FROM session_progress p JOIN station s ON s.id = p.station_id "
                + "WHERE p.session_id = ? AND p.status = 'SOLVED'", Integer.class, sessionId())).containsExactly(1);
        assertThat(jdbc.queryForObject("SELECT status FROM game_session WHERE token = ?::uuid", String.class, token))
                .isEqualTo("EXPIRED");
    }

    @Test
    void ignoresAnActionTheRulesNoLongerAllow() throws Exception {
        sync(List.of(
                answer(SessionApiTest.STATION_1, SessionApiTest.STATION_1_ANSWER, 5),
                // the same station again, e.g. from a second phone that did not know it was solved
                answer(SessionApiTest.STATION_1, SessionApiTest.STATION_1_ANSWER, 6),
                hint(SessionApiTest.STATION_3, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.solved").value(1))
                .andExpect(jsonPath("$.penaltySeconds").value(0));
    }

    @Test
    void rejectsAnIncompleteAction() throws Exception {
        mvc.perform(post("/api/v1/sessions/current/sync").header(SessionController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"ANSWER\"}]}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions sync(List<Map<String, Object>> played) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/sync").header(SessionController.TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("events", played))));
    }

    private Map<String, Object> answer(String stationId, String answer, int minute) {
        return Map.of("id", UUID.randomUUID(), "type", "ANSWER", "stationId", stationId, "answer", answer,
                "at", minute(minute).toString());
    }

    private Map<String, Object> hint(String stationId, int minute) {
        return Map.of("id", UUID.randomUUID(), "type", "HINT", "stationId", stationId, "at", minute(minute).toString());
    }

    private Instant minute(int minute) {
        return startedAt.plus(minute, ChronoUnit.MINUTES);
    }

    private UUID sessionId() {
        return jdbc.queryForObject("SELECT id FROM game_session WHERE token = ?::uuid", UUID.class, token);
    }
}
