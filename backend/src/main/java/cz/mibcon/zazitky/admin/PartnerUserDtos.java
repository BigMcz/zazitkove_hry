package cz.mibcon.zazitky.admin;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PartnerUserDtos {

    static final int MIN_PASSWORD_LENGTH = 10;

    private PartnerUserDtos() {
    }

    public record PartnerUserDto(
            UUID id,
            String username,
            String displayName,
            UUID partnerId,
            String partnerName,
            boolean enabled,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    public record CreatePartnerUserRequest(
            @NotNull UUID partnerId,
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[A-Za-z0-9._@-]+",
                    message = "smí obsahovat jen písmena bez diakritiky, číslice a znaky . _ @ -") String username,
            @Size(max = 200) String displayName,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = 100) String password) {
    }

    public record SetPasswordRequest(@NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = 100) String password) {
    }

    public record SetEnabledRequest(@NotNull Boolean enabled) {
    }

    public record ChangeOwnPasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = MIN_PASSWORD_LENGTH, max = 100) String newPassword) {
    }
}
