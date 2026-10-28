package cz.mibcon.zazitky;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AccessCodeBatchRepository;
import cz.mibcon.zazitky.accesscode.AccessCodeRepository;
import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeStatus;
import cz.mibcon.zazitky.game.GameRepository;
import cz.mibcon.zazitky.game.LocationRepository;
import cz.mibcon.zazitky.game.PartnerRepository;
import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GameStatus;
import cz.mibcon.zazitky.game.domain.Hint;
import cz.mibcon.zazitky.game.domain.Location;
import cz.mibcon.zazitky.game.domain.Partner;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.StationChoice;
import cz.mibcon.zazitky.game.domain.TaskType;
import cz.mibcon.zazitky.session.AnswerAttemptRepository;
import cz.mibcon.zazitky.session.GameSessionRepository;
import cz.mibcon.zazitky.session.domain.AnswerAttempt;
import cz.mibcon.zazitky.session.domain.GameSession;
import cz.mibcon.zazitky.session.domain.SessionProgress;
import cz.mibcon.zazitky.session.domain.SessionStatus;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class SchemaIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    PartnerRepository partners;

    @Autowired
    LocationRepository locations;

    @Autowired
    GameRepository games;

    @Autowired
    AccessCodeBatchRepository batches;

    @Autowired
    AccessCodeRepository accessCodes;

    @Autowired
    GameSessionRepository sessions;

    @Autowired
    AnswerAttemptRepository attempts;

    @Test
    void schemaMigrationIsApplied() {
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion() == null ? null : migration.getVersion().getVersion())
                .contains("1");

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains(
                "partner", "location", "game", "station", "hint", "accepted_answer", "station_choice",
                "access_code_batch", "access_code",
                "game_session", "session_progress", "answer_attempt");
    }

    @Test
    void entitiesRoundTripThroughDatabase() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Partner partner = new Partner();
        partner.setName("Test partner");
        partner.setRevenueSharePct(new BigDecimal("50.00"));
        partners.save(partner);

        Location location = new Location();
        location.setPartner(partner);
        location.setName("Test hrad");
        location.setLatitude(new BigDecimal("50.186900"));
        location.setLongitude(new BigDecimal("12.754500"));
        locations.save(location);

        Station station = new Station();
        station.setOrderNo(1);
        station.setTitle("Sklepení");
        station.setStoryText("Ve sklepení je tma.");
        station.setTaskText("Co kat ve sklepení zanechal?");
        station.setTaskType(TaskType.CHOICE);
        station.setRewardText("Ano!");
        Hint hint = new Hint();
        hint.setOrderNo(1);
        hint.setText("Když odcházel potmě, něco mu chybělo.");
        station.addHint(hint);
        AcceptedAnswer answer = new AcceptedAnswer();
        answer.setValue("Lucernu");
        answer.setNormalizedValue("lucernu");
        station.addAcceptedAnswer(answer);
        StationChoice choice = new StationChoice();
        choice.setOrderNo(1);
        choice.setText("Lucernu");
        station.addChoice(choice);

        Game game = new Game();
        game.setLocation(location);
        game.setSlug("test-game");
        game.setTitle("Test");
        game.setIntroText("Intro");
        game.setOutroText("Outro");
        game.setStatus(GameStatus.PUBLISHED);
        game.addStation(station);
        games.save(game);

        AccessCodeBatch batch = new AccessCodeBatch();
        batch.setPartner(partner);
        batch.setGame(game);
        batch.setQuantity(10);
        batches.save(batch);
        AccessCode code = new AccessCode();
        code.setBatch(batch);
        code.setCode("ABCD-2345");
        accessCodes.save(code);

        UUID token = UUID.randomUUID();
        GameSession session = new GameSession();
        session.setGame(game);
        session.setAccessCode(code);
        session.setToken(token);
        session.setStartedAt(now);
        session.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
        SessionProgress progress = new SessionProgress();
        progress.setStation(station);
        progress.setActivatedAt(now);
        session.addProgress(progress);
        sessions.save(session);

        AnswerAttempt attempt = new AnswerAttempt();
        attempt.setSession(session);
        attempt.setStation(station);
        attempt.setRawAnswer("Meč");
        attempt.setNormalizedAnswer("mec");
        attempt.setCreatedAt(now);
        attempts.save(attempt);

        em.flush();
        em.clear();

        Game loadedGame = games.findBySlug("test-game").orElseThrow();
        assertThat(loadedGame.getLanguage()).isEqualTo("cs");
        assertThat(loadedGame.getSessionValidityHours()).isEqualTo(24);
        assertThat(loadedGame.getCreatedAt()).isNotNull();
        Station loadedStation = loadedGame.getStations().getFirst();
        assertThat(loadedStation.getTaskType()).isEqualTo(TaskType.CHOICE);
        assertThat(loadedStation.getHints()).extracting(Hint::getPenaltySeconds).containsExactly(60);
        assertThat(loadedStation.getAcceptedAnswers()).extracting(AcceptedAnswer::getNormalizedValue)
                .containsExactly("lucernu");
        assertThat(loadedStation.getChoices()).extracting(StationChoice::getText).containsExactly("Lucernu");

        assertThat(accessCodes.findById(code.getId()).orElseThrow().getStatus()).isEqualTo(AccessCodeStatus.NEW);

        GameSession loadedSession = sessions.findByToken(token).orElseThrow();
        assertThat(loadedSession.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(loadedSession.getStartedAt()).isEqualTo(now);
        assertThat(loadedSession.getAccessCode().getCode()).isEqualTo("ABCD-2345");
        assertThat(loadedSession.getProgress()).hasSize(1);
        assertThat(loadedSession.getVersion()).isZero();

        loadedSession.setPenaltySeconds(60);
        em.flush();
        assertThat(loadedSession.getVersion()).isEqualTo(1);
    }
}
