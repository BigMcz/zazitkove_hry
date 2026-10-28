package cz.mibcon.zazitky.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SpaFallbackTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/play", "/play/reward", "/play/finale",
            "/games/00000000-0000-0000-0000-000000000003"})
    void clientRoutesServeIndexHtml(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(null))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("spa-test-index")))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    /** Spring Boot's welcome page mapping forwards "/" to index.html, which the handler above then serves. */
    @Test
    void rootForwardsToIndexHtml() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void missingFileIsNotFound() throws Exception {
        mvc.perform(get("/assets/missing-abc123.js")).andExpect(status().isNotFound());
        mvc.perform(get("/missing.png")).andExpect(status().isNotFound());
    }

    @Test
    void unknownApiPathIsStillProblemJson() throws Exception {
        mvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/not-found"));
    }

    @Test
    void apiStillWorks() throws Exception {
        mvc.perform(get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
