package cz.mibcon.zazitky.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import cz.mibcon.zazitky.IntegrationTest;
import cz.mibcon.zazitky.session.domain.GameSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Double tap on "Odeslat": the second of two overlapping changes of one session must fail. */
@IntegrationTest
class SessionConcurrencyTest {

    static final UUID STATION_1 = UUID.fromString(SessionApiTest.STATION_1);

    @Autowired
    SessionService sessionService;

    @Autowired
    GameSessionRepository sessions;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void overlappingChangesOfOneSessionAreRejected() {
        UUID token = sessionService.start(IntegrationTest.DEV_CODE, null).state().token();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            GameSession session = sessions.findByTokenForUpdate(token).orElseThrow();
            // Another request changes the same session and commits first
            concurrent.executeWithoutResult(inner -> sessionService.revealHint(token, STATION_1));
            session.setPenaltySeconds(session.getPenaltySeconds() + 1);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        GameSession stored = sessions.findByToken(token).orElseThrow();
        assertThat(stored.getPenaltySeconds()).isEqualTo(60);
    }
}
