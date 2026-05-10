package dev.shared.halizeur.log_overlay;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;

@Configuration("halizeur.log_overlay.config")
public class LogOverlayConfig {

    @Option("halizeur.log_overlay.enabled")
    public boolean enabled = false;

    @Option("halizeur.log_overlay.categories")
    public Categories categories = new Categories();

    /**
     * Whitelist categories. A log message is shown when at least one
     * checked category matches. Predefined keyword lists live in
     * {@link LogOverlay} and stay in sync across game locales (FR + EN
     * substrings are bundled together).
     */
    public static class Categories {
        @Option("halizeur.log_overlay.cat.gains")
        public boolean gains = true;

        @Option("halizeur.log_overlay.cat.currencies")
        public boolean currencies = true;

        @Option("halizeur.log_overlay.cat.resources")
        public boolean resources = true;

        @Option("halizeur.log_overlay.cat.boosters")
        public boolean boosters = true;

        @Option("halizeur.log_overlay.cat.errors")
        public boolean errors = true;

        @Option("halizeur.log_overlay.cat.combat")
        public boolean combat = false;
    }
}
