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
import dev.shared.berti.behaviors.configs.SimpleItemsConfig;
import java.util.Arrays;

@Feature(name="Simple Items", description="Use items in an Optimal way")
public class SimpleItems
implements Behavior,
Configurable<SimpleItemsConfig>,
NpcFlags<SimpleItemsConfig.ItemFlags> {
    private final PluginAPI pluginAPI;
    private final HeroAPI heroAPI;
    private final HeroItemsAPI heroItemsAPI;
    private SimpleItemsConfig config;

    public SimpleItems(PluginAPI pluginApi) {
        this.pluginAPI = pluginApi;
        this.heroItemsAPI = (HeroItemsAPI)this.pluginAPI.requireAPI(HeroItemsAPI.class);
        this.heroAPI = (HeroAPI)this.pluginAPI.requireAPI(HeroAPI.class);
    }

    public void setConfig(ConfigSetting<SimpleItemsConfig> config) {
        this.config = (SimpleItemsConfig)config.getValue();
    }

    public void onTickBehavior() {
        this.simpleItem1(this.config.SIMPLEITEM1);
        this.simpleItem(this.config.SIMPLEITEM2, SimpleItemsConfig.ItemFlags.ITEM2);
        this.simpleItem(this.config.SIMPLEITEM3, SimpleItemsConfig.ItemFlags.ITEM3);
    }

    private void simpleItem1(SimpleItemsConfig.SimpleItem1 config) {
        if (config.ACTIVATED && this.heroAPI.isAttacking()) {
            this.heroItemsAPI.useItem((SelectableItem)SelectableItem.Tech.PRECISION_TARGETER, new ItemFlag[0]);
        }
    }

    private void simpleItem(SimpleItemsConfig.SimpleItem config, SimpleItemsConfig.ItemFlags itemFlag) {
        if (!config.ACTIVATED || config.CONDITION == null) {
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
            if (target != null && target.getInfo().hasExtraFlag((Enum)itemFlag)) {
                this.heroItemsAPI.useItem((SelectableItem)SelectableItem.Special.ISH_01, new ItemFlag[0]);
            }
        } else {
            this.heroItemsAPI.useItem((SelectableItem)SelectableItem.Special.ISH_01, new ItemFlag[0]);
        }
    }
}
