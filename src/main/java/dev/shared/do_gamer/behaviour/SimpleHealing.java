package dev.shared.do_gamer.behaviour;

import java.util.HashSet;
import java.util.Set;

import dev.shared.do_gamer.config.SimpleHealingConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem.Ability;
import eu.darkbot.api.game.other.Health;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;
import eu.darkbot.util.Timer;

@Feature(name = "Simple Healing", description = "Activate the ship's healing ability and use the PET healing gear.")
public class SimpleHealing implements Behavior, Configurable<SimpleHealingConfig> {
    private final HeroAPI hero;
    private final HeroItemsAPI items;
    private final PetAPI pet;
    private final AttackAPI attack;
    private SimpleHealingConfig config;
    private final Set<ShipAbility> supportedShips = new HashSet<>();
    private ShipAbility currentShip = null; // Current ship being used
    private static final long PET_COMBO_COOLDOWN_MS = 15_000L;
    private static final int ABILITY_USE_RETRY_DELAY_MS = 250;
    private static final double MAX_REPAIR_TARGET_DISTANCE = 750.0;
    private final Timer petComboCooldown = Timer.get(PET_COMBO_COOLDOWN_MS);

    public SimpleHealing(PluginAPI api) {
        this.hero = api.requireAPI(HeroAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.attack = api.requireAPI(AttackAPI.class);

        // Define supported ships and their abilities
        this.supportedShips.add(new ShipAbility("solace", Ability.SOLACE));
        this.supportedShips.add(new ShipAbility("solace-plus", Ability.SOLACE_PLUS_NANO_CLUSTER_REPAIRER_PLUS));
        this.supportedShips.add(new ShipAbility("orcus", Ability.ORCUS_ASSIMILATE));
        Set.of("aegis", "a-elite", "a-veteran", "hammerclaw", "hammerclaw-plus")
                .forEach(name -> this.supportedShips.add(new ShipAbility(name,
                        Ability.AEGIS_HP_REPAIR, Ability.AEGIS_SHIELD_REPAIR, Ability.AEGIS_REPAIR_POD)));
    }

    @Override
    public void onTickBehavior() {
        Health health = this.hero.getHealth();
        handleShipAbilities(health);
        handlePetGear(health);
    }

    private void handleShipAbilities(Health health) {
        if (!this.isEnabledShipAbility())
            return;
        // Try to use HP ability
        if (this.config.hp.enabled && this.checkHp(health)) {
            this.useAbility(this.currentShip.hp);
        }
        // Try to use shield ability if available
        if (this.config.shield.enabled && this.currentShip.shield != null
                && this.checkShield(health)) {
            this.useAbility(this.currentShip.shield);
        }
        // Try to use HP pod if available
        if (this.config.hpPod.enabled && this.currentShip.hpPod != null
                && this.checkHpPod(health)) {
            this.useAbility(this.currentShip.hpPod);
        }
    }

    private void handlePetGear(Health health) {
        if (!this.isEnabledPetGear())
            return;
        // Try to use Combo Repair
        if (this.pet.hasGear(PetGear.COMBO_REPAIR) && this.checkPetCombo(health)) {
            if (this.attack.hasTarget() && this.attack.isAttacking()) {
                this.attack.stopAttack(); // Stop attacking to avoid skip a gear
            }
            this.usePetCombo();
        }
    }

    // Check if any ship ability is enabled and valid for the current ship
    private boolean isEnabledShipAbility() {
        if (this.config.hp.enabled || this.config.shield.enabled || this.config.hpPod.enabled) {
            if (this.valid(this.currentShip)) {
                return true;
            }

            this.currentShip = this.supportedShips.stream().filter(this::valid).findFirst().orElse(null);
            return this.valid(this.currentShip);
        }
        return false;
    }

    // Check if PET gear is enabled
    private boolean isEnabledPetGear() {
        return this.config.petCombo.enabled && this.pet.isActive();
    }

    // Validate if the ship matches the current ship type
    private boolean valid(ShipAbility ship) {
        if (ship == null) {
            return false;
        }
        String shipType = this.hero.getShipType();
        return (shipType.equals("ship_" + ship.name) || shipType.startsWith("ship_" + ship.name + "_design"));
    }

    // Use the specified ability if available
    private void useAbility(Ability ability) {
        this.items.useItem(ability, ABILITY_USE_RETRY_DELAY_MS,
                ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED);
    }

    // Use the PET Combo Repair gear if available
    private void usePetCombo() {
        if (this.petComboCooldown.isActive()) {
            return; // has cooldown
        }

        try {
            // Use PET gear
            this.pet.setGear(PetGear.COMBO_REPAIR);
            // Activate cooldown
            if (this.pet.getGear() == PetGear.COMBO_REPAIR) {
                this.petComboCooldown.activate();
            }
        } catch (ItemNotEquippedException ignored) {
            // Item not equipped, not critical exception and should be ignored
        } catch (Exception e) {
            System.out.println("Failed to set PET gear to COMBO_REPAIR");
        }
    }

    // Check HP health
    private boolean checkHp(Health health) {
        return (health.hpPercent() <= this.config.hp.min && !this.isRepair());
    }

    // Check shield health
    private boolean checkShield(Health health) {
        return (health.shieldPercent() <= this.config.shield.min && !this.isRepair());
    }

    // Check HP pod health
    private boolean checkHpPod(Health health) {
        return (health.hpPercent() <= this.config.hpPod.min && !this.isRepair());
    }

    // Check PET combo health
    private boolean checkPetCombo(Health health) {
        return (health.hpPercent() <= this.config.petCombo.min && this.isRepair() && !this.hasTarget());
    }

    // Check if repair bot is active
    private boolean isRepair() {
        return this.hero.hasEffect(EntityEffect.REPAIR_BOT);
    }

    // Check if hero has target
    private boolean hasTarget() {
        return (this.attack.hasTarget() && this.attack.isAttacking()
                && this.hero.distanceTo(this.attack.getTarget()) <= MAX_REPAIR_TARGET_DISTANCE);
    }

    // Helper class to associate ship names with their abilities
    private static class ShipAbility {
        public final String name;
        public final Ability hp;
        public final Ability shield;
        public final Ability hpPod;

        public ShipAbility(String name, Ability hp, Ability shield, Ability hpPod) {
            this.name = name;
            this.hp = hp;
            this.shield = shield;
            this.hpPod = hpPod;
        }

        public ShipAbility(String name, Ability hp) {
            this(name, hp, null, null);
        }
    }

    @Override
    public void setConfig(ConfigSetting<SimpleHealingConfig> config) {
        this.config = config.getValue();
    }
}
