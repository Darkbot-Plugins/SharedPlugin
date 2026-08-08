package dev.shared.kopoklesz.behaviour;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import com.github.manolo8.darkbot.core.api.Capability;
import com.github.manolo8.darkbot.core.manager.GuiManager;

import dev.shared.kopoklesz.config.AutoRefinConfig;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.OreAPI.Ore;
import eu.darkbot.api.managers.StatsAPI;

@Feature(name = "Auto refiner", description = "Automatically refine materials")
public class AutoRefin implements Behavior, Configurable<AutoRefinConfig> {
    private static final long FAILED_RETRY_DELAY_NANOS = 3_000_000_000L;
    private static final long TRADE_WINDOW_ADDRESS_OFFSET = 0x78L;

    private final OreAPI ores;
    private final GuiManager guiManager;
    private final IDarkBotAPI darkbotApi;
    private final StatsAPI stats;
    private final Main main;

    private AutoRefinConfig config;

    // Track cargo to prevent unnecessary API calls when unable to refine
    private int lastCargoAmount = -1;
    private boolean lastAttemptNeedsRetry = false;
    private long lastRefineAttemptAtNanos = 0L;

    public AutoRefin(OreAPI ores,
            GuiManager guiManager,
            IDarkBotAPI darkbotApi,
            StatsAPI stats,
            Main main) {
        this.ores = ores;
        this.guiManager = guiManager;
        this.darkbotApi = darkbotApi;
        this.stats = stats;
        this.main = main;
    }

    // config file
    @Override
    public void setConfig(ConfigSetting<AutoRefinConfig> setting) {
        this.config = setting.getValue();
    }

    // behavior
    @Override
    public void onTickBehavior() {
        if (!isReadyForRefining())
            return; // check if we can refine

        if (getCargoPercent() < config.triggerPercent) {
            // Reset tracking variables when cargo is below trigger percent
            if (lastCargoAmount != -1) {
                lastCargoAmount = -1;
                lastAttemptNeedsRetry = false;
                lastRefineAttemptAtNanos = 0L;
            }
            return;
        }

        int currentCargo = stats.getCargo();
        long nowNanos = System.nanoTime();

        // If cargo hasn't changed since last attempt that needs a retry (no ore was
        // refined, either nothing was eligible or the attempt failed), skip to
        // prevent unnecessary API calls until the retry delay elapses
        if (lastAttemptNeedsRetry
                && currentCargo == lastCargoAmount
                && (nowNanos - lastRefineAttemptAtNanos) < FAILED_RETRY_DELAY_NANOS) {
            return;
        }

        // Cache maxRefine values to avoid duplicate calculations
        Map<Ore, Integer> refineMap = Arrays.stream(Ore.values())
                .filter(this::shouldRefineOre)
                .collect(Collectors.toMap(
                        ore -> ore,
                        this::maxRefine));

        lastAttemptNeedsRetry = true; // assume no ore will be refined this tick
        lastCargoAmount = currentCargo; // update last cargo amount
        lastRefineAttemptAtNanos = nowNanos; // delay next retry unless a refine succeeds

        // Find the ore with the highest refineable amount
        refineMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    try {
                        long guiAddress = guiManager.getAddress();
                        if (guiAddress == 0)
                            return;

                        long tradeWindowAddress = darkbotApi.readLong(guiAddress + TRADE_WINDOW_ADDRESS_OFFSET);
                        if (tradeWindowAddress == 0)
                            return;

                        darkbotApi.refine(tradeWindowAddress, entry.getKey(), entry.getValue());
                        lastAttemptNeedsRetry = false; // ore was refined successfully
                        lastRefineAttemptAtNanos = 0L;
                    } catch (RuntimeException ignored) {
                        // Keep bot alive on transient client/API states (for example while the user
                        // is manually interacting with upgrade windows). lastRefineAttemptAtNanos was
                        // already set above, so the next attempt is throttled by
                        // FAILED_RETRY_DELAY_NANOS instead of retrying every tick, avoiding hammering
                        // the API while the client is in this unstable state.
                        System.out.println("Auto refiner: refine attempt failed, will retry after a short delay.");
                    }
                });
    }

    /////////////////////////////// helper methods ///////////////////////////////
    private boolean isReadyForRefining() {
        if (config == null || !config.enabled)
            return false;

        if (main.config.MISCELLANEOUS.AUTO_REFINE || !darkbotApi.hasCapability(Capability.DIRECT_REFINE))
            return false;

        return (guiManager.getAddress() != 0);
    }

    private boolean shouldRefineOre(Ore ore) {
        if (config == null || config.ores == null)
            return false;
        switch (ore) {
            case PROMETID:
                return config.ores.prometid;
            case DURANIUM:
                return config.ores.duranium;
            case PROMERIUM:
                return config.ores.promerium;
            default:
                return false;
        }
    }

    private int maxRefine(Ore ore) {
        switch (ore) {
            case PROMETID:
                return Math.min(ores.getAmount(Ore.PROMETIUM) / 20, ores.getAmount(Ore.ENDURIUM) / 10);
            case DURANIUM:
                return Math.min(ores.getAmount(Ore.TERBIUM) / 20, ores.getAmount(Ore.ENDURIUM) / 10);
            case PROMERIUM:
                int availableXenomit = ores.getAmount(Ore.XENOMIT); // get available xenomit
                if (config.xenomitReserve > 0) {
                    availableXenomit = Math.max(0, availableXenomit - config.xenomitReserve);
                }
                return Math.min(
                        Math.min(ores.getAmount(Ore.PROMETID) / 10, ores.getAmount(Ore.DURANIUM) / 10),
                        availableXenomit);
            default:
                return 0;
        }
    }

    private double getCargoPercent() {
        int max = stats.getMaxCargo();
        return max > 0 ? (double) stats.getCargo() / max : 0.0;
    }
}
