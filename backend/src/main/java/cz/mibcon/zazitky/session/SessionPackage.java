package cz.mibcon.zazitky.session;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import cz.mibcon.zazitky.game.domain.TaskType;

/**
 * Offline game package of a session ({@code GET /sessions/current/package}, docs/02-architektura.md,
 * "Offline režim"). Never contains accepted answers: everything a station reveals is in {@link SealedStation}
 * encrypted by a key that only a correct answer opens.
 */
public record SessionPackage(
        UUID gameId,
        /** PBKDF2-HMAC-SHA256 iterations for the answer keys. */
        int kdfIterations,
        StationContent firstStation,
        /** All stations in order; solving one opens its {@code content}, see {@link Unlocked}. */
        List<SealedStation> stations,
        /** Slot of the letter of each station that has one (in station order), null when the game has no puzzle. */
        List<Integer> puzzleSlots,
        /** Content keys (base64) of the stations this session already solved, also on another phone. */
        Map<UUID, String> unlockedKeys,
        /** Pictures and sounds of the game to download for playing without a signal. */
        List<String> media) {

    public record StationContent(
            UUID id,
            int orderNo,
            String title,
            String storyText,
            String storyAudioUrl,
            boolean listenOnly,
            String imageUrl,
            String imageAlt,
            String taskText,
            TaskType taskType,
            List<String> choices,
            List<Hint> hints) {
    }

    public record Hint(int orderNo, String text, int penaltySeconds) {
    }

    /**
     * {@code keys}: the content key of the station encrypted by the key of each accepted answer (PBKDF2 from the
     * normalized answer and {@code salt}); {@code content}: {@link Unlocked} as JSON encrypted by the content key.
     */
    public record SealedStation(UUID id, String salt, List<Sealed> keys, Sealed content) {
    }

    /** AES-GCM: base64 IV and base64 ciphertext with the tag. */
    public record Sealed(String iv, String data) {
    }

    /** What solving a station reveals; {@code next} is null for the last station, which has {@code finale}. */
    public record Unlocked(
            String rewardText,
            String rewardAudioUrl,
            String nextText,
            String puzzleLetter,
            StationContent next,
            Finale finale) {
    }

    public record Finale(String outroText, String outroAudioUrl) {
    }
}
