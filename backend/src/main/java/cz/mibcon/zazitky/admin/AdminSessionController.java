package cz.mibcon.zazitky.admin;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    public AdminSessionDto session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        return toDto(authentication);
    }

    static AdminSessionDto toDto(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return new AdminSessionDto(null, null, null);
        }
        if (authentication.getPrincipal() instanceof PartnerUserDetails partner) {
            return new AdminSessionDto(partner.username(), PartnerUserDetails.ROLE, partner.partnerName());
        }
        return new AdminSessionDto(authentication.getName(), AdminSecurityConfig.ADMIN_ROLE, null);
    }

    /** @param role ADMIN or PARTNER; null when nobody is logged in */
    public record AdminSessionDto(String username, String role, String partnerName) {
    }
}
