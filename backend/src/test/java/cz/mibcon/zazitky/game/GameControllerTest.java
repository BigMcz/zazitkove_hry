package cz.mibcon.zazitky.game;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GameControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsPublishedGames() throws Exception {
        mvc.perform(get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(DemoGameSeedTest.DEMO_GAME_ID.toString())))
                .andExpect(jsonPath("$[0].title").value("Tajemství katova klíče"))
                .andExpect(jsonPath("$[0].subtitle").value("Demo hra — vyzkoušej si, jak to funguje"))
                .andExpect(jsonPath("$[0].estimatedMinutes").value(20));
    }

    @Test
    void returnsGameDetailWithoutStations() throws Exception {
        mvc.perform(get("/api/v1/games/{id}", DemoGameSeedTest.DEMO_GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DemoGameSeedTest.DEMO_GAME_ID.toString()))
                .andExpect(jsonPath("$.title").value("Tajemství katova klíče"))
                .andExpect(jsonPath("$.introText").value(startsWith("Píše se rok 1621.")))
                .andExpect(jsonPath("$.stationCount").value(5))
                .andExpect(jsonPath("$.stations").doesNotExist())
                .andExpect(jsonPath("$.outroText").doesNotExist());
    }

    @Test
    void unknownGameIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/v1/games/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Hra nebyla nalezena."));
    }

    @Test
    void invalidGameIdIsValidationProblem() throws Exception {
        mvc.perform(get("/api/v1/games/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/validation"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownApiPathIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/not-found"));
    }
}
