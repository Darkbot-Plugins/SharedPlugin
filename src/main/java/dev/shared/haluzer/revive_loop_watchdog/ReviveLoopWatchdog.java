package dev.shared.haluzer.revive_loop_watchdog;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.RepairAPI;

import java.time.Instant;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

@Feature(name = "Revive Loop Watchdog", description =
        "Detects the stuck-on-revive refresh loop bug, pauses the bot, and auto-resumes once it recovers.",
        enabledByDefault = true)
public class ReviveLoopWatchdog implements Behavior, Configurable<ReviveLoopWatchdog.Config> {

    private static final Logger LOGGER = Logger.getLogger(ReviveLoopWatchdog.class.getName());

    private final HeroAPI hero;
    private final BotAPI bot;
    private final RepairAPI repair;

    private Config config = new Config();

    private Instant deadSince = null;
    private boolean pausedByWatchdog = false;

    public ReviveLoopWatchdog(HeroAPI hero, BotAPI bot, RepairAPI repair) {
        this.hero = hero;
        this.bot = bot;
        this.repair = repair;
    }

    @Configuration("revive_loop_watchdog.config")
    public static class Config {
        @Number(min = 1, max = 30, step = 1)
        public int stuckThresholdMinutes = 3;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        deadSince = null;
    }

    @Override
    public void onStoppedBehavior() {
        if (pausedByWatchdog) {
            checkForRecovery();
            return;
        }

        if (repair.isDestroyed()) {
            if (deadSince == null) {
                deadSince = Instant.now();
            }

            long stuckMinutes = Duration.between(deadSince, Instant.now()).toMinutes();
            if (stuckMinutes >= config.stuckThresholdMinutes) {
                pauseForStuckLoop(stuckMinutes);
            }
        } else {
            deadSince = null;
        }
    }

    private void pauseForStuckLoop(long stuckMinutes) {
        if (!bot.isRunning()) {
            return;
        }

        LOGGER.log(Level.INFO, "Revive Loop Watchdog: ship has been destroyed for {0}+ minute(s) with no "
                + "successful revive. Assuming the known DarkBot stuck-on-revive refresh loop bug. "
                + "Pausing bot to stop wasted refreshing.", stuckMinutes);
        bot.setRunning(false);
        pausedByWatchdog = true;
    }

    private void checkForRecovery() {
        boolean loaded = hero.getLocationInfo() != null && hero.getLocationInfo().isInitialized();
        boolean alive = !repair.isDestroyed();

        if (loaded && alive) {
            LOGGER.info("Revive Loop Watchdog: game has finished loading and ship is confirmed alive again. "
                    + "Resuming bot automatically.");
            pausedByWatchdog = false;
            deadSince = null;
            bot.setRunning(true);
        }
    }
}
