package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.accesscode.AccessCodeAdminService;
import cz.mibcon.zazitky.accesscode.AccessCodeFormat;
import cz.mibcon.zazitky.accesscode.AdminBatchDtos.CreateBatchRequest;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.game.GameAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The German version of the demo game: one card, the language is chosen before the start. */
@IntegrationTest
class LanguageVersionTest {

    static final UUID MAIN_GAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID GERMAN_GAME_ID = UUID.fromString("00000000-0000-0002-0000-000000000003");
    static final String GERMAN_STATION_1 = "00000000-0000-0002-0001-000000000001";
    static final String GERMAN_STATION_8 = "00000000-0000-0002-0001-000000000008";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccessCodeAdminService adminService;

    @Autowired
    GameAdminController gameAdmin;

    @Test
    void codeOffersTheGameInOtherLanguages() throws Exception {
        check(newCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.title").value("Katova povinnost"))
                .andExpect(jsonPath("$.game.language").value("cs"))
                .andExpect(jsonPath("$.languageVersions.length()").value(1))
                .andExpect(jsonPath("$.languageVersions[0].id").value(GERMAN_GAME_ID.toString()))
                .andExpect(jsonPath("$.languageVersions[0].title").value("Die Pflicht des Henkers"))
                .andExpect(jsonPath("$.languageVersions[0].language").value("de"))
                .andExpect(jsonPath("$.languageVersions[0].stationCount").value(10));
    }

    @Test
    void playsInTheChosenLanguageAndContinuesInItOnAnotherDevice() throws Exception {
        String code = newCode();
        String token = token(start(code, "de").andExpect(status().isCreated())
                .andExpect(jsonPath("$.game.id").value(GERMAN_GAME_ID.toString()))
                .andExpect(jsonPath("$.game.title").value("Die Pflicht des Henkers"))
                .andExpect(jsonPath("$.game.language").value("de"))
                .andExpect(jsonPath("$.currentStation.id").value(GERMAN_STATION_1))
                .andExpect(jsonPath("$.currentStation.title").value("Aussicht über die Eger")));

        // The code and its sale belong to the main game, the session to the version played
        assertThat(jdbc.queryForObject("SELECT game_id FROM game_session WHERE token = ?::uuid", UUID.class, token))
                .isEqualTo(GERMAN_GAME_ID);
        assertThat(jdbc.queryForObject("SELECT b.game_id FROM access_code c JOIN access_code_batch b ON b.id = c.batch_id"
                + " WHERE c.code = ?", UUID.class, AccessCodeFormat.normalize(code))).isEqualTo(MAIN_GAME_ID);

        answer(token, GERMAN_STATION_1, "Ein altes Längenmaß")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value("Das ist es nicht. Versuch es noch einmal."));
        answer(token, GERMAN_STATION_1, "Die Flussbiegung wie ein angewinkelter Arm")
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.rewardAudioUrl").value("/audio/demo-de/st01-odmena.mp3"));
        mvc.perform(get("/api/v1/sessions/current/package").header(SessionController.TOKEN_HEADER, token))
                .andExpect(jsonPath("$.session.language").value("de"))
                .andExpect(jsonPath("$.session.gameTitle").value("Die Pflicht des Henkers"));

        // Entering the code again continues the German game, whatever language is sent
        start(code, "cs").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.game.language").value("de"));
    }

    @Test
    void mainLanguageOrNoneStartsTheMainGame() throws Exception {
        start(newCode(), null).andExpect(status().isCreated()).andExpect(jsonPath("$.game.language").value("cs"));
        start(newCode(), "cs").andExpect(status().isCreated())
                .andExpect(jsonPath("$.game.id").value(MAIN_GAME_ID.toString()));
    }

    @Test
    void numberAnswerWithoutDigitsIsExplainedInGerman() throws Exception {
        String token = token(start(newCode(), "de"));
        jdbc.update("UPDATE session_progress SET station_id = ?::uuid WHERE session_id ="
                + " (SELECT id FROM game_session WHERE token = ?::uuid)", GERMAN_STATION_8, token);

        answer(token, GERMAN_STATION_8, "dreizehn")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value("Die Antwort muss eine Zahl enthalten."));
    }

    @Test
    void unknownLanguageIsRejected() throws Exception {
        String code = newCode();
        start(code, "fr").andExpect(status().isBadRequest());
        start(code, "deutsch").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM access_code WHERE code = ?", String.class,
                AccessCodeFormat.normalize(code))).as("a failed start does not sell the code").isEqualTo("NEW");
    }

    @Test
    void codesAreMadeOnlyForMainGames() {
        assertThat(gameAdmin.games()).extracting(GameAdminController.AdminGameDto::id)
                .contains(MAIN_GAME_ID)
                .doesNotContain(GERMAN_GAME_ID);
        assertThatThrownBy(() -> adminService.createBatch(new CreateBatchRequest(GERMAN_GAME_ID, 1, 5, null)))
                .isInstanceOf(BadRequestException.class);
    }

    private String newCode() {
        UUID batchId = adminService.createBatch(new CreateBatchRequest(MAIN_GAME_ID, 1, 5, "test")).id();
        adminService.handOver(batchId);
        return AccessCodeFormat.format(
                jdbc.queryForObject("SELECT code FROM access_code WHERE batch_id = ?", String.class, batchId));
    }

    private ResultActions check(String code) throws Exception {
        return mvc.perform(post("/api/v1/access-codes/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions start(String code, String language) throws Exception {
        String lang = language == null ? "null" : "\"" + language + "\"";
        return mvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"teamName\":null,\"language\":" + lang + "}"));
    }

    private ResultActions answer(String token, String stationId, String answer) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/stations/{id}/answers", stationId)
                .header(SessionController.TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"answer\":\"" + answer + "\"}").getBytes(StandardCharsets.UTF_8)));
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }
}
