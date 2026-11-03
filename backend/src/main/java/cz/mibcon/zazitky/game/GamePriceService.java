package cz.mibcon.zazitky.game;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import cz.mibcon.zazitky.common.BadRequestException;
import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.GamePriceDtos.GamePricesDto;
import cz.mibcon.zazitky.game.GamePriceDtos.PriceDto;
import cz.mibcon.zazitky.game.GamePriceDtos.SetPriceRequest;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GamePrice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selling prices of the cards of the main games, valid from a date until the next change (like the partner's share).
 * Language versions share the price of their main game, as they share its cards.
 */
@Service
@Transactional
public class GamePriceService {

    /** Prices are valid from a calendar day in the castles' local time. */
    private static final ZoneId BILLING_ZONE = ZoneId.of("Europe/Prague");

    private final GameRepository games;
    private final GamePriceRepository prices;
    private final Clock clock;

    public GamePriceService(GameRepository games, GamePriceRepository prices, Clock clock) {
        this.games = games;
        this.prices = prices;
        this.clock = clock;
    }

    /** Main games of the partner with their price history. */
    @Transactional(readOnly = true)
    public List<GamePricesDto> listForPartner(UUID partnerId) {
        List<Game> partnerGames = games.findMainByPartnerId(partnerId);
        Map<UUID, List<GamePrice>> byGame = prices.findByGameIdInOrderByValidFrom(
                        partnerGames.stream().map(Game::getId).toList()).stream()
                .collect(Collectors.groupingBy(price -> price.getGame().getId()));
        return partnerGames.stream()
                .map(game -> toDto(game, byGame.getOrDefault(game.getId(), List.of())))
                .toList();
    }

    /**
     * Price valid from a date until the next entry; a price for an existing date is replaced.
     * A date in the past changes the revenue of activations that may already be invoiced.
     */
    public GamePricesDto setPrice(UUID gameId, SetPriceRequest request) {
        Game game = findMainGame(gameId);
        GamePrice price = prices.findByGameIdAndValidFrom(gameId, request.validFrom())
                .orElseGet(() -> {
                    GamePrice created = new GamePrice();
                    created.setGame(game);
                    created.setValidFrom(request.validFrom());
                    return created;
                });
        price.setPriceCzk(request.priceCzk());
        prices.saveAndFlush(price);
        return toDto(game);
    }

    public GamePricesDto deletePrice(UUID gameId, UUID priceId) {
        Game game = findMainGame(gameId);
        GamePrice price = prices.findByIdAndGameId(priceId, gameId)
                .orElseThrow(() -> new NotFoundException("Cena nebyla nalezena."));
        prices.delete(price);
        prices.flush();
        return toDto(game);
    }

    private Game findMainGame(UUID gameId) {
        Game game = games.findById(gameId).orElseThrow(() -> new NotFoundException("Hra nebyla nalezena."));
        if (game.getMainGame() != null) {
            throw new BadRequestException("Cena se nastavuje u hlavní hry, jazykové verze ji sdílejí.");
        }
        return game;
    }

    private GamePricesDto toDto(Game game) {
        return toDto(game, prices.findByGameIdInOrderByValidFrom(List.of(game.getId())));
    }

    private GamePricesDto toDto(Game game, List<GamePrice> gamePrices) {
        LocalDate today = LocalDate.now(clock.withZone(BILLING_ZONE));
        Integer current = gamePrices.stream()
                .filter(price -> !price.getValidFrom().isAfter(today))
                .reduce((first, second) -> second)
                .map(GamePrice::getPriceCzk)
                .orElse(null);
        return new GamePricesDto(game.getId(), game.getTitle(), current,
                gamePrices.stream().map(price -> new PriceDto(price.getId(), price.getValidFrom(), price.getPriceCzk()))
                        .toList());
    }
}
