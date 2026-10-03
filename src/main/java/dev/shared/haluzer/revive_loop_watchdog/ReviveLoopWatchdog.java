package dev.shared.haluzer.revive_loop_watchdog;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.RepairAPI;

import java.time.Instant;
import java.time.Duration;

@Feature(name = "Revive Loop Watchdog", description =
        "Detects the stuck-on-revive refresh loop bug, pauses the bot, and auto-resumes once it recovers.",
        enabledByDefault = false)
public class ReviveLoopWatchdog implements Behavior, Configurable<ReviveLoopWatchdog.Config> {

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
        @Option("revive_loop_watchdog.config.stuckthresholdminutes")
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

        System.out.println("Revive Loop Watchdog: ship destroyed for " + stuckMinutes
                + "+ minute(s) with no successful revive, pausing bot to stop wasted refreshing.");
        bot.setRunning(false);
        pausedByWatchdog = true;
    }

    private void checkForRecovery() {
        boolean loaded = hero.getLocationInfo() != null && hero.getLocationInfo().isInitialized();
        boolean alive = !repair.isDestroyed();

        if (loaded && alive) {
            System.out.println("Revive Loop Watchdog: game loaded and ship alive again, resuming bot.");
            pausedByWatchdog = false;
            deadSince = null;
            bot.setRunning(true);
        }
    }
}
