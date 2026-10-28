package cz.mibcon.zazitky.common;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limits are per client IP ({@code request.getRemoteAddr()}). Behind a reverse proxy the proxy must
 * pass the client address and {@code server.forward-headers-strategy} must be set, otherwise all
 * players share one limit.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    @Bean
    RateLimiter accessCodeRateLimiter(@Value("${app.access-code.attempts-per-minute:10}") int attempts, Clock clock) {
        return new RateLimiter(attempts, clock);
    }

    @Bean
    RateLimiter adminLoginRateLimiter(@Value("${app.admin.login-attempts-per-minute:5}") int attempts, Clock clock) {
        return new RateLimiter(attempts, clock);
    }
}
