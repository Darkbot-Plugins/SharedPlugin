package dev.shared.haluzer.log_overlay;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;

@Configuration("haluzer.log_overlay.config")
public class LogOverlayConfig {

    @Option("haluzer.log_overlay.enabled")
    public boolean enabled = false;
}
