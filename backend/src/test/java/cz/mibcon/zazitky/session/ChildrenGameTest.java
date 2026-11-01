package cz.mibcon.zazitky.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The children demo game (for_children): its own wrong answer message and the puzzle letters in order. */
@IntegrationTest
class ChildrenGameTest {

    static final String STATION_1 = "00000000-0000-0001-0001-000000000001";
    static final String STATION_2 = "00000000-0000-0001-0001-000000000002";

    @Autowired
    MockMvc mvc;

    @Test
    void speaksToChildrenAndKeepsTheLettersInOrder() throws Exception {
        String token = startGame();
        state(token)
                .andExpect(jsonPath("$.game.forChildren").value(true))
                .andExpect(jsonPath("$.puzzle.slots.length()").value(6));

        answer(token, STATION_1, "Hrad má tvar boty")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.message").value("Krá, to ne! Zkus to znovu."));
        hint(token, STATION_1).andExpect(jsonPath("$.penaltySeconds").value(0));
        answer(token, STATION_1, "Řeka se ohýbá jako ruka").andExpect(jsonPath("$.correct").value(true));
        answer(token, STATION_2, "stříbrný").andExpect(jsonPath("$.correct").value(true));

        state(token)
                .andExpect(jsonPath("$.puzzle.slots[0]").value("P"))
                .andExpect(jsonPath("$.puzzle.slots[1]").value("O"))
                .andExpect(jsonPath("$.puzzle.slots[2]").isEmpty())
                .andExpect(jsonPath("$.puzzle.latestSlot").value(1));

        // The phone plays the same way without a signal
        mvc.perform(get("/api/v1/sessions/current/package").header(SessionController.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.forChildren").value(true))
                .andExpect(jsonPath("$.session.wrongAnswerText").value("Krá, to ne! Zkus to znovu."))
                .andExpect(jsonPath("$.puzzleSlots['" + STATION_1 + "']").value(0))
                .andExpect(jsonPath("$.puzzleSlots['" + STATION_2 + "']").value(1));
    }

    @Test
    void theAdultGameStaysAsItWas() throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\",\"teamName\":null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = JsonPath.read(body, "$.token");

        state(token).andExpect(jsonPath("$.game.forChildren").value(false));
        answer(token, SessionApiTest.STATION_1, "Tvar hradní věže")
                .andExpect(jsonPath("$.message").value(SessionService.WRONG_ANSWER_MESSAGE));
        mvc.perform(get("/api/v1/sessions/current/package").header(SessionController.TOKEN_HEADER, token))
                .andExpect(jsonPath("$.session.forChildren").value(false))
                .andExpect(jsonPath("$.session.wrongAnswerText").isEmpty());
    }

    private String startGame() throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEMO-DETI\",\"teamName\":\"Kryštofovi\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    private ResultActions state(String token) throws Exception {
        return mvc.perform(get("/api/v1/sessions/current").header(SessionController.TOKEN_HEADER, token))
                .andExpect(status().isOk());
    }

    private ResultActions answer(String token, String stationId, String answer) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/stations/{id}/answers", stationId)
                        .header(SessionController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"answer\":\"" + answer + "\"}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    private ResultActions hint(String token, String stationId) throws Exception {
        return mvc.perform(post("/api/v1/sessions/current/stations/{id}/hints", stationId)
                        .header(SessionController.TOKEN_HEADER, token))
                .andExpect(status().isOk());
    }
}
