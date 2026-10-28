package cz.mibcon.zazitky.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class PartnerAccessTest {

    static final String DEMO_PARTNER_ID = "00000000-0000-0000-0000-000000000001";
    /** Development account from the seed. */
    static final String DEV_PARTNER_USER = "hrad";
    static final String DEV_PARTNER_PASSWORD = "hrad-loket";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void partnerSeesOnlyTheDataOfItsOwnCastle() throws Exception {
        String otherPartner = createOtherPartnerWithActivatedCode();
        int year = Year.now(ZoneId.of("Europe/Prague")).getValue();

        MockHttpSession session = session(login(DEV_PARTNER_USER, DEV_PARTNER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARTNER"))
                .andExpect(jsonPath("$.partnerName").value("Demo partner")));
        mvc.perform(get("/api/v1/admin/session").session(session)).andExpect(jsonPath("$.role").value("PARTNER"));

        String report = body(mvc.perform(get("/api/v1/partner/reports/monthly-activations")
                .param("year", String.valueOf(year)).session(session)).andExpect(status().isOk()));
        List<String> reportPartners = JsonPath.read(report, "$[*].partnerName");
        assertThat(reportPartners).doesNotContain(otherPartner).allMatch("Demo partner"::equals);

        String batches = body(mvc.perform(get("/api/v1/partner/batches").session(session)).andExpect(status().isOk()));
        List<String> batchPartners = JsonPath.read(batches, "$[*].partnerName");
        assertThat(batchPartners).allMatch("Demo partner"::equals);
        List<Boolean> development = JsonPath.read(batches, "$[*].multiSession");
        assertThat(development).as("development code DEMO-KATA is not shown to the castle").doesNotContain(true);

        // The admin sees both
        String adminReport = body(mvc.perform(get("/api/v1/admin/reports/monthly-activations")
                .param("year", String.valueOf(year)).with(admin())));
        List<String> allPartners = JsonPath.read(adminReport, "$[*].partnerName");
        assertThat(allPartners).contains(otherPartner);
    }

    @Test
    void partnerCannotUseTheAdministration() throws Exception {
        MockHttpSession session = session(login(DEV_PARTNER_USER, DEV_PARTNER_PASSWORD));

        mvc.perform(get("/api/v1/admin/batches").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/forbidden"));
        mvc.perform(get("/api/v1/admin/batches/{id}/codes.csv", UUID.randomUUID()).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/partner-users").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/batches").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerApiRequiresPartnerLogin() throws Exception {
        mvc.perform(get("/api/v1/partner/batches"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/partner/batches").with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesPartnerAccountThatCanLogIn() throws Exception {
        String username = "Pokladna-" + UUID.randomUUID().toString().substring(0, 8);
        createUser(username, "Silne-heslo-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username.toLowerCase()))
                .andExpect(jsonPath("$.partnerName").value("Demo partner"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.lastLoginAt").isEmpty());

        login(username.toUpperCase(), "Silne-heslo-1").andExpect(status().isOk());

        String users = body(mvc.perform(get("/api/v1/admin/partner-users").with(admin())));
        List<Object> lastLogin = JsonPath.read(users, "$[?(@.username == '" + username.toLowerCase() + "')].lastLoginAt");
        assertThat(lastLogin).singleElement().isNotNull();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM partner_user WHERE username = ?", String.class,
                username.toLowerCase())).startsWith("{bcrypt}");
    }

    @Test
    void rejectsInvalidOrDuplicateAccounts() throws Exception {
        createUser(DEV_PARTNER_USER, "Silne-heslo-1").andExpect(status().isBadRequest());
        createUser("ADMIN", "Silne-heslo-1").andExpect(status().isBadRequest());
        createUser("kratke-heslo", "123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        createUser("s mezerou", "Silne-heslo-1").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/partner-users").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(userJson("bez-csrf", "Silne-heslo-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        String username = "zablokovany-" + UUID.randomUUID().toString().substring(0, 8);
        String id = JsonPath.read(body(createUser(username, "Silne-heslo-1")), "$.id");

        setEnabled(id, false);
        login(username, "Silne-heslo-1").andExpect(status().isUnauthorized());

        setEnabled(id, true);
        login(username, "Silne-heslo-1").andExpect(status().isOk());
    }

    @Test
    void passwordCanBeResetByAdminAndChangedByPartner() throws Exception {
        String username = "heslo-" + UUID.randomUUID().toString().substring(0, 8);
        String id = JsonPath.read(body(createUser(username, "Puvodni-heslo-1")), "$.id");

        mvc.perform(post("/api/v1/admin/partner-users/{id}/password", id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Nove-od-admina-1\"}"))
                .andExpect(status().isNoContent());
        login(username, "Puvodni-heslo-1").andExpect(status().isUnauthorized());
        MockHttpSession session = session(login(username, "Nove-od-admina-1").andExpect(status().isOk()));

        changeOwnPassword(session, "spatne-soucasne", "Vlastni-heslo-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Současné heslo nesouhlasí."));
        changeOwnPassword(session, "Nove-od-admina-1", "Vlastni-heslo-1").andExpect(status().isNoContent());
        login(username, "Vlastni-heslo-1").andExpect(status().isOk());
    }

    private String createOtherPartnerWithActivatedCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String name = "Jiný hrad " + suffix;
        UUID partner = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        jdbc.update("INSERT INTO partner (id, name, revenue_share_pct) VALUES (?, ?, 40)", partner, name);
        jdbc.update("INSERT INTO location (id, partner_id, name) VALUES (?, ?, 'Hrad')", location, partner);
        jdbc.update("INSERT INTO game (id, location_id, slug, title, intro_text, outro_text, status)"
                + " VALUES (?, ?, ?, 'Jiná hra', 'Intro', 'Outro', 'PUBLISHED')", game, location, "jina-hra-" + suffix);
        jdbc.update("INSERT INTO access_code_batch (id, partner_id, game_id, quantity, unit_price_czk)"
                + " VALUES (?, ?, ?, 1, 100)", batch, partner, game);
        jdbc.update("INSERT INTO access_code (batch_id, code, status, activated_at) VALUES (?, ?, 'ACTIVATED', now())",
                batch, ("T" + suffix.toUpperCase()).substring(0, 8));
        return name;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/admin/login").param("username", username).param("password", password)
                .with(csrf()));
    }

    private static MockHttpSession session(ResultActions login) {
        return (MockHttpSession) login.andReturn().getRequest().getSession();
    }

    private ResultActions createUser(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/admin/partner-users").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(userJson(username, password)));
    }

    private static String userJson(String username, String password) {
        return "{\"partnerId\":\"" + DEMO_PARTNER_ID + "\",\"username\":\"" + username + "\","
                + "\"displayName\":\"Pokladna\",\"password\":\"" + password + "\"}";
    }

    private void setEnabled(String id, boolean enabled) throws Exception {
        mvc.perform(post("/api/v1/admin/partner-users/{id}/enabled", id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions changeOwnPassword(MockHttpSession session, String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/partner/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
