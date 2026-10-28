package cz.mibcon.zazitky.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class PartnerAdminTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newShareAppliesOnlyToNewBatchesSoInvoicedMonthsDoNotChange() throws Exception {
        String name = "Hrad " + UUID.randomUUID().toString().substring(0, 8);
        UUID partnerId = UUID.randomUUID();
        UUID gameId = createPartnerWithGame(partnerId, name, "50.00");

        UUID oldBatch = createBatch(gameId);
        updatePartner(partnerId, "{\"name\":\"" + name + "\",\"contactEmail\":\"pokladna@hrad.cz\",\"revenueSharePct\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueSharePct").value(30))
                .andExpect(jsonPath("$.contactEmail").value("pokladna@hrad.cz"));
        UUID newBatch = createBatch(gameId);

        String batches = body(mvc.perform(get("/api/v1/admin/batches").with(admin())));
        assertThat(share(batches, oldBatch)).isEqualByComparingTo("50");
        assertThat(share(batches, newBatch)).isEqualByComparingTo("30");

        activateOneCode(oldBatch);
        activateOneCode(newBatch);
        String report = body(mvc.perform(get("/api/v1/admin/reports/monthly-activations")
                .param("year", String.valueOf(Year.now(ZoneId.of("Europe/Prague")).getValue())).with(admin())));
        List<Number> shares = JsonPath.read(report, "$[?(@.partnerName == '" + name + "')].partnerShareCzk");
        // 200 Kč × 50 % + 200 Kč × 30 %
        assertThat(shares).singleElement().satisfies(value -> assertThat(value.doubleValue()).isEqualTo(160.0));

        String partners = body(mvc.perform(get("/api/v1/admin/partners").with(admin())));
        List<Number> listed = JsonPath.read(partners, "$[?(@.id == '" + partnerId + "')].revenueSharePct");
        assertThat(listed).singleElement().satisfies(value -> assertThat(value.doubleValue()).isEqualTo(30.0));
    }

    @Test
    void rejectsInvalidPartnerSettings() throws Exception {
        UUID partnerId = UUID.randomUUID();
        createPartnerWithGame(partnerId, "Hrad " + partnerId.toString().substring(0, 8), "50.00");

        updatePartner(partnerId, "{\"name\":\"X\",\"revenueSharePct\":101}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("revenueSharePct"));
        updatePartner(partnerId, "{\"name\":\"X\",\"revenueSharePct\":10.123}").andExpect(status().isBadRequest());
        updatePartner(partnerId, "{\"name\":\"X\",\"contactEmail\":\"neni-email\",\"revenueSharePct\":10}")
                .andExpect(status().isBadRequest());
        updatePartner(partnerId, "{\"name\":\" \",\"revenueSharePct\":10}").andExpect(status().isBadRequest());
        updatePartner(UUID.randomUUID(), "{\"name\":\"X\",\"revenueSharePct\":10}").andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAdminChangesPartners() throws Exception {
        mvc.perform(put("/api/v1/admin/partners/{id}", PartnerAccessTest.DEMO_PARTNER_ID)
                        .with(user("hrad").roles(PartnerUserDetails.ROLE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"revenueSharePct\":99}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/partners/{id}", PartnerAccessTest.DEMO_PARTNER_ID).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"revenueSharePct\":99}"))
                .andExpect(status().isForbidden());
    }

    private UUID createPartnerWithGame(UUID partnerId, String name, String sharePct) {
        UUID location = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        jdbc.update("INSERT INTO partner (id, name, revenue_share_pct) VALUES (?, ?, ?)", partnerId, name,
                new BigDecimal(sharePct));
        jdbc.update("INSERT INTO location (id, partner_id, name) VALUES (?, ?, 'Hrad')", location, partnerId);
        jdbc.update("INSERT INTO game (id, location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, ?, 'Hra', 'Intro', 'Outro', 'PUBLISHED')", game, location, "hra-" + game);
        return game;
    }

    private UUID createBatch(UUID gameId) throws Exception {
        String body = body(mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + gameId + "\",\"quantity\":2,\"validityHours\":5,\"unitPriceCzk\":200}"))
                .andExpect(status().isCreated()));
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void activateOneCode(UUID batchId) {
        jdbc.update("UPDATE access_code SET status = 'ACTIVATED', activated_at = now()"
                + " WHERE id = (SELECT id FROM access_code WHERE batch_id = ? LIMIT 1)", batchId);
    }

    private static BigDecimal share(String batchesJson, UUID batchId) {
        List<Number> values = JsonPath.read(batchesJson, "$[?(@.id == '" + batchId + "')].revenueSharePct");
        return new BigDecimal(values.getFirst().toString());
    }

    private ResultActions updatePartner(UUID partnerId, String json) throws Exception {
        return mvc.perform(put("/api/v1/admin/partners/{id}", partnerId).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
