package cz.mibcon.zazitky.accesscode;

import java.time.Instant;
import java.util.List;

import cz.mibcon.zazitky.game.GameDetailDto;

/**
 * @param code         the code in printed form, e.g. {@code ABCD-2345}
 * @param game              the main game of the code
 * @param languageVersions  the same game in other languages to choose from before the start, empty when none
 * @param resumesGame       the code is already in use, starting with it continues the existing game
 * @param validUntil        null when the code is not activated yet or never expires
 */
public record CodeCheckDto(String code, GameDetailDto game, List<GameDetailDto> languageVersions,
        boolean resumesGame, Instant validUntil) {
}
