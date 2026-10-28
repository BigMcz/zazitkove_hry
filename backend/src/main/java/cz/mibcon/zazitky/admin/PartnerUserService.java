package cz.mibcon.zazitky.admin;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import cz.mibcon.zazitky.admin.PartnerUserDtos.CreatePartnerUserRequest;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerDto;
import cz.mibcon.zazitky.admin.PartnerUserDtos.PartnerUserDto;
import cz.mibcon.zazitky.admin.domain.PartnerUser;
import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.PartnerRepository;
import cz.mibcon.zazitky.game.domain.Partner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts of castle staff, managed by the admin. */
@Service
@Transactional
public class PartnerUserService {

    private final PartnerUserRepository users;
    private final PartnerRepository partners;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String adminUsername;

    public PartnerUserService(PartnerUserRepository users, PartnerRepository partners, PasswordEncoder passwordEncoder,
            Clock clock, @Value("${app.admin.username:admin}") String adminUsername) {
        this.users = users;
        this.partners = partners;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminUsername = adminUsername;
    }

    /** For Spring Security's login. */
    @Transactional(readOnly = true)
    public PartnerUserDetails loadForLogin(String username) {
        PartnerUser user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown partner user"));
        return new PartnerUserDetails(user.getId(), user.getPartner().getId(), user.getPartner().getName(),
                user.getUsername(), user.getPasswordHash(), user.isEnabled());
    }

    public void recordLogin(UUID userId) {
        users.findById(userId).ifPresent(user -> user.setLastLoginAt(clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<PartnerDto> listPartners() {
        return partners.findAll(Sort.by("name")).stream()
                .map(partner -> new PartnerDto(partner.getId(), partner.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerUserDto> listUsers() {
        return users.findAllWithPartner().stream().map(PartnerUserService::toDto).toList();
    }

    public PartnerUserDto create(CreatePartnerUserRequest request) {
        Partner partner = partners.findById(request.partnerId())
                .orElseThrow(() -> new NotFoundException("Partner nebyl nalezen."));
        String username = normalize(request.username());
        if (username.equals(normalize(adminUsername)) || users.existsByUsername(username)) {
            throw new BadRequestException("Uživatelské jméno " + username + " už existuje.");
        }

        PartnerUser user = new PartnerUser();
        user.setPartner(partner);
        user.setUsername(username);
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? null
                : request.displayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        users.save(user);
        return toDto(user);
    }

    public void setPassword(UUID userId, String password) {
        find(userId).setPasswordHash(passwordEncoder.encode(password));
    }

    /** A disabled account cannot log in; an already open session lasts until it expires or logs out. */
    public void setEnabled(UUID userId, boolean enabled) {
        find(userId).setEnabled(enabled);
    }

    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        PartnerUser user = find(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Současné heslo nesouhlasí.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private PartnerUser find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Účet nebyl nalezen."));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private static PartnerUserDto toDto(PartnerUser user) {
        return new PartnerUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getPartner().getId(),
                user.getPartner().getName(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
