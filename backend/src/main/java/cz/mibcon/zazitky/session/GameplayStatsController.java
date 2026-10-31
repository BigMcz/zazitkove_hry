package cz.mibcon.zazitky.session;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.session.GameplayStatsDtos.GameStats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** How the games are played; only for the admin (see AdminSecurityConfig), the castles see only sales. */
@RestController
public class GameplayStatsController {

    private final GameplayStatsService stats;

    public GameplayStatsController(GameplayStatsService stats) {
        this.stats = stats;
    }

    /** @param partnerId only games of this partner; all when missing */
    @GetMapping("/api/v1/admin/reports/gameplay")
    public List<GameStats> gameplay(@RequestParam @Min(2020) @Max(2100) int year,
            @RequestParam(required = false) UUID partnerId) {
        return stats.gameStats(year, partnerId);
    }
}
