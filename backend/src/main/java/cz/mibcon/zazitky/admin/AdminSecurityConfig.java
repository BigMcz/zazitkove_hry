package cz.mibcon.zazitky.admin;

import java.io.IOException;
import java.util.Locale;

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
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Two filter chains:
 * <ul>
 * <li>{@code /api/v1/admin/**} and {@code /api/v1/partner/**} — form login with a session cookie,
 * CSRF protection for the SPA (token in the {@code XSRF-TOKEN} cookie, sent back in the
 * {@code X-XSRF-TOKEN} header), all errors as {@code ProblemDetail}. Two kinds of accounts:
 * the admin (role ADMIN, one account from configuration) and castle staff (role PARTNER,
 * {@code partner_user} table, only the partner API with the data of their partner).</li>
 * <li>everything else — public player API and the frontend; no cookies are used there (the session
 * is identified by the {@code X-Session-Token} header), so neither CSRF nor an HTTP session.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfig {

    static final String ADMIN_API = "/api/v1/admin/**";
    static final String PARTNER_API = "/api/v1/partner/**";
    static final String ADMIN_ROLE = "ADMIN";
    static final String LOGIN_URL = "/api/v1/admin/login";
    static final String LOGOUT_URL = "/api/v1/admin/logout";
    static final String SESSION_URL = "/api/v1/admin/session";

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfig.class);

    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http, ObjectMapper json,
            @Qualifier("adminLoginRateLimiter") RateLimiter loginRateLimiter,
            @Qualifier("loginFailureLimiter") RateLimiter loginFailures, PartnerUserService partnerUsers,
            PasswordEncoder encoder, @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password:}") String adminPassword) throws Exception {
        DaoAuthenticationProvider adminProvider =
                new DaoAuthenticationProvider(adminUsers(adminUsername, adminPassword, encoder));
        adminProvider.setPasswordEncoder(encoder);
        DaoAuthenticationProvider partnerProvider = new DaoAuthenticationProvider(partnerUsers::loadForLogin);
        partnerProvider.setPasswordEncoder(encoder);

        http.securityMatcher(ADMIN_API, PARTNER_API)
                .authenticationManager(new ProviderManager(adminProvider, partnerProvider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SESSION_URL).permitAll()
                        .requestMatchers(PARTNER_API).hasRole(PartnerUserDetails.ROLE)
                        .anyRequest().hasRole(ADMIN_ROLE))
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((request, response, authentication) -> {
                            loginFailures.reset(accountKey(request));
                            if (authentication.getPrincipal() instanceof PartnerUserDetails partner) {
                                partnerUsers.recordLogin(partner.userId());
                            }
                            writeJson(response, json, HttpStatus.OK, AdminSessionController.toDto(authentication));
                        })
                        .failureHandler((request, response, exception) -> {
                            loginFailures.record(accountKey(request));
                            writeProblem(response, json, HttpStatus.UNAUTHORIZED, "invalid-credentials",
                                    "Nesprávné přihlášení", "Nesprávné jméno nebo heslo.");
                        }))
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
                .addFilterBefore(new LoginRateLimitFilter(loginRateLimiter, loginFailures, json),
                        CsrfFilter.class)
                .addFilterAfter(new RevokedPartnerFilter(partnerUsers, json), AnonymousAuthenticationFilter.class);
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
    private static UserDetailsService adminUsers(String username, String password, PasswordEncoder encoder) {
        if (password.isBlank()) {
            log.warn("app.admin.password (ADMIN_PASSWORD) is not set, the administration is disabled");
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles(ADMIN_ROLE)
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

    /** The account a login attempt is for, as the partner accounts store it. */
    private static String accountKey(HttpServletRequest request) {
        String username = request.getParameter("username");
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Limits login attempts per client address, and locks an account for a while after too many wrong passwords
     * (password guessing from many addresses). The lock also stops the right password until it passes.
     */
    static class LoginRateLimitFilter extends OncePerRequestFilter {

        private final RateLimiter rateLimiter;
        private final RateLimiter failures;
        private final ObjectMapper json;

        LoginRateLimitFilter(RateLimiter rateLimiter, RateLimiter failures, ObjectMapper json) {
            this.rateLimiter = rateLimiter;
            this.failures = failures;
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
                rateLimiter.acquire(RateLimiter.clientKey(request.getRemoteAddr()));
                failures.check(accountKey(request));
            } catch (TooManyRequestsException e) {
                writeProblem(response, json, e.getStatus(), e.getTypeSuffix(), e.getTitle(), e.getMessage());
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /**
     * Ends the session of castle staff whose account the admin has disabled or whose password has changed since the
     * login, so that revoking access takes effect at once and not only when the session expires.
     */
    static class RevokedPartnerFilter extends OncePerRequestFilter {

        private final PartnerUserService partnerUsers;
        private final ObjectMapper json;

        RevokedPartnerFilter(PartnerUserService partnerUsers, ObjectMapper json) {
            this.partnerUsers = partnerUsers;
            this.json = json;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PartnerUserDetails partner
                    && !partnerUsers.isStillValid(partner)) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthorized", "Nepřihlášeno",
                        "Přístup k účtu se změnil, přihlas se prosím znovu.");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
