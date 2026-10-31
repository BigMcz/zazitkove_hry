package cz.mibcon.zazitky.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import cz.mibcon.zazitky.game.domain.AcceptedAnswer;
import cz.mibcon.zazitky.game.domain.Game;
import cz.mibcon.zazitky.game.domain.Station;
import cz.mibcon.zazitky.game.domain.StationChoice;
import cz.mibcon.zazitky.session.SessionPackage.Finale;
import cz.mibcon.zazitky.session.SessionPackage.Hint;
import cz.mibcon.zazitky.session.SessionPackage.Sealed;
import cz.mibcon.zazitky.session.SessionPackage.SealedStation;
import cz.mibcon.zazitky.session.SessionPackage.StationContent;
import cz.mibcon.zazitky.session.SessionPackage.Unlocked;
import cz.mibcon.zazitky.session.domain.GameSession;
import cz.mibcon.zazitky.session.domain.ProgressStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds offline packages. Sealing a game (PBKDF2 for every accepted answer) is slow, so it is done once per
 * game and kept in memory; game content changes only with a new deployment (seed migration), which restarts
 * the application. Once the administration edits content, it must evict the game here.
 */
@Component
class GamePackages {

    private final ObjectMapper json;
    private final Map<UUID, SealedGame> sealedGames = new ConcurrentHashMap<>();

    GamePackages(ObjectMapper json) {
        this.json = json;
    }

    SessionPackage forSession(GameSession session) {
        Game game = session.getGame();
        SealedGame sealed = sealedGames.computeIfAbsent(game.getId(), id -> seal(game));

        Map<UUID, String> unlockedKeys = new LinkedHashMap<>();
        session.getProgress().stream()
                .filter(progress -> progress.getStatus() == ProgressStatus.SOLVED)
                .map(progress -> progress.getStation().getId())
                .forEach(stationId -> unlockedKeys.put(stationId, sealed.contentKeys().get(stationId)));

        long letters = game.getStations().stream().filter(station -> station.getPuzzleLetter() != null).count();
        List<Integer> puzzleSlots = letters == 0 ? null : PuzzleLetters.scrambledSlots(session.getId(), (int) letters);

        return new SessionPackage(game.getId(), PackageCrypto.KDF_ITERATIONS, sealed.firstStation(),
                sealed.stations(), puzzleSlots, unlockedKeys, sealed.media());
    }

    private SealedGame seal(Game game) {
        List<Station> stations = game.getStations();
        List<SealedStation> sealedStations = new ArrayList<>();
        Map<UUID, String> contentKeys = new LinkedHashMap<>();
        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            Station next = i + 1 < stations.size() ? stations.get(i + 1) : null;
            Unlocked unlocked = new Unlocked(station.getRewardText(), station.getRewardAudioUrl(),
                    station.getNextText(), station.getPuzzleLetter(),
                    next == null ? null : content(next),
                    next == null ? new Finale(game.getOutroText(), game.getOutroAudioUrl()) : null);

            byte[] rawKey = PackageCrypto.randomBytes(32);
            SecretKey contentKey = PackageCrypto.contentKey(rawKey);
            byte[] salt = PackageCrypto.randomBytes(16);
            List<Sealed> keys = station.getAcceptedAnswers().stream()
                    .map(AcceptedAnswer::getNormalizedValue)
                    .distinct()
                    .map(answer -> PackageCrypto.answerKey(answer, salt, PackageCrypto.KDF_ITERATIONS))
                    .map(answerKey -> PackageCrypto.seal(answerKey, rawKey))
                    .toList();
            Sealed content = PackageCrypto.seal(contentKey, json.writeValueAsBytes(unlocked));

            sealedStations.add(new SealedStation(station.getId(), PackageCrypto.encode(salt), keys, content));
            contentKeys.put(station.getId(), PackageCrypto.encode(rawKey));
        }
        return new SealedGame(content(stations.getFirst()), List.copyOf(sealedStations), Map.copyOf(contentKeys),
                media(game));
    }

    private static StationContent content(Station station) {
        List<String> choices = station.getChoices().isEmpty() ? null
                : station.getChoices().stream().map(StationChoice::getText).toList();
        List<Hint> hints = station.getHints().stream()
                .map(hint -> new Hint(hint.getOrderNo(), hint.getText(), hint.getPenaltySeconds()))
                .toList();
        return new StationContent(station.getId(), station.getOrderNo(), station.getTitle(), station.getStoryText(),
                station.getStoryAudioUrl(), station.isListenOnly(), station.getImageUrl(), station.getImageAlt(),
                station.getTaskText(), station.getTaskType(), choices, hints);
    }

    private static List<String> media(Game game) {
        Stream<String> gameMedia = Stream.of(game.getCoverImageUrl(), game.getIntroAudioUrl(), game.getOutroAudioUrl());
        Stream<String> stationMedia = game.getStations().stream()
                .flatMap(station -> Stream.of(station.getImageUrl(), station.getStoryAudioUrl(),
                        station.getRewardAudioUrl()));
        Set<String> media = new LinkedHashSet<>();
        Stream.concat(gameMedia, stationMedia).filter(Objects::nonNull).forEach(media::add);
        return List.copyOf(media);
    }

    private record SealedGame(
            StationContent firstStation,
            List<SealedStation> stations,
            Map<UUID, String> contentKeys,
            List<String> media) {
    }
}
