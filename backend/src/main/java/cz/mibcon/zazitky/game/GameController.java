package cz.mibcon.zazitky.game;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<GameSummaryDto> list() {
        return gameService.listPublished();
    }

    @GetMapping("/{gameId}")
    public GameDetailDto detail(@PathVariable UUID gameId) {
        return gameService.getPublished(gameId);
    }
}
