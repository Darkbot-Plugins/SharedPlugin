package dev.shared.berti.behaviors;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.StatsAPI;
import dev.shared.berti.behaviors.configs.ConfigChangerConfig;
import java.util.Arrays;

@Feature(name="Config Changer (by @berti1027)", description="Changes config once the specified amount of booty keys has been collected")
public class ConfigChanger
implements Behavior,
Configurable<ConfigChangerConfig> {
    private final StatsAPI statsAPI;
    private final ConfigAPI configAPI;
    private ConfigChangerConfig config;

    public ConfigChanger(PluginAPI pluginAPI) {
        this.statsAPI = (StatsAPI)pluginAPI.requireAPI(StatsAPI.class);
        this.configAPI = (ConfigAPI)pluginAPI.requireAPI(ConfigAPI.class);
    }

    public void BootyKeys() {
        if (this.config.BOOTY_KEY == null) {
            return;
        }
        double booty_key = this.statsAPI.getStat((StatsAPI.Key)this.config.BOOTY_KEY).getCurrent();
        if (booty_key <= this.config.STOP_AT_BOXES) {
            this.configAPI.setConfigProfile(this.config.BOT_PROFILE);
        }
    }

    public void setConfig(ConfigSetting<ConfigChangerConfig> config) {
        this.config = (ConfigChangerConfig)config.getValue();
    }

    public void onTickBehavior() {
        this.BootyKeys();
    }
}