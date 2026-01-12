package dev.shared.berti.behaviors;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.NpcFlags;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import dev.shared.berti.behaviors.configs.CustomTechsConfig;
import java.util.Arrays;

@Feature(name="Custom Techs", description="Use techs in an Optimal way")
public class CustomTechs
implements Behavior,
Configurable<CustomTechsConfig>,
NpcFlags<CustomTechsConfig.TechFlags> {
    private final PluginAPI pluginAPI;
    private final HeroItemsAPI heroItemsAPI;
    private final HeroAPI heroAPI;
    private CustomTechsConfig config;

    public CustomTechs(PluginAPI pluginAPI) {
        this.pluginAPI = pluginAPI;
        this.heroItemsAPI = (HeroItemsAPI)this.pluginAPI.requireAPI(HeroItemsAPI.class);
        this.heroAPI = (HeroAPI)this.pluginAPI.requireAPI(HeroAPI.class);
    }

    public void setConfig(ConfigSetting<CustomTechsConfig> config) {
        this.config = (CustomTechsConfig)config.getValue();
    }

    public void onTickBehavior() {
        this.customTech(this.config.TECH1, CustomTechsConfig.TechFlags.TECH1);
        this.customTech(this.config.TECH2, CustomTechsConfig.TechFlags.TECH2);
        this.customTech(this.config.TECH3, CustomTechsConfig.TechFlags.TECH3);
        this.customTech(this.config.TECH4, CustomTechsConfig.TechFlags.TECH4);
        this.customTech(this.config.TECH5, CustomTechsConfig.TechFlags.TECH5);
        this.customTech(this.config.TECH6, CustomTechsConfig.TechFlags.TECH6);
        this.customTech(this.config.TECH7, CustomTechsConfig.TechFlags.TECH7);
        this.customTech(this.config.TECH8, CustomTechsConfig.TechFlags.TECH8);
        this.customTech(this.config.TECH9, CustomTechsConfig.TechFlags.TECH9);
        this.customTech(this.config.TECH10, CustomTechsConfig.TechFlags.TECH10);
    }

    public void customTech(CustomTechsConfig.Tech config, CustomTechsConfig.TechFlags techFlag) {
        if (!config.ACTIVATED || config.CONDITION == null || config.TECHS == null) {
            return;
        }
        if (config.CONDITION.get(this.pluginAPI) != Condition.Result.ALLOW) {
            return;
        }
        if (config.NPC_FLAG) {
            if (!this.heroAPI.isAttacking()) {
                return;
            }
            Npc target = (Npc)this.heroAPI.getLocalTargetAs(Npc.class);
            if (target != null && target.getInfo().hasExtraFlag((Enum)techFlag)) {
                this.heroItemsAPI.useItem((SelectableItem)config.TECHS, new ItemFlag[0]);
            }
        } else {
            this.heroItemsAPI.useItem((SelectableItem)config.TECHS, new ItemFlag[0]);
        }
    }
}
