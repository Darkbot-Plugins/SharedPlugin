package dev.shared.berti.behaviors;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.NpcFlags;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;
import dev.shared.berti.behaviors.configs.SimplePetConfig;
import eu.darkbot.api.config.annotations.Option;

import java.util.Arrays;

@Feature(name="Pet-Link", description="This behavior uses Pet HP-Link for you and automatically keeps your pet at full health")
public class SimplePet
implements Behavior,
Configurable<SimplePetConfig>,
NpcFlags<SimplePetConfig.ItemFlags> {
    private final PetAPI pet;
    private final HeroAPI heroAPI;
    private final HeroItemsAPI heroItemsAPI;
    private final ConfigSetting<PetGear> petMode;
    private final PluginAPI pluginAPI;
    private SimplePetConfig config;
    private static final int PET_LINK = 326;

    public SimplePet(PluginAPI api) {
        this.pet = (PetAPI)api.requireAPI(PetAPI.class);
        this.heroAPI = (HeroAPI)api.requireAPI(HeroAPI.class);
        this.heroItemsAPI = (HeroItemsAPI)api.requireAPI(HeroItemsAPI.class);
        this.pluginAPI = api;
        ConfigAPI configAPI = (ConfigAPI)api.requireAPI(ConfigAPI.class);
        this.petMode = configAPI.requireConfig("pet.module_id");
    }

    public void onTickBehavior() {
        Npc target;
        if (!this.config.ACTIVATED) {
            return;
        }
        if (!this.pet.isValid()) {
            return;
        }
        if (!this.pet.isEnabled()) {
            this.pet.setEnabled(true);
        }
        if (this.config.COMBO_REPAIR && this.pet.hasGear(PetGear.COMBO_REPAIR) && !this.pet.hasEffect(326) && this.heroAPI.getHealth().hpPercent() < 1.0 && this.heroAPI.getHealth().hpIncreasedIn(1000) && ((target = (Npc)this.heroAPI.getLocalTargetAs(Npc.class)) == null || this.heroAPI.distanceTo((Locatable)target) >= 850.0)) {
            try {
                this.pet.setGear(PetGear.COMBO_REPAIR);
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Combo Repair");
            }
            return;
        }
        if (this.pet.hasGear(PetGear.REPAIR) && !this.pet.hasEffect(326) && this.pet.getHealth().hpPercent() < 1.0 && ((target = (Npc)this.heroAPI.getLocalTargetAs(Npc.class)) == null || this.heroAPI.distanceTo((Locatable)target) >= 850.0)) {
            try {
                this.pet.setGear(PetGear.REPAIR);
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Pet Repair");
            }
            return;
        }
        if (this.config.CONDITION == null) {
            return;
        }
        if (this.pet.hasEffect(326) && this.pet.hasGear(PetGear.HP_LINK)) {
            try {
                this.pet.setGear(PetGear.HP_LINK);
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Pet HP Link");
            }
            return;
        }
        if (this.config.CONDITION.get(this.pluginAPI) == Condition.Result.ALLOW && this.pet.hasGear(PetGear.HP_LINK) && !this.pet.hasCooldown(PetGear.Cooldown.HP_LINK) && !this.pet.hasEffect(326)) {
            if (this.config.NPC_FLAG && ((target = (Npc)this.heroAPI.getLocalTargetAs(Npc.class)) == null || !target.getInfo().hasExtraFlag((Enum)SimplePetConfig.ItemFlags.HP_LINK_FLAG))) {
                return;
            }
            try {
                this.heroItemsAPI.useItem((SelectableItem)SelectableItem.Pet.G_HPL1, new ItemFlag[0]);
                this.pet.setGear(PetGear.HP_LINK);
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Pet HP Link");
            }
        }
        if (this.config.PET_ANTI_PUSH && !this.pet.hasEffect(326)) {
            target = (Npc)this.heroAPI.getLocalTargetAs(Npc.class);
            try {
                if (target != null) {
                    this.pet.setGear((PetGear)this.petMode.getValue());
                } else {
                    this.pet.setGear(PetGear.PASSIVE);
                }
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Guard mode");
            }
        }
    }

    @Override
    public void setConfig(ConfigSetting<SimplePetConfig> configSetting) {
        this.config = configSetting.getValue();
    }
}
