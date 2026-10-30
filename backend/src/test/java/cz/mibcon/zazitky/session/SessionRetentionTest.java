package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SessionRetentionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionRetention retention;

    @Test
    void removesTeamNameOnlyAfterTheRetentionPeriod() throws Exception {
        String old = startGame("Starý tým");
        String recent = startGame("Nedávný tým");
        expire(old, SessionRetention.RETENTION.plusDays(1));
        expire(recent, SessionRetention.RETENTION.minusDays(1));

        retention.clearOldTeamNames();

        assertThat(teamName(old)).isNull();
        assertThat(teamName(recent)).isEqualTo("Nedávný tým");
        // The game itself stays for statistics
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_session WHERE token = ?::uuid", Integer.class, old))
                .isEqualTo(1);
    }

    private void expire(String token, Duration ago) {
        jdbc.update("UPDATE game_session SET expires_at = ? WHERE token = ?::uuid",
                Timestamp.from(Instant.now().minus(ago)), token);
    }

    private String teamName(String token) {
        return jdbc.queryForObject("SELECT team_name FROM game_session WHERE token = ?::uuid", String.class, token);
    }

    private String startGame(String teamName) throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\",\"teamName\":\"" + teamName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }
}
