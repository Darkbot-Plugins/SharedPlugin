package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;

@Configuration("dailytaskapi.ram_cleaner")
public final class DailyRamCleanerConfig {
    public boolean enabled = true;

    @Number(min = 512, max = 16384, step = 128)
    public int ramThresholdMb = 1200;

    @Number(min = 15, max = 600, step = 15)
    public int checkIntervalSeconds = 30;

    public boolean avoidCombat = true;

    /**
     * Clearing the game cache can cause assets to be loaded again. Keep this
     * disabled unless ordinary working-set trimming is insufficient.
     */
    public boolean clearGameCache = false;
}
