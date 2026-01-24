package dev.shared.do_gamer.utils;

import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.shared.modules.TemporalModule;

/**
 * Utility class to detect if the current bot module is a TemporalModule.
 */
public final class TemporalModuleDetector {

    private TemporalModuleDetector() {
        // Prevent instantiation
    }

    /**
     * Checks if the current bot module is an instance of TemporalModule.
     */
    public static boolean isUsing(BotAPI bot) {
        if (bot == null) {
            return false;
        }
        return (bot.getModule() instanceof TemporalModule);
    }
}
