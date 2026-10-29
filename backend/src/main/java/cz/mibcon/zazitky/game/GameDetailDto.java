package cz.mibcon.zazitky.game;

import java.util.UUID;

/** Game intro screen. Deliberately without stations — those are revealed one by one in a session. */
public record GameDetailDto(
        UUID id,
        String title,
        String subtitle,
        String introText,
        /** Spoken intro, null = text only. */
        String introAudioUrl,
        /** Picture on the intro screen, null = none. */
        String coverImageUrl,
        Integer estimatedMinutes,
        long stationCount) {
}
