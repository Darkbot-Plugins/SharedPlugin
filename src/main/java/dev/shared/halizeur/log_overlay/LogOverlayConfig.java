package dev.shared.halizeur.log_overlay;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;

@Configuration("halizeur.log_overlay.config")
public class LogOverlayConfig {

    @Option("general.enabled")
    public boolean enabled = false;
}
