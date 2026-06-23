package dev.shared.berti.modules.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration(value="invokeconfig")
public class InvokeConfig {
    @Option("invokeconfig.map_switch_count")
    @Number(min=1.0, max=100.0, step=1.0)
    public int MAP_SWITCH_COUNT = 1;
}
