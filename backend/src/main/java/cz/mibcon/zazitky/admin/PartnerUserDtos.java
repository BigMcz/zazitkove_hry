package cz.mibcon.zazitky.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PartnerUserDtos {

    static final int MIN_PASSWORD_LENGTH = 10;

    private PartnerUserDtos() {
    }

    /**
     * @param currentSharePct share valid today; null when none is set yet
     * @param shares          all shares by {@code validFrom}, including planned ones
     */
    public record PartnerDto(UUID id, String name, String contactEmail, BigDecimal currentSharePct,
            List<ShareDto> shares) {
    }

    public record ShareDto(UUID id, LocalDate validFrom, BigDecimal sharePct) {
    }

    public record UpdatePartnerRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 200) String contactEmail) {
    }

    /** A share for the same date replaces the existing one. */
    public record SetShareRequest(
            @NotNull LocalDate validFrom,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal sharePct) {
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
