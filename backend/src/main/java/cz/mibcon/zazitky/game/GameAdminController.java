package cz.mibcon.zazitky.game;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.GameStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Games for the administration (e.g. choosing the game of a new batch of codes), only for the admin. */
@RestController
public class GameAdminController {

    private final GameRepository games;

    public GameAdminController(GameRepository games) {
        this.games = games;
    }

    @GetMapping("/api/v1/admin/games")
    @Transactional(readOnly = true)
    public List<AdminGameDto> games() {
        return games.findAllWithPartner().stream()
                .map(game -> new AdminGameDto(game.getId(), game.getTitle(), game.getStatus(),
                        game.getLocation().getPartner().getName()))
                .toList();
    }

    public record AdminGameDto(UUID id, String title, GameStatus status, String partnerName) {
    }
}
