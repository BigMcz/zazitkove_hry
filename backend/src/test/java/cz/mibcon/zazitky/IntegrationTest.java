package cz.mibcon.zazitky;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application context against PostgreSQL in Testcontainers, with schema and demo game seed.
 * All integration tests share this setup, so Spring caches one context (and one container) for them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
        "app.admin.password=" + IntegrationTest.ADMIN_PASSWORD,
        // all tests come from 127.0.0.1; the limit itself is tested with its own IP address
        "app.access-code.attempts-per-minute=" + IntegrationTest.CODE_ATTEMPTS_PER_MINUTE,
        "app.admin.login-attempts-per-minute=1000"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    String ADMIN_PASSWORD = "test-password";
    int CODE_ATTEMPTS_PER_MINUTE = 200;
    /** Development code from the seed: never expires, every entry starts a new game. */
    String DEV_CODE = "DEMO-KATA";
}
