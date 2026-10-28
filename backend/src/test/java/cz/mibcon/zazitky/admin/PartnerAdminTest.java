package cz.mibcon.zazitky.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@IntegrationTest
class PartnerAdminTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void eachActivationIsSettledWithTheShareValidOnItsDay() throws Exception {
        String name = "Hrad " + UUID.randomUUID().toString().substring(0, 8);
        UUID partnerId = UUID.randomUUID();
        UUID gameId = createPartnerWithGame(partnerId, name);

        setShare(partnerId, "2026-01-01", "25").andExpect(status().isOk());
        setShare(partnerId, "2026-05-01", "30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares[*].validFrom").value(contains("2026-01-01", "2026-05-01")));

        UUID batch = createBatch(gameId, 4);
        List<UUID> codes = jdbc.queryForList("SELECT id FROM access_code WHERE batch_id = ? ORDER BY code", UUID.class, batch);
        activate(codes.get(0), "2026-03-15T10:00:00Z");
        activate(codes.get(1), "2026-04-30T21:30:00Z"); // 23:30 in Prague → still April
        activate(codes.get(2), "2026-04-30T22:30:00Z"); // 00:30 on 1 May in Prague → May
        activate(codes.get(3), "2026-05-20T10:00:00Z");

        String report = body(mvc.perform(get("/api/v1/admin/reports/monthly-activations").param("year", "2026")
                .with(admin())));
        assertThat(shareFor(report, name, "2026-03")).isEqualTo(50.0);  // 200 × 25 %
        assertThat(shareFor(report, name, "2026-04")).isEqualTo(50.0);  // 200 × 25 %
        assertThat(shareFor(report, name, "2026-05")).isEqualTo(120.0); // 2 × 200 × 30 %
        List<Number> mayCount = JsonPath.read(report,
                "$[?(@.partnerName == '" + name + "' && @.month == '2026-05')].activatedCount");
        assertThat(mayCount).singleElement().satisfies(count -> assertThat(count.intValue()).isEqualTo(2));
    }

    @Test
    void currentShareIsTheLatestNotInTheFuture() throws Exception {
        UUID partnerId = UUID.randomUUID();
        createPartnerWithGame(partnerId, "Hrad " + partnerId.toString().substring(0, 8));

        setShare(partnerId, "2020-01-01", "25");
        String partner = body(setShare(partnerId, "2099-01-01", "30"));

        assertThat(JsonPath.<Number>read(partner, "$.currentSharePct").doubleValue()).isEqualTo(25.0);
        assertThat(JsonPath.<List<Object>>read(partner, "$.shares")).hasSize(2);
    }

    @Test
    void sameDateReplacesTheShareAndAShareCanBeDeleted() throws Exception {
        UUID partnerId = UUID.randomUUID();
        createPartnerWithGame(partnerId, "Hrad " + partnerId.toString().substring(0, 8));

        setShare(partnerId, "2026-12-01", "30");
        String replaced = body(setShare(partnerId, "2026-12-01", "35"));
        List<Map<String, Object>> shares = JsonPath.read(replaced, "$.shares");
        assertThat(shares).singleElement().satisfies(share ->
                assertThat(((Number) share.get("sharePct")).doubleValue()).isEqualTo(35.0));

        String shareId = (String) shares.getFirst().get("id");
        mvc.perform(delete("/api/v1/admin/partners/{p}/shares/{s}", partnerId, shareId).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares").isEmpty())
                .andExpect(jsonPath("$.currentSharePct").isEmpty());
        mvc.perform(delete("/api/v1/admin/partners/{p}/shares/{s}", partnerId, shareId).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesNameAndContact() throws Exception {
        UUID partnerId = UUID.randomUUID();
        createPartnerWithGame(partnerId, "Starý název");

        updatePartner(partnerId, "{\"name\":\"Hrad Loket\",\"contactEmail\":\"pokladna@hrad.cz\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hrad Loket"))
                .andExpect(jsonPath("$.contactEmail").value("pokladna@hrad.cz"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        UUID partnerId = UUID.randomUUID();
        createPartnerWithGame(partnerId, "Hrad " + partnerId.toString().substring(0, 8));

        setShare(partnerId, "2026-12-01", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sharePct"));
        setShare(partnerId, "2026-12-01", "10.123").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/partners/{id}/shares", partnerId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sharePct\":10}"))
                .andExpect(status().isBadRequest());
        updatePartner(partnerId, "{\"name\":\"X\",\"contactEmail\":\"neni-email\"}").andExpect(status().isBadRequest());
        updatePartner(partnerId, "{\"name\":\" \"}").andExpect(status().isBadRequest());
        setShare(UUID.randomUUID(), "2026-12-01", "10").andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAdminChangesPartners() throws Exception {
        String demo = PartnerAccessTest.DEMO_PARTNER_ID;
        mvc.perform(post("/api/v1/admin/partners/{id}/shares", demo)
                        .with(user("hrad").roles(PartnerUserDetails.ROLE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"validFrom\":\"2026-12-01\",\"sharePct\":99}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/partners/{id}/shares", demo).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"validFrom\":\"2026-12-01\",\"sharePct\":99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void castleSeesItsOwnShares() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/admin/login")
                        .param("username", PartnerAccessTest.DEV_PARTNER_USER)
                        .param("password", PartnerAccessTest.DEV_PARTNER_PASSWORD).with(csrf()))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/v1/partner/shares").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validFrom").value("2026-01-01"))
                .andExpect(jsonPath("$[0].sharePct").value(50.0));
    }

    private UUID createPartnerWithGame(UUID partnerId, String name) {
        UUID location = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        jdbc.update("INSERT INTO partner (id, name) VALUES (?, ?)", partnerId, name);
        jdbc.update("INSERT INTO location (id, partner_id, name) VALUES (?, ?, 'Hrad')", location, partnerId);
        jdbc.update("INSERT INTO game (id, location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, ?, 'Hra', 'Intro', 'Outro', 'PUBLISHED')", game, location, "hra-" + game);
        return game;
    }

    private UUID createBatch(UUID gameId, int quantity) throws Exception {
        String body = body(mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + gameId + "\",\"quantity\":" + quantity
                                + ",\"validityHours\":5,\"unitPriceCzk\":200}"))
                .andExpect(status().isCreated()));
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private void activate(UUID codeId, String instant) {
        jdbc.update("UPDATE access_code SET status = 'ACTIVATED', activated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(instant)), codeId);
    }

    private static double shareFor(String report, String partnerName, String month) {
        List<Number> values = JsonPath.read(report,
                "$[?(@.partnerName == '" + partnerName + "' && @.month == '" + month + "')].partnerShareCzk");
        assertThat(values).as("report row %s %s", partnerName, month).hasSize(1);
        return values.getFirst().doubleValue();
    }

    private ResultActions setShare(UUID partnerId, String validFrom, String sharePct) throws Exception {
        return mvc.perform(post("/api/v1/admin/partners/{id}/shares", partnerId).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validFrom\":\"" + validFrom + "\",\"sharePct\":" + sharePct + "}"));
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
