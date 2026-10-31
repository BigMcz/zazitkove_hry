package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.common.TextNormalizer;
import cz.mibcon.zazitky.session.SessionPackage.SealedStation;
import cz.mibcon.zazitky.session.SessionPackage.Unlocked;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class SessionPackageTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void packageOpensTheNextStationOnlyWithACorrectAnswer() throws Exception {
        String token = startGame();
        String body = packageOf(token);
        SessionPackage pack = json.readValue(body, SessionPackage.class);

        assertThat(pack.firstStation().id().toString()).isEqualTo(SessionApiTest.STATION_1);
        assertThat(pack.session().gameTitle()).isEqualTo("Katova povinnost");
        assertThat(pack.session().expiresAt()).isAfter(pack.session().startedAt());
        assertThat(pack.stations()).hasSize(10);
        assertThat(pack.unlockedKeys()).isEmpty();
        assertThat(pack.media()).contains("/images/demo/st01.jpg", "/audio/demo/st01-odmena.mp3");
        // No accepted answer (except CHOICE, where it is one of the visible choices), reward or later station
        List<String> secrets = jdbc.queryForList("""
                SELECT a.value FROM accepted_answer a JOIN station s ON s.id = a.station_id
                WHERE s.task_type <> 'CHOICE'""", String.class);
        // Short numbers like "13" also occur in base64, so those are checked as whole JSON values. The answer
        // of the final puzzle ("povinnost") is in the title of the demo game, which the player sees anyway.
        secrets = secrets.stream().filter(answer -> !"Katova povinnost".contains(answer)).toList();
        assertThat(secrets).isNotEmpty().allSatisfy(answer -> assertThat(body)
                .doesNotContain(answer.length() < 4 ? "\"" + answer + "\"" : answer));
        assertThat(body).doesNotContain("Správně.").doesNotContain("Vystoupej k hradní bráně.");

        SealedStation first = pack.stations().getFirst();
        Unlocked unlocked = unlock(first, SessionApiTest.STATION_1_ANSWER, pack.kdfIterations());
        assertThat(unlocked.rewardText()).startsWith("Správně.");
        assertThat(unlocked.next().id().toString()).isEqualTo(SessionApiTest.STATION_2);
        assertThat(unlocked.next().hints()).isNotEmpty();
        assertThatThrownBy(() -> unlock(first, "Tvar hradní věže", pack.kdfIterations()))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void lastStationRevealsTheFinale() throws Exception {
        SessionPackage pack = json.readValue(packageOf(startGame()), SessionPackage.class);
        SealedStation last = pack.stations().getLast();
        String answer = jdbc.queryForObject("SELECT value FROM accepted_answer WHERE station_id = ?::uuid LIMIT 1",
                String.class, last.id().toString());
        String taskType = jdbc.queryForObject("SELECT task_type FROM station WHERE id = ?::uuid", String.class,
                last.id().toString());

        Unlocked unlocked = unlock(last, answer, pack.kdfIterations(), "NUMBER".equals(taskType));

        assertThat(unlocked.next()).isNull();
        assertThat(unlocked.finale().outroText()).isNotBlank();
    }

    @Test
    void solvedStationsComeWithTheirKeys() throws Exception {
        String token = startGame();
        mvc.perform(post("/api/v1/sessions/current/stations/{id}/answers", SessionApiTest.STATION_1)
                        .header(SessionController.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SessionDtos.SubmitAnswerRequest(
                                SessionApiTest.STATION_1_ANSWER))))
                .andExpect(status().isOk());

        SessionPackage pack = json.readValue(packageOf(token), SessionPackage.class);
        String key = pack.unlockedKeys().get(UUID.fromString(SessionApiTest.STATION_1));

        byte[] content = PackageCrypto.open(PackageCrypto.contentKey(PackageCrypto.decode(key)),
                pack.stations().getFirst().content());
        assertThat(json.readValue(content, Unlocked.class).next().id().toString()).isEqualTo(SessionApiTest.STATION_2);
        assertThat(pack.puzzleSlots()).hasSize(9);
    }

    @Test
    void needsAValidToken() throws Exception {
        mvc.perform(get("/api/v1/sessions/current/package")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sessions/current/package").header(SessionController.TOKEN_HEADER, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/problems/invalid-token"));
    }

    private Unlocked unlock(SealedStation station, String answer, int iterations) throws GeneralSecurityException {
        return unlock(station, answer, iterations, false);
    }

    private Unlocked unlock(SealedStation station, String answer, int iterations, boolean number)
            throws GeneralSecurityException {
        String normalized = number ? TextNormalizer.normalizeNumber(answer) : TextNormalizer.normalize(answer);
        SecretKey answerKey = PackageCrypto.answerKey(normalized, PackageCrypto.decode(station.salt()), iterations);
        GeneralSecurityException failure = null;
        for (SessionPackage.Sealed wrapped : station.keys()) {
            try {
                byte[] rawKey = PackageCrypto.open(answerKey, wrapped);
                byte[] content = PackageCrypto.open(PackageCrypto.contentKey(rawKey), station.content());
                return json.readValue(content, Unlocked.class);
            } catch (GeneralSecurityException e) {
                failure = e;
            }
        }
        throw failure;
    }

    private String startGame() throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + IntegrationTest.DEV_CODE + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("token").asString();
    }

    private String packageOf(String token) throws Exception {
        return mvc.perform(get("/api/v1/sessions/current/package").header(SessionController.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
