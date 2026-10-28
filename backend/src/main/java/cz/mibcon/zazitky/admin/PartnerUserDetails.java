package cz.mibcon.zazitky.admin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Logged-in castle staff; {@link #partnerId()} limits what the partner API returns. */
public record PartnerUserDetails(
        UUID userId,
        UUID partnerId,
        String partnerName,
        String username,
        String passwordHash,
        boolean enabled) implements UserDetails {

    public static final String ROLE = "PARTNER";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
