package cz.mibcon.zazitky.session;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** GET /admin/reports/gameplay: completion, duration and stations, on a game of its own partner. */
@IntegrationTest
class GameplayStatsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID partnerId;
    UUID gameId;
    UUID station1;
    UUID station2;
    /** Start of all games: two hours ago, so that everything happened in the past. */
    Instant t0;

    @BeforeEach
    void gameOfOwnPartner() {
        partnerId = insert("INSERT INTO partner (name) VALUES ('Statistický hrad') RETURNING id");
        UUID locationId = insert("INSERT INTO location (partner_id, name) VALUES (?, 'Hrad') RETURNING id", partnerId);
        gameId = insert("""
                INSERT INTO game (location_id, slug, title, intro_text, outro_text, status)
                VALUES (?, ?, 'Statistická hra', 'Úvod', 'Závěr', 'PUBLISHED') RETURNING id
                """, locationId, "stat-" + UUID.randomUUID());
        station1 = station(1);
        station2 = station(2);
        t0 = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void summarizesTheGamesOfRealPlayers() throws Exception {
        UUID sold = batch(false);
        UUID development = batch(true);

        UUID fast = session(sold, "COMPLETED", 30, 60, false);
        progress(fast, station1, 0, 10, 3, 1);
        progress(fast, station2, 10, 30, 1, 0);
        UUID slow = session(sold, "COMPLETED", 50, 0, false);
        progress(slow, station1, 0, 20, 1, 0);
        progress(slow, station2, 20, 50, 2, 0);
        UUID expired = session(sold, "EXPIRED", null, 60, false);
        progress(expired, station1, 0, 5, 1, 0);
        progress(expired, station2, 5, null, 4, 1);
        UUID playing = session(sold, "IN_PROGRESS", null, 0, true);
        progress(playing, station1, 0, null, 0, 0);
        UUID test = session(development, "COMPLETED", 1, 0, false);
        progress(test, station1, 0, 1, 1, 0);

        mvc.perform(get("/api/v1/admin/reports/gameplay").with(admin())
                        .param("year", String.valueOf(t0.atZone(ZoneId.of("Europe/Prague")).getYear()))
                        .param("partnerId", partnerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameTitle").value("Statistická hra"))
                .andExpect(jsonPath("$[0].partnerName").value("Statistický hrad"))
                .andExpect(jsonPath("$[0].started").value(4))
                .andExpect(jsonPath("$[0].completed").value(2))
                .andExpect(jsonPath("$[0].unfinished").value(1))
                .andExpect(jsonPath("$[0].playing").value(1))
                .andExpect(jsonPath("$[0].durationP25Seconds").value(2100))
                .andExpect(jsonPath("$[0].durationMedianSeconds").value(2400))
                .andExpect(jsonPath("$[0].durationP75Seconds").value(2700))
                .andExpect(jsonPath("$[0].durationWithPenaltyMedianSeconds").value(2430))
                .andExpect(jsonPath("$[0].stations[0].orderNo").value(1))
                .andExpect(jsonPath("$[0].stations[0].reached").value(4))
                .andExpect(jsonPath("$[0].stations[0].solved").value(3))
                .andExpect(jsonPath("$[0].stations[0].stoppedHere").value(0))
                .andExpect(jsonPath("$[0].stations[0].medianSeconds").value(600))
                .andExpect(jsonPath("$[0].stations[0].wrongAttempts").value(2))
                .andExpect(jsonPath("$[0].stations[0].teamsWithHint").value(1))
                .andExpect(jsonPath("$[0].stations[1].reached").value(3))
                .andExpect(jsonPath("$[0].stations[1].solved").value(2))
                .andExpect(jsonPath("$[0].stations[1].stoppedHere").value(1))
                .andExpect(jsonPath("$[0].stations[1].medianSeconds").value(1500))
                .andExpect(jsonPath("$[0].stations[1].wrongAttempts").value(5))
                .andExpect(jsonPath("$[0].stations[1].teamsWithHint").value(1));
    }

    @Test
    void showsNothingWithoutGamesInTheYearAndOnlyToTheAdmin() throws Exception {
        session(batch(false), "COMPLETED", 30, 0, false);

        mvc.perform(get("/api/v1/admin/reports/gameplay").with(admin())
                        .param("year", "2020").param("partnerId", partnerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/admin/reports/gameplay").with(user("hrad").roles("PARTNER")).param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    private UUID station(int orderNo) {
        return insert("""
                INSERT INTO station (game_id, order_no, title, story_text, task_text, task_type, reward_text)
                VALUES (?, ?, ?, 'Příběh', 'Úkol', 'TEXT', 'Odměna') RETURNING id
                """, gameId, orderNo, "Stanoviště " + orderNo);
    }

    private UUID batch(boolean development) {
        return insert("""
                INSERT INTO access_code_batch (partner_id, game_id, quantity, multi_session, handed_over_at)
                VALUES (?, ?, 10, ?, now()) RETURNING id
                """, partnerId, gameId, development);
    }

    /** One game started with a new code of the batch at t0; finishedMinute null = not finished. */
    private UUID session(UUID batchId, String status, Integer finishedMinute, int penaltySeconds, boolean valid) {
        UUID codeId = insert("""
                INSERT INTO access_code (batch_id, code, status, activated_at) VALUES (?, ?, 'ACTIVATED', ?) RETURNING id
                """, batchId, UUID.randomUUID().toString().substring(0, 20), Timestamp.from(t0));
        return insert("""
                INSERT INTO game_session (game_id, access_code_id, token, status, penalty_seconds, started_at,
                                          finished_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, gameId, codeId, UUID.randomUUID(), status, penaltySeconds, Timestamp.from(t0),
                finishedMinute == null ? null : minute(finishedMinute),
                valid ? Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)) : minute(60));
    }

    private void progress(UUID sessionId, UUID stationId, int activatedMinute, Integer solvedMinute, int attempts,
            int hints) {
        jdbc.update("""
                INSERT INTO session_progress (session_id, station_id, status, hints_used, attempts_count,
                                              activated_at, solved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, sessionId, stationId, solvedMinute == null ? "ACTIVE" : "SOLVED", hints, attempts,
                minute(activatedMinute), solvedMinute == null ? null : minute(solvedMinute));
    }

    private Timestamp minute(int minute) {
        return Timestamp.from(t0.plus(minute, ChronoUnit.MINUTES));
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
