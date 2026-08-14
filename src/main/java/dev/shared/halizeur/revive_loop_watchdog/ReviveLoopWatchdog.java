package dev.shared.halizeur.revive_loop_watchdog;

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
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Detects DarkBot's known "stuck on revive" refresh loop bug
 * (see: github.com/darkbot-reloaded/DarkBot issue #391 / #455).
 *
 * If the ship stays destroyed for far longer than a real revive should ever take,
 * this assumes the bot is stuck refreshing uselessly, pauses it (so it stops
 * burning hours doing nothing), and keeps watching in the background. Once the
 * game genuinely finishes loading again AND the ship is confirmed alive, it
 * resumes the bot automatically. Every state change is logged to the console/log
 * file so it's visible after the fact even if nobody was watching live.
 */
@Feature(name = "Revive Loop Watchdog", description =
        "Detects the stuck-on-revive refresh loop bug, pauses the bot, and auto-resumes once it recovers.",
        enabledByDefault = true)
public class ReviveLoopWatchdog implements Behavior, Configurable<ReviveLoopWatchdog.Config> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final HeroAPI hero;
    private final BotAPI bot;
    private final RepairAPI repair;

    private Config config;

    // When the ship was first observed destroyed in the current death, null if alive
    private Instant deadSince = null;

    // True only if THIS plugin paused the bot (so we never touch a manual user pause)
    private boolean pausedByWatchdog = false;

    public ReviveLoopWatchdog(HeroAPI hero, BotAPI bot, RepairAPI repair) {
        this.hero = hero;
        this.bot = bot;
        this.repair = repair;
    }

    @Configuration("revive_loop_watchdog.config")
    public static class Config {
        @Number(min = 1, max = 30, step = 1)
        public int STUCK_THRESHOLD_MINUTES = 3;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    // Runs while the bot is actively working normally.
    @Override
    public void onTickBehavior() {
        // Ship is alive & bot is ticking normally: nothing is wrong, clear any stale death timer.
        deadSince = null;
    }

    // Runs whenever onTickBehavior wouldn't: bot stopped/paused, ship destroyed, refreshing, or still loading.
    // This is the only place we can watch a stuck-revive situation, since the ship being
    // "destroyed" is itself one of the conditions that routes ticks here instead of onTickBehavior.
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
            if (stuckMinutes >= config.STUCK_THRESHOLD_MINUTES) {
                pauseForStuckLoop(stuckMinutes);
            }
        } else {
            // Not destroyed, and we didn't pause it - just a normal stop/refresh/loading moment.
            deadSince = null;
        }
    }

    private void pauseForStuckLoop(long stuckMinutes) {
        log("Ship has been destroyed for " + stuckMinutes + "+ minute(s) with no successful revive. " +
                "Assuming the known DarkBot stuck-on-revive refresh loop bug. Pausing bot to stop wasted refreshing.");
        bot.setRunning(false);
        pausedByWatchdog = true;
    }

    private void checkForRecovery() {
        boolean loaded = hero.getLocationInfo() != null && hero.getLocationInfo().isInitialized();
        boolean alive = !repair.isDestroyed();

        if (loaded && alive) {
            log("Game has finished loading and ship is confirmed alive again. Resuming bot automatically.");
            pausedByWatchdog = false;
            deadSince = null;
            bot.setRunning(true);
        }
        // else: keep waiting, still stuck or still loading - checked again next tick.
    }

    private void log(String message) {
        System.out.println("[" + TIME_FORMAT.format(Instant.now()) + " | ReviveLoopWatchdog] " + message);
    }
}
