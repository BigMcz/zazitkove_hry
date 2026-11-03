package cz.mibcon.zazitky.game;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Selling prices of the games (cards) over time. */
public final class GamePriceDtos {

    private GamePriceDtos() {
    }

    /**
     * @param currentPriceCzk price valid today; null when none is set yet
     * @param prices          all prices by {@code validFrom}, including planned ones
     */
    public record GamePricesDto(UUID gameId, String gameTitle, Integer currentPriceCzk, List<PriceDto> prices) {
    }

    public record PriceDto(UUID id, LocalDate validFrom, int priceCzk) {
    }

    /** A price for the same date replaces the existing one. */
    public record SetPriceRequest(@NotNull LocalDate validFrom, @NotNull @Min(0) @Max(100_000) Integer priceCzk) {
    }
}
