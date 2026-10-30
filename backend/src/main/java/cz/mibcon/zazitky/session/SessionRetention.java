package cz.mibcon.zazitky.session;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps details of a particular game for 12 months after its code expired, as promised on the privacy
 * page (/ochrana-udaju): then the team name is removed. Progress, answers and times stay as anonymous
 * statistics, code activations for the administration are not touched.
 */
@Component
class SessionRetention {

    static final Duration RETENTION = Duration.ofDays(365);

    private static final Logger log = LoggerFactory.getLogger(SessionRetention.class);

    private final GameSessionRepository sessions;
    private final Clock clock;

    SessionRetention(GameSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Prague")
    @Transactional
    public void clearOldTeamNames() {
        int cleared = sessions.clearTeamNamesExpiredBefore(clock.instant().minus(RETENTION));
        if (cleared > 0) {
            log.info("Removed team names from {} game sessions older than the retention period", cleared);
        }
    }
}
