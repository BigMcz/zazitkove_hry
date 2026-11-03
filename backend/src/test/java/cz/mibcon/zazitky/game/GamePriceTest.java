package cz.mibcon.zazitky.game;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Selling prices of the games valid from a date: set by the admin, read by the castle. */
@IntegrationTest
class GamePriceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pricesOfThePartnersMainGamesWithTheCurrentOne() throws Exception {
        Castle castle = createCastle();

        setPrice(castle.gameId, "2020-01-01", 199).andExpect(status().isOk());
        setPrice(castle.gameId, "2099-01-01", 249)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPriceCzk").value(199))
                .andExpect(jsonPath("$.prices[*].validFrom").value(contains("2020-01-01", "2099-01-01")));
        // The same date replaces the price
        setPrice(castle.gameId, "2020-01-01", 229).andExpect(jsonPath("$.prices[*].priceCzk").value(contains(229, 249)));

        mvc.perform(get("/api/v1/admin/partners/{id}/game-prices", castle.partnerId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameId").value(castle.gameId.toString()))
                .andExpect(jsonPath("$[0].gameTitle").value("Hra"))
                .andExpect(jsonPath("$[0].currentPriceCzk").value(229))
                .andExpect(jsonPath("$[0].prices.length()").value(2));
    }

    @Test
    void deletesAPriceAndRejectsInvalidOnes() throws Exception {
        Castle castle = createCastle();
        String body = setPrice(castle.gameId, "2020-01-01", 199).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String priceId = JsonPath.read(body, "$.prices[0].id");

        mvc.perform(delete("/api/v1/admin/games/{gameId}/prices/{id}", castle.gameId, priceId).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPriceCzk").isEmpty())
                .andExpect(jsonPath("$.prices.length()").value(0));
        setPrice(castle.gameId, "2020-01-01", -1).andExpect(status().isBadRequest());
        setPrice(castle.versionId, "2020-01-01", 199).andExpect(status().isBadRequest());
    }

    @Test
    void castleSeesItsPricesButCannotChangeThem() throws Exception {
        Castle castle = createCastle();
        setPrice(castle.gameId, "2020-01-01", 199);
        RequestPostProcessor pokladna = user(castle.username).roles("PARTNER");

        mvc.perform(get("/api/v1/partner/game-prices").session(castleLogin(castle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPriceCzk").value(199));
        mvc.perform(post("/api/v1/admin/games/{id}/prices", castle.gameId).with(pokladna).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validFrom\":\"2020-01-01\",\"priceCzk\":1}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession castleLogin(Castle castle) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/admin/login").with(csrf())
                        .param("username", castle.username).param("password", "heslo-pokladny"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    private ResultActions setPrice(UUID gameId, String validFrom, int priceCzk) throws Exception {
        return mvc.perform(post("/api/v1/admin/games/{id}/prices", gameId).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validFrom\":\"" + validFrom + "\",\"priceCzk\":" + priceCzk + "}"));
    }

    private Castle createCastle() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID partner = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.update("INSERT INTO partner (id, name) VALUES (?, ?)", partner, "Hrad " + suffix);
        jdbc.update("INSERT INTO location (id, partner_id, name) VALUES (?, ?, 'Hrad')", location, partner);
        jdbc.update("INSERT INTO game (id, location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, ?, 'Hra', 'Intro', 'Outro', 'PUBLISHED')", game, location, "hra-" + suffix);
        jdbc.update("INSERT INTO game (id, location_id, main_game_id, slug, title, intro_text, outro_text, language)"
                + " VALUES (?, ?, ?, ?, 'Spiel', 'Intro', 'Outro', 'de')", version, location, game, "spiel-" + suffix);
        String username = "pokladna-" + suffix;
        jdbc.update("INSERT INTO partner_user (partner_id, username, password_hash) VALUES (?, ?, ?)", partner,
                username, "{noop}heslo-pokladny");
        return new Castle(partner, game, version, username);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private record Castle(UUID partnerId, UUID gameId, UUID versionId, String username) {
    }
}
