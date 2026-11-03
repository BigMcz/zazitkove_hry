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
        long stationCount,
        /** A game for children: the start asks for the name on the diploma instead of a team name. */
        boolean forChildren,
        /** Language of the texts and recordings, e.g. {@code cs} or {@code de}. */
        String language) {
}
