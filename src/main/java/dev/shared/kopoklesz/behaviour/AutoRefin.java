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

    private final OreAPI ores;
    private final GuiManager guiManager;
    private final IDarkBotAPI darkbotApi;
    private final StatsAPI stats;
    private final Main main;

    private AutoRefinConfig config;

    // Track cargo to prevent unnecessary API calls when unable to refine
    private int lastCargoAmount = -1;
    private boolean lastRefineAttemptFailed = false;

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

        int currentCargo = stats.getCargo();

        // If cargo hasn't changed since last failed refine attempt, skip to prevent
        // unnecessary API calls
        if (lastRefineAttemptFailed && currentCargo == lastCargoAmount) {
            return;
        }

        // Cache maxRefine values to avoid duplicate calculations
        Map<Ore, Integer> refineMap = Arrays.stream(Ore.values())
                .filter(this::shouldRefineOre)
                .collect(Collectors.toMap(
                        ore -> ore,
                        this::maxRefine));

        lastRefineAttemptFailed = true; // assume refine attempt will fail
        lastCargoAmount = currentCargo; // update last cargo amount

        // Find the ore with the highest refineable amount
        refineMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    darkbotApi.refine(
                            darkbotApi.readLong(guiManager.getAddress() + 0x78),
                            entry.getKey(),
                            entry.getValue());
                    lastRefineAttemptFailed = false; // refine attempt succeeded
                });
    }

    /////////////////////////////// helper methods ///////////////////////////////
    private boolean isReadyForRefining() {
        if (config == null || !config.enabled)
            return false;

        if (main.config.MISCELLANEOUS.AUTO_REFINE || !darkbotApi.hasCapability(Capability.DIRECT_REFINE))
            return false;

        if (guiManager.getAddress() == 0)
            return false;

        return (getCargoPercent() > config.triggerPercent);
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
