package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class SessionApiTest {

    static final String DEMO_GAME_ID = "00000000-0000-0000-0000-000000000003";
    static final String STATION_1 = "00000000-0000-0000-0001-000000000001";
    static final String STATION_2 = "00000000-0000-0000-0001-000000000002";
    static final String STATION_3 = "00000000-0000-0000-0001-000000000003";
    static final String STATION_4 = "00000000-0000-0000-0001-000000000004";
    static final String STATION_5 = "00000000-0000-0000-0001-000000000005";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void playsDemoGameFromStartToFinale() throws Exception {
        String token = startGame("Kuchtovi");
        assertNoSecretsInState(token, STATION_1);

        // Station 1 (TEXT): wrong answer, one hint, then a correct variant with different case and spaces
        answer(token, STATION_1, "kámen")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value(SessionService.WRONG_ANSWER_MESSAGE))
                .andExpect(jsonPath("$.rewardText").doesNotExist());
        hint(token, STATION_1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealedHints[*].text").value(contains("Myslí se něco, co vzniká kopáním.")))
                .andExpect(jsonPath("$.hintsAvailable").value(1))
                .andExpect(jsonPath("$.nextHintPenaltySeconds").value(120))
                .andExpect(jsonPath("$.penaltySeconds").value(60));
        assertNoSecretsInState(token, STATION_1);
        answer(token, STATION_1, " JÁMA ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.rewardText").value(startsWith("Správně!")))
                .andExpect(jsonPath("$.nextStationId").value(STATION_2))
                .andExpect(jsonPath("$.gameCompleted").value(false));

        // Station 2 (NUMBER): both hints, a third one is refused, answer with a unit
        assertNoSecretsInState(token, STATION_2);
        hint(token, STATION_2).andExpect(status().isOk());
        hint(token, STATION_2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealedHints", hasSize(2)))
                .andExpect(jsonPath("$.hintsAvailable").value(0))
                .andExpect(jsonPath("$.nextHintPenaltySeconds").doesNotExist())
                .andExpect(jsonPath("$.penaltySeconds").value(240));
        hint(token, STATION_2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
        answer(token, STATION_2, "padesát šest")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value(SessionService.NOT_A_NUMBER_MESSAGE));
        answer(token, STATION_2, "56 schodů").andExpect(jsonPath("$.correct").value(true));

        // Station 3 (CHOICE): choices are offered as texts, the client sends the chosen text
        state(token)
                .andExpect(jsonPath("$.currentStation.taskType").value("CHOICE"))
                .andExpect(jsonPath("$.currentStation.choices").value(contains("Meč", "Provaz", "Lucernu", "Klíč")));
        answer(token, STATION_3, "Lucernu").andExpect(jsonPath("$.correct").value(true));

        // Stations 4 and 5 (TEXT)
        assertNoSecretsInState(token, STATION_4);
        answer(token, STATION_4, "brana").andExpect(jsonPath("$.correct").value(true));
        assertNoSecretsInState(token, STATION_5);
        answer(token, STATION_5, "Klíč!")
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.nextStationId").doesNotExist())
                .andExpect(jsonPath("$.gameCompleted").value(true));

        state(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.teamName").value("Kuchtovi"))
                .andExpect(jsonPath("$.penaltySeconds").value(240))
                .andExpect(jsonPath("$.progress.solved").value(5))
                .andExpect(jsonPath("$.progress.total").value(5))
                .andExpect(jsonPath("$.currentStation").isEmpty())
                .andExpect(jsonPath("$.solvedStations[*].orderNo").value(contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.finale.outroText").value(startsWith("Klíč ležel")))
                .andExpect(jsonPath("$.finale.hintsUsed").value(3))
                .andExpect(jsonPath("$.finale.totalSeconds").value(greaterThanOrEqualTo(240)))
                .andExpect(jsonPath("$.token").doesNotExist());

        // Every attempt is logged, progress counters match
        UUID sessionId = sessionId(token);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ?", Integer.class,
                sessionId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ? AND correct",
                Integer.class, sessionId)).isEqualTo(5);
        Map<String, Object> first = jdbc.queryForMap(
                "SELECT attempts_count, hints_used, status, solved_at FROM session_progress"
                        + " WHERE session_id = ? AND station_id = ?::uuid", sessionId, STATION_1);
        assertThat(first).containsEntry("attempts_count", 2).containsEntry("hints_used", 1)
                .containsEntry("status", "SOLVED");
        assertThat(first.get("solved_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT normalized_answer FROM answer_attempt"
                + " WHERE session_id = ? AND raw_answer = ' JÁMA '", String.class, sessionId)).isEqualTo("jama");
        assertThat(jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM game_session WHERE id = ?",
                Boolean.class, sessionId)).isTrue();

        // A completed game cannot be played any more
        answer(token, STATION_5, "klic")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
    }

    @Test
    void startReturnsTokenAndFirstStation() throws Exception {
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"teamName\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/current"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.teamName").isEmpty())
                .andExpect(jsonPath("$.game.id").value(DEMO_GAME_ID))
                .andExpect(jsonPath("$.progress.solved").value(0))
                .andExpect(jsonPath("$.progress.total").value(5))
                .andExpect(jsonPath("$.currentStation.id").value(STATION_1))
                .andExpect(jsonPath("$.currentStation.choices").isEmpty())
                .andExpect(jsonPath("$.currentStation.hintsAvailable").value(2))
                .andExpect(jsonPath("$.currentStation.nextHintPenaltySeconds").value(60))
                .andExpect(jsonPath("$.currentStation.revealedHints").isEmpty())
                .andExpect(jsonPath("$.solvedStations").isEmpty())
                .andExpect(jsonPath("$.finale").isEmpty());
    }

    @Test
    void lockedOrUnknownStationCannotBeAnswered() throws Exception {
        String token = startGame(null);

        answer(token, STATION_2, "56")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
        hint(token, STATION_3).andExpect(status().isConflict());
        answer(token, UUID.randomUUID().toString(), "56")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/not-found"));

        // A refused answer is not logged as an attempt
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ?", Integer.class,
                sessionId(token))).isZero();
    }

    @Test
    void solvedStationCannotBeAnsweredAgain() throws Exception {
        String token = startGame(null);
        answer(token, STATION_1, "jáma").andExpect(jsonPath("$.correct").value(true));

        answer(token, STATION_1, "jáma").andExpect(status().isConflict());
    }

    @Test
    void missingMalformedOrUnknownTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/invalid-token"));
        mvc.perform(get("/api/v1/sessions/current").header(SessionController.TOKEN_HEADER, "abc"))
                .andExpect(status().isUnauthorized());
        state(UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        answer(UUID.randomUUID().toString(), STATION_1, "jáma").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionIsGoneAndMarkedExpired() throws Exception {
        String token = startGame(null);
        jdbc.update("UPDATE game_session SET expires_at = now() - interval '1 minute' WHERE token = ?::uuid", token);

        state(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("/problems/session-expired"));
        answer(token, STATION_1, "jáma").andExpect(status().isGone());

        assertThat(jdbc.queryForObject("SELECT status FROM game_session WHERE token = ?::uuid", String.class, token))
                .isEqualTo("EXPIRED");
    }

    @Test
    void abandonedSessionCannotBePlayed() throws Exception {
        String token = startGame(null);

        abandon(token).andExpect(status().isNoContent());
        abandon(token).andExpect(status().isNoContent());

        state(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.currentStation").isEmpty());
        answer(token, STATION_1, "jáma").andExpect(status().isConflict());
        hint(token, STATION_1).andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsAreValidationProblems() throws Exception {
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("gameId"));
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"teamName\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        String token = startGame(null);
        answer(token, STATION_1, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("answer"));
        answer(token, STATION_1, "x".repeat(201)).andExpect(status().isBadRequest());
    }

    /**
     * Neither the accepted answers of the current station nor its hints the player has not
     * revealed may appear anywhere in the state response.
     */
    private void assertNoSecretsInState(String token, String stationId) throws Exception {
        String body = state(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStation.id").value(stationId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContainIgnoringCase("acceptedAnswer").doesNotContainIgnoringCase("normalized");

        int hintsUsed = JsonPath.read(body, "$.currentStation.revealedHints.length()");
        List<String> unrevealedHints = jdbc.queryForList(
                "SELECT text FROM hint WHERE station_id = ?::uuid AND order_no > ?", String.class, stationId, hintsUsed);
        assertThat(unrevealedHints).allSatisfy(hint -> assertThat(body).doesNotContain(hint));

        // Checked only within the current station: the game title or rewards of solved stations may
        // legitimately contain the same word (e.g. "Tajemství katova klíče"). CHOICE offers the answer as a choice.
        Map<String, Object> current = JsonPath.read(body, "$.currentStation");
        if (!"CHOICE".equals(current.get("taskType"))) {
            List<String> answers = jdbc.queryForList(
                    "SELECT value FROM accepted_answer WHERE station_id = ?::uuid", String.class, stationId);
            String visibleText = String.join(" ", (String) current.get("title"), (String) current.get("storyText"),
                    (String) current.get("taskText"));
            assertThat(answers).allSatisfy(answer -> assertThat(visibleText).doesNotContainIgnoringCase(answer));
        }
    }

    private String startGame(String teamName) throws Exception {
        String team = teamName == null ? "null" : "\"" + teamName + "\"";
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"teamName\":" + team + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    private UUID sessionId(String token) {
        return jdbc.queryForObject("SELECT id FROM game_session WHERE token = ?::uuid", UUID.class, token);
    }

    private ResultActions state(String token) throws Exception {
        return mvc.perform(get("/api/v1/sessions/current").header(SessionController.TOKEN_HEADER, token));
    }

    private ResultActions answer(String token, String stationId, String answer) throws Exception {
        String json = "{\"answer\":\"" + answer + "\"}";
        return mvc.perform(post("/api/v1/sessions/current/stations/{id}/answers", stationId)
                .header(SessionController.TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions hint(String token, String stationId) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/stations/{id}/hints", stationId)
                .header(SessionController.TOKEN_HEADER, token));
    }

    private ResultActions abandon(String token) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/abandon").header(SessionController.TOKEN_HEADER, token));
    }
}
