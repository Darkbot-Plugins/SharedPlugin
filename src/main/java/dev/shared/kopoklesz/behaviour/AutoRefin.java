package dev.shared.kopoklesz.behaviour;

import dev.shared.kopoklesz.config.AutoRefinConfig;
import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import com.github.manolo8.darkbot.core.api.Capability;
import com.github.manolo8.darkbot.core.manager.GuiManager;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.StatsAPI;

import java.util.Arrays;
import java.util.Comparator;
import eu.darkbot.util.Timer;

import static eu.darkbot.api.managers.OreAPI.*;

@Feature(name = "Auto refiner", description = "Automatically refine materials")
public class AutoRefin implements Behavior, Configurable<AutoRefinConfig> {

    private final Main main;
    private final OreAPI ores;
    private final GuiManager guiManager;
    private final IDarkBotAPI darkbotApi;
    private final StatsAPI stats;

    private AutoRefinConfig config;

    public AutoRefin(Main main,
                      OreAPI ores,
                      GuiManager guiManager,
                      IDarkBotAPI darkbotApi,
                      StatsAPI stats) {
        this.main = main;
        this.ores = ores;
        this.guiManager = guiManager;
        this.darkbotApi = darkbotApi;
        this.stats = stats;
    }

    // config file
    @Override
    public void setConfig(ConfigSetting<AutoRefinConfig> setting) {
        this.config = setting.getValue();
    }

    // behavior
    @Override
    public void onTickBehavior() {
        if (!isReadyForRefining()) return; // check if we can refine

        Arrays.stream(Ore.values())
                .filter(this::shouldRefineOre)  // filter allowed ores
                .max(Comparator.comparingInt((Ore o) -> maxRefine(o))) // get the ore with the highest possible refine amount
                .ifPresent(ore -> {
                    int maxRefine = maxRefine(ore);
                    if (maxRefine <= 0) return;
                    darkbotApi.refine(darkbotApi.readLong(guiManager.getAddress() + 0x78), ore, maxRefine); // refining
                });
    }

    /////////////////////////////////////////////////// helper methods ///////////////////////////////////////////////////
    private boolean isReadyForRefining() {
        if (config == null || !config.enabled) return false;

        if (!darkbotApi.hasCapability(Capability.DIRECT_REFINE)) return false;

        if (guiManager.getAddress() == 0) return false;

        if (getCargoPercent() <= config.triggerPercent) return false;

        return true;
    }

    private boolean shouldRefineOre(OreAPI.Ore ore) {
        if (config == null || config.ores == null) return false;
        switch (ore) {
            case PROMETID: return config.ores.prometid;
            case DURANIUM: return config.ores.duranium;
            case PROMERIUM: return config.ores.promerium;
            default: return false;
        }
    }

    private int maxRefine(OreAPI.Ore ore) {
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
