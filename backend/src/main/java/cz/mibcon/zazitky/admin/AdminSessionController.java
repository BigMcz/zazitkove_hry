package cz.mibcon.zazitky.admin;

import java.security.Principal;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminSessionController {

    /**
     * Who is logged in (username null = nobody). Also issues the {@code XSRF-TOKEN} cookie, which the
     * admin SPA needs before its first POST (login): reading the token makes Spring Security save it.
     */
    @GetMapping(AdminSecurityConfig.SESSION_URL)
    public AdminSessionDto session(Principal principal, CsrfToken csrfToken) {
        csrfToken.getToken();
        return new AdminSessionDto(principal == null ? null : principal.getName());
    }

    public record AdminSessionDto(String username) {
    }
}
