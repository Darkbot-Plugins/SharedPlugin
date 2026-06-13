package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.game.stats.Stats;
import eu.darkbot.shared.config.ProfileNames;

@Configuration(value="config.config_changer")
public class ConfigChangerConfig {
    @Option("config.config_changer.stop_at_boxes")
    @Number(min=0.0, max=10000.0)
    public double STOP_AT_BOXES = 0.0;
    @Option("config.config_changer.bot_profile")
    @Dropdown(options=ProfileNames.class)
    public String BOT_PROFILE = "";
    @Option("config.config_changer.booty_key")
    @Dropdown
    public Stats.BootyKey BOOTY_KEY;
}
