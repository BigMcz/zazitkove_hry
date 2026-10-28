package cz.mibcon.zazitky.game;

import java.util.UUID;

public record GameSummaryDto(
        UUID id,
        String title,
        String subtitle,
        Integer estimatedMinutes) {
}
