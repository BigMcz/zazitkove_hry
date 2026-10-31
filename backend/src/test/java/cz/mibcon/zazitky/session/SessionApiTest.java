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
    static final String STATION_1 = station(1);
    static final String STATION_2 = station(2);
    static final String STATION_3 = station(3);
    static final String STATION_4 = station(4);
    static final String STATION_10 = station(10);
    static final String STATION_1_ANSWER = "Ohyb řeky jako ohnutá paže";

    /** Stations 5–7 played straight through: the accepted answer of each. */
    static final List<String> MIDDLE_ANSWERS = List.of(
            "Žádné mučení, jen co nařídil soud",
            "Aby odsouzeného zaměstnal a uklidnil",
            "Protože i Kristus šel pěšky");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void playsDemoGameFromStartToFinale() throws Exception {
        String token = startGame("Kuchtovi");
        assertNoSecretsInState(token, STATION_1);
        state(token)
                .andExpect(jsonPath("$.puzzle.slots", hasSize(9)))
                .andExpect(jsonPath("$.puzzle.slots[?(@ != null)]").isEmpty())
                .andExpect(jsonPath("$.puzzle.latestSlot").isEmpty());

        // Station 1 (CHOICE): wrong choice, its only hint, then the right choice with different case and spaces
        answer(token, STATION_1, "Tvar hradní věže")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value(SessionService.WRONG_ANSWER_MESSAGE))
                .andExpect(jsonPath("$.rewardText").doesNotExist());
        hint(token, STATION_1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealedHints[*].text").value(contains("Podívej se, co dělá řeka kolem skály.")))
                .andExpect(jsonPath("$.hintsAvailable").value(0))
                .andExpect(jsonPath("$.nextHintPenaltySeconds").doesNotExist())
                .andExpect(jsonPath("$.penaltySeconds").value(60));
        assertNoSecretsInState(token, STATION_1);
        answer(token, STATION_1, "  OHYB ŘEKY jako ohnutá paže ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.rewardText").value(startsWith("Správně.")))
                .andExpect(jsonPath("$.rewardAudioUrl").value("/audio/demo/st01-odmena.mp3"))
                .andExpect(jsonPath("$.nextText").value(startsWith("Vystoupej k hradní bráně.")))
                .andExpect(jsonPath("$.nextStationId").value(STATION_2))
                .andExpect(jsonPath("$.gameCompleted").value(false));

        // Station 2 (CHOICE): choices are offered as texts, the client sends the chosen text;
        // the puzzle has the letter of station 1 in a scrambled slot
        state(token)
                .andExpect(jsonPath("$.puzzle.slots[?(@ != null)]").value(contains("P")))
                .andExpect(jsonPath("$.puzzle.latestSlot").isNumber())
                .andExpect(jsonPath("$.currentStation.taskType").value("CHOICE"))
                .andExpect(jsonPath("$.currentStation.choices").value(contains("Věznice", "Pivovar", "Kasárna", "Škola")));
        answer(token, STATION_2, "Věznice").andExpect(jsonPath("$.correct").value(true));

        // Station 3 (TEXT): both hints, a third one is refused, letters separated by commas
        assertNoSecretsInState(token, STATION_3);
        hint(token, STATION_3).andExpect(status().isOk());
        hint(token, STATION_3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealedHints", hasSize(2)))
                .andExpect(jsonPath("$.hintsAvailable").value(0))
                .andExpect(jsonPath("$.nextHintPenaltySeconds").doesNotExist())
                .andExpect(jsonPath("$.penaltySeconds").value(240));
        hint(token, STATION_3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
        answer(token, STATION_3, "B, A, D, C").andExpect(jsonPath("$.correct").value(true));

        // Station 4 (TEXT): a listening task with a picture
        state(token)
                .andExpect(jsonPath("$.currentStation.listenOnly").value(true))
                .andExpect(jsonPath("$.currentStation.imageUrl").value("/images/demo/st04.webp"))
                .andExpect(jsonPath("$.currentStation.imageAlt").isString())
                .andExpect(jsonPath("$.currentStation.storyAudioUrl").value("/audio/demo/st04-pribeh.mp3"));
        answer(token, STATION_4, "Obraz Spasitele!").andExpect(jsonPath("$.correct").value(true));

        // Stations 5–7 (CHOICE)
        for (int i = 0; i < MIDDLE_ANSWERS.size(); i++) {
            answer(token, station(5 + i), MIDDLE_ANSWERS.get(i)).andExpect(jsonPath("$.correct").value(true));
        }

        // Stations 8 and 9 (NUMBER): a number in words is refused, a unit is ignored
        answer(token, station(8), "třináct")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value(SessionService.NOT_A_NUMBER_MESSAGE));
        answer(token, station(8), "13 let").andExpect(jsonPath("$.correct").value(true));
        answer(token, station(9), "386 krejcarů").andExpect(jsonPath("$.correct").value(true));

        // Station 10 (TEXT): the last one has no direction where to go next
        assertNoSecretsInState(token, STATION_10);
        answer(token, STATION_10, "Povinnost!")
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.nextText").doesNotExist())
                .andExpect(jsonPath("$.nextStationId").doesNotExist())
                .andExpect(jsonPath("$.gameCompleted").value(true));

        state(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.teamName").value("Kuchtovi"))
                .andExpect(jsonPath("$.penaltySeconds").value(240))
                .andExpect(jsonPath("$.progress.solved").value(10))
                .andExpect(jsonPath("$.progress.total").value(10))
                .andExpect(jsonPath("$.currentStation").isEmpty())
                .andExpect(jsonPath("$.solvedStations[*].orderNo").value(contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
                .andExpect(jsonPath("$.solvedStations[0].nextText").value(startsWith("Vystoupej")))
                .andExpect(jsonPath("$.finale.outroText").value(startsWith("„Povinnost.")))
                .andExpect(jsonPath("$.finale.outroAudioUrl").value("/audio/demo/zaver.mp3"))
                .andExpect(jsonPath("$.finale.hintsUsed").value(3))
                .andExpect(jsonPath("$.finale.totalSeconds").value(greaterThanOrEqualTo(240)))
                .andExpect(jsonPath("$.token").doesNotExist());

        // All letters of the puzzle are collected, scrambled
        String finished = state(token).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> letters = JsonPath.read(finished, "$.puzzle.slots");
        assertThat(String.join("", letters)).isNotEqualTo("POVINNOST");
        assertThat(letters.stream().sorted().toList())
                .isEqualTo("POVINNOST".chars().mapToObj(Character::toString).sorted().toList());

        // Every attempt is logged, progress counters match
        UUID sessionId = sessionId(token);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ?", Integer.class,
                sessionId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ? AND correct",
                Integer.class, sessionId)).isEqualTo(10);
        Map<String, Object> first = jdbc.queryForMap(
                "SELECT attempts_count, hints_used, status, solved_at FROM session_progress"
                        + " WHERE session_id = ? AND station_id = ?::uuid", sessionId, STATION_1);
        assertThat(first).containsEntry("attempts_count", 2).containsEntry("hints_used", 1)
                .containsEntry("status", "SOLVED");
        assertThat(first.get("solved_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT normalized_answer FROM answer_attempt"
                        + " WHERE session_id = ? AND raw_answer = '  OHYB ŘEKY jako ohnutá paže '", String.class,
                sessionId)).isEqualTo("ohyb reky jako ohnuta paze");
        assertThat(jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM game_session WHERE id = ?",
                Boolean.class, sessionId)).isTrue();

        // A completed game cannot be played any more
        answer(token, STATION_10, "povinnost")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
    }

    @Test
    void startReturnsTokenAndFirstStation() throws Exception {
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\",\"teamName\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/current"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.teamName").isEmpty())
                .andExpect(jsonPath("$.game.id").value(DEMO_GAME_ID))
                .andExpect(jsonPath("$.progress.solved").value(0))
                .andExpect(jsonPath("$.progress.total").value(10))
                .andExpect(jsonPath("$.currentStation.id").value(STATION_1))
                .andExpect(jsonPath("$.currentStation.choices", hasSize(4)))
                .andExpect(jsonPath("$.currentStation.listenOnly").value(false))
                .andExpect(jsonPath("$.currentStation.imageUrl").value("/images/demo/st01.webp"))
                .andExpect(jsonPath("$.currentStation.hintsAvailable").value(1))
                .andExpect(jsonPath("$.currentStation.nextHintPenaltySeconds").value(60))
                .andExpect(jsonPath("$.currentStation.revealedHints").isEmpty())
                .andExpect(jsonPath("$.solvedStations").isEmpty())
                .andExpect(jsonPath("$.finale").isEmpty());
    }

    @Test
    void lockedOrUnknownStationCannotBeAnswered() throws Exception {
        String token = startGame(null);

        answer(token, STATION_2, "Věznice")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
        hint(token, STATION_3).andExpect(status().isConflict());
        answer(token, UUID.randomUUID().toString(), "13")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/not-found"));

        // A refused answer is not logged as an attempt
        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_attempt WHERE session_id = ?", Integer.class,
                sessionId(token))).isZero();
    }

    @Test
    void solvedStationCannotBeAnsweredAgain() throws Exception {
        String token = startGame(null);
        answer(token, STATION_1, STATION_1_ANSWER).andExpect(jsonPath("$.correct").value(true));

        answer(token, STATION_1, STATION_1_ANSWER).andExpect(status().isConflict());
    }

    @Test
    void missingMalformedOrUnknownTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/invalid-token"));
        mvc.perform(get("/api/v1/sessions/current").header(SessionController.TOKEN_HEADER, "abc"))
                .andExpect(status().isUnauthorized());
        state(UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        answer(UUID.randomUUID().toString(), STATION_1, STATION_1_ANSWER).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionIsGoneAndMarkedExpired() throws Exception {
        String token = startGame(null);
        jdbc.update("UPDATE game_session SET expires_at = now() - interval '1 minute' WHERE token = ?::uuid", token);

        state(token)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("/problems/session-expired"));
        answer(token, STATION_1, STATION_1_ANSWER).andExpect(status().isGone());

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
        answer(token, STATION_1, STATION_1_ANSWER).andExpect(status().isConflict());
        hint(token, STATION_1).andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsAreValidationProblems() throws Exception {
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\",\"teamName\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"XXXX-XXXX\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/invalid-code"));

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

        // Checked only within the title and task of the current station: the story of a comprehension task
        // (e.g. the dialogue in the cell) and rewards of solved stations may legitimately contain the answer.
        // CHOICE offers the answer as a choice.
        Map<String, Object> current = JsonPath.read(body, "$.currentStation");
        if (!"CHOICE".equals(current.get("taskType"))) {
            List<String> answers = jdbc.queryForList(
                    "SELECT value FROM accepted_answer WHERE station_id = ?::uuid", String.class, stationId);
            String visibleText = String.join(" ", (String) current.get("title"), (String) current.get("taskText"));
            assertThat(answers).allSatisfy(answer -> assertThat(visibleText).doesNotContainIgnoringCase(answer));
        }
    }

    private String startGame(String teamName) throws Exception {
        String team = teamName == null ? "null" : "\"" + teamName + "\"";
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\",\"teamName\":" + team + "}"))
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

    static String station(int orderNo) {
        return "00000000-0000-0000-0001-%012d".formatted(orderNo);
    }
}
