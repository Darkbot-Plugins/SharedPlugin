package dev.shared.utils;

import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.shared.modules.MapModule;
import eu.darkbot.shared.modules.TemporalModule;

/**
 * Utility class to detect if the current bot module is a TemporalModule.
 */
public final class TemporalModuleDetector {

    private TemporalModuleDetector() {
        // Prevent instantiation
    }

    /**
     * Creates a fluent checker for the current bot module.
     */
    public static Checker using(BotAPI bot) {
        return new Checker(bot);
    }

    /**
     * Fluent checker for TemporalModule-based module checks.
     */
    public static final class Checker {
        private final BotAPI bot;

        private Checker(BotAPI bot) {
            this.bot = bot;
        }

        /**
         * Checks if the current bot module is an instance of TemporalModule.
         */
        public boolean isTemporal() {
            if (this.bot == null) {
                return false;
            }
            return (this.bot.getModule() instanceof TemporalModule);
        }

        /**
         * Checks if the current bot module is an instance of TemporalModule
         * but not an instance of MapModule (traveling).
         */
        public boolean isTemporalNotMap() {
            if (this.bot == null) {
                return false;
            }
            return this.isTemporal() && !(this.bot.getModule() instanceof MapModule);
        }

        /**
         * Checks if the current bot module is an instance of MapModule (traveling).
         */
        public boolean isMapModule() {
            if (this.bot == null) {
                return false;
            }
            return this.bot.getModule() instanceof MapModule;
        }
    }
}
