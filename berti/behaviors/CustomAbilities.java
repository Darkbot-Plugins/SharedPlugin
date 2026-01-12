package dev.shared.berti.behaviors;

import com.github.manolo8.darkbot.core.entities.Npc;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.NpcFlags;
import eu.darkbot.api.game.group.GroupMember;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import dev.shared.berti.behaviors.configs.CustomAbilitiesConfig;
import java.util.Arrays;

@Feature(name="Custom Abilities", description="Use abilities in an Optimal way")
public class CustomAbilities
implements Behavior,
Configurable<CustomAbilitiesConfig>,
NpcFlags<CustomAbilitiesConfig.AbilityFlags> {
    private final PluginAPI pluginAPI;
    private final HeroItemsAPI heroItemsAPI;
    private final HeroAPI heroAPI;
    private final GroupAPI groupAPI;
    private CustomAbilitiesConfig config;

    public CustomAbilities(PluginAPI pluginAPI) {
        this.pluginAPI = pluginAPI;
        this.groupAPI = (GroupAPI)pluginAPI.requireAPI(GroupAPI.class);
        this.heroItemsAPI = (HeroItemsAPI)pluginAPI.requireAPI(HeroItemsAPI.class);
        this.heroAPI = (HeroAPI)pluginAPI.requireAPI(HeroAPI.class);
    }

    public void setConfig(ConfigSetting<CustomAbilitiesConfig> config) {
        this.config = (CustomAbilitiesConfig)config.getValue();
    }

    public void onTickBehavior() {
        this.customAbility(this.config.ABILITY1, CustomAbilitiesConfig.AbilityFlags.ABILITY1);
        this.customAbility(this.config.ABILITY2, CustomAbilitiesConfig.AbilityFlags.ABILITY2);
        this.customAbility(this.config.ABILITY3, CustomAbilitiesConfig.AbilityFlags.ABILITY3);
        this.customAbility(this.config.ABILITY4, CustomAbilitiesConfig.AbilityFlags.ABILITY4);
        this.groupAbility(this.config.GROUP_ABILITY);
    }

    public void customAbility(CustomAbilitiesConfig.Ability config, CustomAbilitiesConfig.AbilityFlags abilityFlag) {
        if (!config.ACTIVATED || config.CONDITION == null || config.ABILITIES == null) {
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
            if (target != null && target.getInfo().hasExtraFlag((Enum)abilityFlag)) {
                this.heroItemsAPI.useItem((SelectableItem)config.ABILITIES, new ItemFlag[0]);
            }
        } else {
            this.heroItemsAPI.useItem((SelectableItem)config.ABILITIES, new ItemFlag[0]);
        }
    }

    public void groupAbility(CustomAbilitiesConfig.GroupAbility config) {
        Boolean plus;
        String shipName = this.heroAPI.getShipType();
        Boolean bl = plus = !shipName.contains("ship_solace") ? null : Boolean.valueOf(shipName.contains("-plus"));
        if (!config.ACTIVATED) {
            return;
        }
        for (GroupMember groupMember : this.groupAPI.getMembers()) {
            if (!this.groupAPI.hasGroup() || groupMember.getMapId() != this.heroAPI.getMap().getId() || !(groupMember.getMemberInfo().hpPercent() <= config.GROUP_HEAL) || plus == null) continue;
            this.heroItemsAPI.useItem((SelectableItem)(plus != false ? SelectableItem.Ability.SOLACE_PLUS_NANO_CLUSTER_REPAIRER_PLUS : SelectableItem.Ability.SOLACE), new ItemFlag[0]);
        }
    }
}
