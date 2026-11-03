package cz.mibcon.zazitky.game;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.common.NotFoundException;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.GameStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository games;
    private final StationRepository stations;

    public GameService(GameRepository games, StationRepository stations) {
        this.games = games;
        this.stations = stations;
    }

    public GameDetailDto getPublished(UUID gameId) {
        Game game = games.findByIdAndStatus(gameId, GameStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Hra nebyla nalezena."));
        return toDetail(game);
    }

    /** Published versions of the main game in other languages. */
    public List<GameDetailDto> getPublishedLanguageVersions(UUID mainGameId) {
        return games.findByMainGameIdAndStatusOrderByLanguage(mainGameId, GameStatus.PUBLISHED).stream()
                .map(this::toDetail)
                .toList();
    }

    private GameDetailDto toDetail(Game game) {
        return new GameDetailDto(
                game.getId(),
                game.getTitle(),
                game.getSubtitle(),
                game.getIntroText(),
                game.getIntroAudioUrl(),
                game.getCoverImageUrl(),
                game.getEstimatedMinutes(),
                stations.countByGameId(game.getId()),
                game.isForChildren(),
                game.getLanguage());
    }
}
