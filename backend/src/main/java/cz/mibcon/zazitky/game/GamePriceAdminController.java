package cz.mibcon.zazitky.game;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.game.GamePriceDtos.GamePricesDto;
import cz.mibcon.zazitky.game.GamePriceDtos.SetPriceRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Selling prices of the games; only for the admin (the castle sees them read-only in the partner API). */
@RestController
public class GamePriceAdminController {

    private final GamePriceService prices;

    public GamePriceAdminController(GamePriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/api/v1/admin/partners/{partnerId}/game-prices")
    public List<GamePricesDto> list(@PathVariable UUID partnerId) {
        return prices.listForPartner(partnerId);
    }

    @PostMapping("/api/v1/admin/games/{gameId}/prices")
    public GamePricesDto set(@PathVariable UUID gameId, @Valid @RequestBody SetPriceRequest request) {
        return prices.setPrice(gameId, request);
    }

    @DeleteMapping("/api/v1/admin/games/{gameId}/prices/{priceId}")
    public GamePricesDto delete(@PathVariable UUID gameId, @PathVariable UUID priceId) {
        return prices.deletePrice(gameId, priceId);
    }
}
