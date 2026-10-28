package cz.mibcon.zazitky.admin;

import java.io.IOException;
import java.util.Map;

import cz.mibcon.zazitky.common.ApiExceptionHandler;
import cz.mibcon.zazitky.common.RateLimiter;
import cz.mibcon.zazitky.common.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Two filter chains:
 * <ul>
 * <li>{@code /api/v1/admin/**} — one admin account from configuration, form login with a session
 * cookie, CSRF protection for the SPA (token in the {@code XSRF-TOKEN} cookie, sent back in the
 * {@code X-XSRF-TOKEN} header), all errors as {@code ProblemDetail}.</li>
 * <li>everything else — public player API and the frontend; no cookies are used there (the session
 * is identified by the {@code X-Session-Token} header), so neither CSRF nor an HTTP session.</li>
 * </ul>
 * Partner accounts (castle staff checking monthly activations) will need users with roles in the DB.
 */
@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfig {

    static final String ADMIN_API = "/api/v1/admin/**";
    static final String LOGIN_URL = "/api/v1/admin/login";
    static final String LOGOUT_URL = "/api/v1/admin/logout";
    static final String SESSION_URL = "/api/v1/admin/session";

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfig.class);

    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http, ObjectMapper json,
            @Qualifier("adminLoginRateLimiter") RateLimiter loginRateLimiter) throws Exception {
        http.securityMatcher(ADMIN_API)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SESSION_URL).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((request, response, authentication) ->
                                writeJson(response, json, HttpStatus.OK, Map.of("username", authentication.getName())))
                        .failureHandler((request, response, exception) ->
                                writeProblem(response, json, HttpStatus.UNAUTHORIZED, "invalid-credentials",
                                        "Nesprávné přihlášení", "Nesprávné jméno nebo heslo.")))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Nepřihlášeno", "Pro administraci se musíš přihlásit."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, json, HttpStatus.FORBIDDEN, "forbidden",
                                        "Přístup odepřen", "Požadavek byl odmítnut. Obnov stránku a zkus to znovu.")))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new LoginRateLimitFilter(loginRateLimiter, json),
                        CsrfFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // SpaWebConfig decides about caching of the frontend files
                .headers(headers -> headers.cacheControl(cache -> cache.disable()))
                .requestCache(cache -> cache.disable());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Without a configured password there is no admin account, the admin API then cannot be used. */
    @Bean
    UserDetailsService adminUsers(@Value("${app.admin.username:admin}") String username,
            @Value("${app.admin.password:}") String password, PasswordEncoder encoder) {
        if (password.isBlank()) {
            log.warn("app.admin.password (ADMIN_PASSWORD) is not set, the administration is disabled");
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper json, HttpStatus status,
            String typeSuffix, String title, String detail) throws IOException {
        ProblemDetail problem = ApiExceptionHandler.problem(status, typeSuffix, title, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper json, HttpStatus status, Object body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }

    /** Limits login attempts per client IP (password guessing). */
    static class LoginRateLimitFilter extends OncePerRequestFilter {

        private final RateLimiter rateLimiter;
        private final ObjectMapper json;

        LoginRateLimitFilter(RateLimiter rateLimiter, ObjectMapper json) {
            this.rateLimiter = rateLimiter;
            this.json = json;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !("POST".equals(request.getMethod()) && LOGIN_URL.equals(request.getRequestURI()));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                rateLimiter.acquire(request.getRemoteAddr());
            } catch (TooManyRequestsException e) {
                writeProblem(response, json, e.getStatus(), e.getTypeSuffix(), e.getTitle(), e.getMessage());
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
