package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class AdminApiTest {

    static final String DEMO_GAME_ID = "00000000-0000-0000-0000-000000000003";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void adminApiRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/admin/batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/unauthorized"));
        mvc.perform(get("/api/v1/admin/games")).andExpect(status().isUnauthorized());
    }

    /*
     * The XSRF-TOKEN cookie itself is not asserted here: csrf() from spring-security-test replaces the
     * token repository of the shared CsrfFilter, so the result would depend on test order. The cookie
     * flow (session → login → POST) is verified in the browser.
     */
    @Test
    void sessionEndpointIsOpenForAnonymousUser() throws Exception {
        mvc.perform(get("/api/v1/admin/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    void loginWithPasswordOpensTheAdministration() throws Exception {
        mvc.perform(post("/api/v1/admin/login").param("username", "admin").param("password", "wrong").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/invalid-credentials"));
        mvc.perform(post("/api/v1/admin/login")
                        .param("username", "admin").param("password", IntegrationTest.ADMIN_PASSWORD))
                .andExpect(status().isForbidden());

        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/admin/login")
                        .param("username", "admin").param("password", IntegrationTest.ADMIN_PASSWORD).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/v1/admin/batches").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/session").session(session)).andExpect(jsonPath("$.username").value("admin"));

        mvc.perform(post("/api/v1/admin/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/batches").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsBatchOfUniqueNewCodesAndExportsThemForPrinting() throws Exception {
        String body = mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"quantity\":25,\"validityHours\":5,"
                                + "\"note\":\"Sezóna 2027 — tisk 1\",\"unitPriceCzk\":150}").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(25))
                .andExpect(jsonPath("$.partnerName").value("Demo partner"))
                .andExpect(jsonPath("$.activatedCount").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID batchId = UUID.fromString(JsonPath.read(body, "$.id"));

        List<String> codes = jdbc.queryForList("SELECT code FROM access_code WHERE batch_id = ? AND status = 'NEW'",
                String.class, batchId);
        assertThat(codes).hasSize(25).doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).matches("[A-HJKMNP-Z2-9]{8}"));

        mvc.perform(get("/api/v1/admin/batches").with(admin()))
                .andExpect(jsonPath("$[*].note").value(hasItem("Sezóna 2027 — tisk 1")));

        String csv = mvc.perform(get("/api/v1/admin/batches/{id}/codes.csv", batchId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();
        assertThat(lines.getFirst()).isEqualTo("﻿kod;odkaz");
        assertThat(lines).hasSize(26);
        String first = AccessCodeFormat.format(codes.stream().sorted().findFirst().orElseThrow());
        assertThat(lines.get(1)).isEqualTo(first + ";http://localhost/?kod=" + first);
    }

    @Test
    void changesRequireCsrfTokenAndValidInput() throws Exception {
        mvc.perform(post("/api/v1/admin/batches").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"quantity\":1,\"validityHours\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"quantity\":0,\"validityHours\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        mvc.perform(get("/api/v1/admin/batches/{id}/codes.csv", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsGamesForNewBatch() throws Exception {
        mvc.perform(get("/api/v1/admin/games").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + DEMO_GAME_ID + "')].partnerName").value(hasItem("Demo partner")));
    }

    @Test
    void monthlyReportCountsActivatedCodesWithoutDevelopmentCodes() throws Exception {
        String month = YearMonth.now(ZoneId.of("Europe/Prague")).toString();
        int year = Year.now(ZoneId.of("Europe/Prague")).getValue();
        long before = reportCount(year, month);

        String body = mvc.perform(post("/api/v1/admin/batches").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"" + DEMO_GAME_ID + "\",\"quantity\":3,\"validityHours\":5,\"unitPriceCzk\":200}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID batchId = UUID.fromString(JsonPath.read(body, "$.id"));
        List<String> codes = jdbc.queryForList("SELECT code FROM access_code WHERE batch_id = ?", String.class, batchId);
        for (String code : codes.subList(0, 2)) {
            mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code + "\"}")).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\"}")).andExpect(status().isCreated());

        assertThat(reportCount(year, month)).isEqualTo(before + 2);
        mvc.perform(get("/api/v1/admin/batches").with(admin()))
                .andExpect(jsonPath("$[?(@.id == '" + batchId + "')].activatedCount").value(hasItem(2)));
        mvc.perform(get("/api/v1/admin/reports/monthly-activations").param("year", "1999").with(admin()))
                .andExpect(status().isBadRequest());
    }

    private long reportCount(int year, String month) throws Exception {
        String json = mvc.perform(get("/api/v1/admin/reports/monthly-activations")
                        .param("year", String.valueOf(year)).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> counts = JsonPath.read(json,
                "$[?(@.month == '" + month + "' && @.partnerName == 'Demo partner')].activatedCount");
        return counts.stream().mapToLong(Number::longValue).sum();
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
