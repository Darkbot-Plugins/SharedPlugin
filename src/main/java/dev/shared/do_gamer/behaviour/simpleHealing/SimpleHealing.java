package dev.shared.do_gamer.behaviour.simpleHealing;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final String CHECK_HP = "hp";
    private static final String CHECK_SHIELD = "shield";
    private static final String CHECK_HP_POD = "hpPod";
    private static final String CHECK_PET_COMBO = "petCombo";
    private final Timer petComboCooldown = Timer.get(PET_COMBO_COOLDOWN_MS);
    private static final Logger logger = Logger.getLogger(SimpleHealing.class.getName());

    public SimpleHealing(PluginAPI api) {
        this.hero = api.requireAPI(HeroAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.attack = api.requireAPI(AttackAPI.class);

        // Define supported ships and their abilities
        this.supportedShips.add(new ShipAbility("solace", Ability.SOLACE));
        this.supportedShips.add(new ShipAbility("solace-plus", Ability.SOLACE_PLUS_NANO_CLUSTER_REPAIRER_PLUS));
        this.supportedShips.add(new ShipAbility("orcus", Ability.ORCUS_ASSIMILATE));
        Set.of("aegis", "a-elite", "a-veteran", "hammerclaw", "hammerclaw-plus").forEach(name -> {
            this.supportedShips.add(new ShipAbility(name, Ability.AEGIS_HP_REPAIR, Ability.AEGIS_SHIELD_REPAIR,
                    Ability.AEGIS_REPAIR_POD));
        });
    }

    @Override
    public void onTickBehavior() {
        // Use ship abilities if enabled
        if (this.isEnabledShipAbility()) {
            // Try to use HP ability
            if (this.config.hp.ENABLED && this.checkHealth(CHECK_HP)) {
                this.useAbility(this.currentShip.hp);
            }

            // Try to use shield ability if available
            if (this.config.shield.ENABLED && this.currentShip.shield != null && this.checkHealth(CHECK_SHIELD)) {
                this.useAbility(this.currentShip.shield);
            }

            // Try to use HP pod if available
            if (this.config.hpPod.ENABLED && this.currentShip.hpPod != null && this.checkHealth(CHECK_HP_POD)) {
                this.useAbility(this.currentShip.hpPod);
            }
        }

        // Use PET gear if enabled
        if (this.isEnabledPetGear()) {
            // Try to use Combo Repair
            if (this.pet.hasGear(PetGear.COMBO_REPAIR) && this.checkHealth(CHECK_PET_COMBO)) {
                if (this.attack.hasTarget() && this.attack.isAttacking()) {
                    this.attack.stopAttack(); // Stop attacking to avoid skip a gear
                }
                this.usePetCombo();
            }
        }
    }

    // Check if any ship ability is enabled and valid for the current ship
    private boolean isEnabledShipAbility() {
        if (this.config.hp.ENABLED || this.config.shield.ENABLED || this.config.hpPod.ENABLED) {
            if (this.valid(this.currentShip)) {
                return true;
            }

            this.currentShip = this.supportedShips.stream().filter(s -> this.valid(s)).findFirst().orElse(null);
            return this.valid(this.currentShip);
        }
        return false;
    }

    // Check if PET gear is enabled
    private boolean isEnabledPetGear() {
        return this.config.petCombo.ENABLED && this.pet.isActive();
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
            logger.log(Level.WARNING, "Failed to set PET gear to COMBO_REPAIR", e);
        }
    }

    // Check health by type
    private boolean checkHealth(String type) {
        Health health = this.hero.getHealth();
        switch (type) {
            case CHECK_HP:
                return (health.hpPercent() <= this.config.hp.MIN_HP && !this.isRepair());
            case CHECK_SHIELD:
                return (health.shieldPercent() <= this.config.shield.MIN_SHIELD && !this.isRepair());
            case CHECK_HP_POD:
                return (health.hpPercent() <= this.config.hpPod.MIN_HP && !this.isRepair());
            case CHECK_PET_COMBO:
                return (health.hpPercent() <= this.config.petCombo.MIN_HP && this.isRepair() && !this.hasTarget());
            default:
                return false;
        }
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
