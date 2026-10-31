package cz.mibcon.zazitky.session;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.session.GameplayStatsDtos.GameStats;
import cz.mibcon.zazitky.session.GameplayStatsDtos.StationStats;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completion, duration and difficulty of the stations, computed from the played games. Team names are
 * not needed, so the numbers stay after the retention clears them (SessionRetention).
 */
@Service
@Transactional(readOnly = true)
public class GameplayStatsService {

    /** Years follow the local time of the castles, as the activation report does. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    /** Games of real players started in the year; "playing" = still running and valid now. */
    private static final String SESSIONS = """
            WITH s AS (
                SELECT gs.id, gs.game_id, gs.status, gs.started_at, gs.finished_at, gs.penalty_seconds,
                       gs.status = 'IN_PROGRESS' AND gs.expires_at > :now AS playing
                FROM game_session gs
                JOIN access_code c ON c.id = gs.access_code_id
                JOIN access_code_batch b ON b.id = c.batch_id
                JOIN game g ON g.id = gs.game_id
                JOIN location l ON l.id = g.location_id
                WHERE NOT b.multi_session
                  AND gs.started_at >= :from AND gs.started_at < :to
                  AND (CAST(:partnerId AS uuid) IS NULL OR l.partner_id = :partnerId)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public GameplayStatsService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @param partnerId only games of this partner; null = all */
    public List<GameStats> gameStats(int year, UUID partnerId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(clock.instant()))
                .addValue("from", Timestamp.from(LocalDate.of(year, 1, 1).atStartOfDay(ZONE).toInstant()))
                .addValue("to", Timestamp.from(LocalDate.of(year + 1, 1, 1).atStartOfDay(ZONE).toInstant()))
                .addValue("partnerId", partnerId);

        Map<UUID, List<StationStats>> stations = jdbc.query(SESSIONS + """
                SELECT st.game_id, st.id, st.order_no, st.title,
                       count(sp.id) AS reached,
                       count(sp.id) FILTER (WHERE sp.status = 'SOLVED') AS solved,
                       count(sp.id) FILTER (WHERE sp.status <> 'SOLVED' AND NOT s.playing) AS stopped_here,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM sp.solved_at - sp.activated_at))
                           FILTER (WHERE sp.solved_at IS NOT NULL) AS median_seconds,
                       coalesce(sum(sp.attempts_count - CASE WHEN sp.status = 'SOLVED' THEN 1 ELSE 0 END), 0)
                           AS wrong_attempts,
                       count(sp.id) FILTER (WHERE sp.hints_used > 0) AS with_hint
                FROM station st
                LEFT JOIN (session_progress sp JOIN s ON s.id = sp.session_id) ON sp.station_id = st.id
                WHERE st.game_id IN (SELECT game_id FROM s)
                GROUP BY st.game_id, st.id, st.order_no, st.title
                ORDER BY st.order_no
                """, params, (rs, row) -> new GameStation(rs.getObject("game_id", UUID.class), new StationStats(
                        rs.getObject("id", UUID.class),
                        rs.getInt("order_no"),
                        rs.getString("title"),
                        rs.getLong("reached"),
                        rs.getLong("solved"),
                        rs.getLong("stopped_here"),
                        seconds(rs, "median_seconds"),
                        rs.getLong("wrong_attempts"),
                        rs.getLong("with_hint"))))
                .stream()
                .collect(Collectors.groupingBy(GameStation::gameId,
                        Collectors.mapping(GameStation::station, Collectors.toList())));

        return jdbc.query(SESSIONS + """
                SELECT g.id, g.title, p.name AS partner_name,
                       count(*) AS started,
                       count(*) FILTER (WHERE s.status = 'COMPLETED') AS completed,
                       count(*) FILTER (WHERE s.playing) AS playing,
                       percentile_cont(ARRAY[0.25, 0.5, 0.75]) WITHIN GROUP (ORDER BY extract(epoch FROM s.finished_at - s.started_at))
                           FILTER (WHERE s.status = 'COMPLETED') AS duration,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM s.finished_at - s.started_at) + s.penalty_seconds)
                           FILTER (WHERE s.status = 'COMPLETED') AS duration_with_penalty
                FROM s
                JOIN game g ON g.id = s.game_id
                JOIN location l ON l.id = g.location_id
                JOIN partner p ON p.id = l.partner_id
                GROUP BY g.id, g.title, p.name
                ORDER BY p.name, g.title
                """, params, (rs, row) -> {
                    UUID gameId = rs.getObject("id", UUID.class);
                    long started = rs.getLong("started");
                    long completed = rs.getLong("completed");
                    long playing = rs.getLong("playing");
                    Long[] duration = durations(rs);
                    return new GameStats(gameId, rs.getString("title"), rs.getString("partner_name"),
                            started, completed, started - completed - playing, playing,
                            duration[0], duration[1], duration[2], seconds(rs, "duration_with_penalty"),
                            stations.getOrDefault(gameId, List.of()));
                });
    }

    private record GameStation(UUID gameId, StationStats station) {
    }

    private static Long seconds(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Math.round(value.doubleValue());
    }

    /** Quartiles of the duration; nulls when no game was completed. */
    private static Long[] durations(ResultSet rs) throws SQLException {
        Long[] result = new Long[3];
        Array array = rs.getArray("duration");
        if (array != null) {
            Object[] values = (Object[]) array.getArray();
            for (int i = 0; i < result.length; i++) {
                result[i] = values[i] == null ? null : Math.round(((Number) values[i]).doubleValue());
            }
        }
        return result;
    }
}
