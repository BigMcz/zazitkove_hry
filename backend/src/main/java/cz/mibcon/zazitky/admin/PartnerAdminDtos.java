package cz.mibcon.zazitky.admin;

import java.math.BigDecimal;
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

/** Partners (castles): company details and the history of their share of the revenue. */
public final class PartnerAdminDtos {

    private PartnerAdminDtos() {
    }

    /**
     * @param companyId       IČ
     * @param vatId           DIČ
     * @param currentSharePct share valid today; null when none is set yet
     * @param shares          all shares by {@code validFrom}, including planned ones
     */
    public record PartnerDto(
            UUID id,
            String name,
            String companyId,
            String vatId,
            String contactEmail,
            String contactPerson,
            String phone,
            BigDecimal currentSharePct,
            List<ShareDto> shares) {
    }

    public record ShareDto(UUID id, LocalDate validFrom, BigDecimal sharePct) {
    }

    /** Create or update a partner; everything except the name is optional. */
    public record PartnerRequest(
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "\\d{8}", message = "IČ musí mít 8 číslic") String companyId,
            @Pattern(regexp = "[A-Za-z]{2}[0-9A-Za-z]{2,12}", message = "DIČ ve tvaru např. CZ12345678") String vatId,
            @Email @Size(max = 200) String contactEmail,
            @Size(max = 200) String contactPerson,
            @Pattern(regexp = "[+0-9 ()/-]{6,30}", message = "telefon smí obsahovat číslice, mezery a + ( ) / -") String phone) {
    }

    /** A share for the same date replaces the existing one. */
    public record SetShareRequest(
            @NotNull LocalDate validFrom,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal sharePct) {
    }
}
