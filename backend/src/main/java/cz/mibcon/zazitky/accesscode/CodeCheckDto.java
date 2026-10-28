package cz.mibcon.zazitky.accesscode;

import java.time.Instant;

import cz.mibcon.zazitky.game.GameDetailDto;

/**
 * @param code         the code in printed form, e.g. {@code ABCD-2345}
 * @param resumesGame  the code is already in use, starting with it continues the existing game
 * @param validUntil   null when the code is not activated yet or never expires
 */
public record CodeCheckDto(String code, GameDetailDto game, boolean resumesGame, Instant validUntil) {
}
