package dev.shared.do_gamer.behaviour;

import dev.shared.do_gamer.config.AvoidDrawFireConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem.Special;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;

@Feature(name = "Avoid Draw Fire", description = "If has Draw Fire effect, then stops attacking and sets the PET into passive mode. May also use EMP-01.")
public class AvoidDrawFire implements Behavior, Configurable<AvoidDrawFireConfig> {
    private final HeroAPI hero;
    private final HeroItemsAPI items;
    private final PetAPI pet;
    private final AttackAPI attacker;
    private AvoidDrawFireConfig config;
    private static final long ATTACK_STOP_DURATION_MS = 5_000L;
    private static final int USE_RETRY_DELAY_MS = 250;

    public AvoidDrawFire(PluginAPI api) {
        this.hero = api.requireAPI(HeroAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<AvoidDrawFireConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        // Check if the hero has the Draw Fire effect
        if (this.hero.hasEffect(EntityEffect.DRAW_FIRE)) {
            // Stop attacking to reduce threat
            this.attacker.stopAttack();
            this.attacker.setBlacklisted(ATTACK_STOP_DURATION_MS);

            // Set PET to passive mode to avoid drawing fire
            if (this.pet.isEnabled() && this.pet.isActive()) {
                try {
                    this.pet.setGear(PetGear.PASSIVE);
                } catch (ItemNotEquippedException ignored) {
                    // Ignore exception
                }
            }

            // Optionally use EMP if configured
            if (this.config != null && this.config.useEmp && this.canUseEmp()) {
                this.items.useItem(Special.EMP_01, USE_RETRY_DELAY_MS,
                        ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED);
            }
        }
    }

    private boolean canUseEmp() {
        return this.items.getItem(Special.EMP_01, ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE)
                .filter(item -> item.getQuantity() > 0).isPresent();
    }

}
