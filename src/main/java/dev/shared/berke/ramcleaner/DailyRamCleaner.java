package dev.shared.berke.ramcleaner;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import com.github.manolo8.darkbot.core.api.Capability;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.HeroAPI;

/**
 * Threshold-based game-client working-set cleaner.
 *
 * <p>The implementation uses the same RAM operation exposed by DarkBot's own
 * API settings screen. It does not terminate processes, edit JVM arguments or
 * delete files.</p>
 */
@Feature(name = "BerkePlugin RAM Cleaner",
        description = "Reduces the game client's working set above a configurable memory threshold",
        enabledByDefault = true)
public final class DailyRamCleaner implements Behavior, Configurable<DailyRamCleanerConfig> {
    private static final long MIN_RETRY_AFTER_COMBAT_MS = 5_000L;

    private final IDarkBotAPI darkbotApi;
    private final HeroAPI hero;
    private final AttackAPI attack;

    private DailyRamCleanerConfig config = new DailyRamCleanerConfig();
    private long nextCheckAt;

    public DailyRamCleaner(PluginAPI api) {
        this.darkbotApi = Main.API;
        this.hero = api.requireAPI(HeroAPI.class);
        this.attack = api.requireAPI(AttackAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<DailyRamCleanerConfig> setting) {
        DailyRamCleanerConfig configured = setting.getValue();
        if (configured != null) config = configured;
        setting.addListener(updated -> {
            if (updated != null) config = updated;
            nextCheckAt = 0L;
        });
    }

    @Override
    public void onTickBehavior() {
        DailyRamCleanerConfig current = config;
        if (current == null || !current.enabled) {
            nextCheckAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextCheckAt) return;

        long checkIntervalMs = Math.max(15, current.checkIntervalSeconds) * 1_000L;
        nextCheckAt = now + checkIntervalMs;

        long memoryUsageMb;
        try {
            memoryUsageMb = darkbotApi.getMemoryUsage();
        } catch (RuntimeException unavailable) {
            return;
        }

        boolean inCombat = attack.isAttacking()
                || attack.hasTarget()
                || hero.getLocalTarget() != null;
        if (!DailyRamCleanerPolicy.shouldClean(memoryUsageMb,
                current.ramThresholdMb, current.avoidCombat, inCombat)) {
            if (current.avoidCombat && inCombat && memoryUsageMb >= current.ramThresholdMb) {
                nextCheckAt = Math.min(nextCheckAt, now + MIN_RETRY_AFTER_COMBAT_MS);
            }
            return;
        }

        try {
            if (current.clearGameCache
                    && darkbotApi.hasCapability(Capability.HANDLER_CLEAR_CACHE)) {
                darkbotApi.clearCache(".*");
            }

            if (darkbotApi.hasCapability(Capability.HANDLER_CLEAR_RAM)) {
                darkbotApi.emptyWorkingSet();
            }
        } catch (RuntimeException cleanupFailure) {
            // Best-effort: DarkBot may temporarily reject cleanup while the
            // game client is reconnecting.
        }
    }
}
