package cz.mibcon.zazitky.session;

import java.util.List;
import java.util.UUID;

/** How the games are played, for the operator's administration. */
public final class GameplayStatsDtos {

    private GameplayStatsDtos() {
    }

    /**
     * Games started in a year (Europe/Prague); development codes are left out. Durations are in seconds
     * from the start to the last solved station, only of completed games; null when no game was completed.
     *
     * @param started    every started game, also one started again after leaving the previous one
     * @param unfinished left by the players or expired before the end
     * @param playing    still running and valid
     */
    public record GameStats(
            UUID gameId,
            String gameTitle,
            String partnerName,
            long started,
            long completed,
            long unfinished,
            long playing,
            Long durationP25Seconds,
            Long durationMedianSeconds,
            Long durationP75Seconds,
            /** Median of the duration plus the penalties for hints, i.e. the final time of the game. */
            Long durationWithPenaltyMedianSeconds,
            List<StationStats> stations) {
    }

    /**
     * One station of the game in the same games.
     *
     * @param reached        games that got to the station
     * @param solved         of those, solved
     * @param stoppedHere    ended here: left or expired while the station was not solved yet
     * @param medianSeconds  from reaching to solving the station; null when nobody solved it
     * @param wrongAttempts  all wrong answers together
     * @param teamsWithHint  games that took at least one hint here
     */
    public record StationStats(
            UUID stationId,
            int orderNo,
            String title,
            long reached,
            long solved,
            long stoppedHere,
            Long medianSeconds,
            long wrongAttempts,
            long teamsWithHint) {
    }
}
