package dev.shared.do_gamer.behaviour.solaris_inc;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.github.manolo8.darkbot.config.NpcExtraFlag;
import com.github.manolo8.darkbot.core.itf.NpcExtraProvider;

import dev.shared.do_gamer.config.SolarisIncConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.shared.modules.MapModule;

@Feature(name = "Solaris Ability", description = "Activate Solaris (also Paladin) ability when there are a certain number of NPCs nearby")
public class SolarisInc implements Behavior, Configurable<SolarisIncConfig>, NpcExtraProvider {
    private final AttackAPI attack;
    private final BotAPI bot;
    private final HeroAPI hero;
    private final EntitiesAPI entities;
    private final HeroItemsAPI items;
    private final MovementAPI movement;
    private final PetAPI pet;
    private SolarisIncConfig config;
    private long lastUseTime = 0; // Last use time of the ability
    private long lastStickyTime = 0; // Last time sticky was active
    private final Set<ShipAbility> supportedShips = new HashSet<>();
    private ShipAbility currentShip = null; // Current ship being used
    private static final int MIN_ABILITY_COOLDOWN_SECONDS = 30;

    public SolarisInc(PluginAPI api) {
        this.attack = api.requireAPI(AttackAPI.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.pet = api.requireAPI(PetAPI.class);

        // Define supported ships and their ability
        this.supportedShips.add(new ShipAbility("solaris", 10, CustomAbility.SOLARIS_INC));
        this.supportedShips.add(new ShipAbility("solaris-plus", 10, CustomAbility.SOLARIS_PLUS_INCINERATE_PLUS));
        this.supportedShips.add(new ShipAbility("paladin", 3, CustomAbility.PALADIN_RIPPER));
    }

    @Override
    public void onTickBehavior() {
        // Early exit if feature is disabled
        if (this.config == null || !this.config.enabled) {
            return;
        }
        // Keep inactive while traveling
        if (this.bot.getModule() instanceof MapModule) {
            return;
        }

        ShipAbility ship = this.getCurrentShip();
        if (ship != null) {
            this.activateInc();
        }
    }

    private void activateInc() {
        long currentTime = System.currentTimeMillis();
        long npcNumb = this.getNpcs().count();

        // Activate the Solaris ability if enough NPCs are nearby
        if (npcNumb >= this.config.npc.minNumb && !this.isCooldown() && this.useAbility()) {
            // Update last use time
            this.lastUseTime = currentTime;
            this.tryToUsePetKamikaze();
        }

        // Stick to NPCs if ability was recently used
        if (npcNumb >= 1 && this.isStick()) {
            this.lastStickyTime = currentTime;

            if (this.attack.hasTarget()) {
                // If the ship has a target, move to the target
                Npc npc = (Npc) this.attack.getTarget();
                this.moveToNpc(npc);
            } else {
                // If no target, find the closest NPC and move to it
                this.getNpcs().min((n1, n2) -> Double.compare(this.hero.distanceTo(n1), this.hero.distanceTo(n2)))
                        .ifPresent(this::moveToNpc);
            }
        }

        // Use Sticky config if recently sticky
        if (this.config.stick.useConfig && (currentTime - this.lastStickyTime) <= 2000L) {
            this.hero.setMode(this.config.stick.shipMode);
        }
    }

    // Get the current ship
    private ShipAbility getCurrentShip() {
        if (!this.valid(this.currentShip)) {
            this.currentShip = this.supportedShips.stream().filter(this::valid).findFirst().orElse(null);
        }
        return this.currentShip;
    }

    // Validate if the ship matches the current ship type
    private boolean valid(ShipAbility ship) {
        if (ship == null) {
            return false;
        }
        String shipType = this.hero.getShipType();
        return (shipType.equals("ship_" + ship.name) || shipType.startsWith("ship_" + ship.name + "_design"));
    }

    // Use ability if available
    private boolean useAbility() {
        ShipAbility ship = this.getCurrentShip();
        if (ship == null) {
            return false;
        }
        double wait = (double) this.config.other.minWait;
        CustomAbility ability = ship.ability;
        return this.items
                .useItem(ability, wait, ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED)
                .isSuccessful();
    }

    // Try to use Pet Kamikaze if available
    private void tryToUsePetKamikaze() {
        if (!this.config.other.usePetKamikaze || !this.pet.isEnabled() || !this.pet.isActive()) {
            return; // Feature disabled or PET inactive
        }
        double wait = (double) this.config.other.minWait;
        this.items.useItem(SelectableItem.Pet.G_KK1, wait, ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE,
                ItemFlag.NOT_SELECTED);
    }

    private Stream<Npc> getNpcs() {
        double heroX = this.hero.getX();
        double heroY = this.hero.getY();

        return this.entities.getNpcs().stream()
                .map(Npc.class::cast)
                .filter(npc -> {
                    if (npc.distanceTo(heroX, heroY) <= this.config.npc.maxDistance) {
                        if (this.config.npc.extraFlagOnly) {
                            return npc.getInfo().hasExtraFlag(ExtraNpcFlagsEnum.SOLARIS_ABILITY);
                        }
                        return true;
                    }
                    return false;
                });
    }

    private void moveToNpc(Npc npc) {
        // Stick to the NPC by moving towards its position
        this.movement.moveTo(npc.getX(), npc.getY());
    }

    private boolean isStick() {
        // Check if the ship should stick to NPCs
        return this.config.stick.minHp > 0.0 && this.isActive() && this.enoughHp();
    }

    private boolean isActive() {
        // The active time of ability
        ShipAbility ship = this.getCurrentShip();
        return ship != null && this.cooldown(ship.duration);
    }

    private boolean isCooldown() {
        // At least 30 second cooldown with boosters
        return this.cooldown(MIN_ABILITY_COOLDOWN_SECONDS);
    }

    private boolean cooldown(int seconds) {
        long time = seconds * 1000L;
        return (System.currentTimeMillis() - this.lastUseTime) <= time;
    }

    private boolean enoughHp() {
        // Check if the hero's HP is above the configured threshold
        return this.hero.getHealth().hpPercent() >= this.config.stick.minHp;
    }

    // Helper class to associate ship names with their ability
    private static class ShipAbility {
        public final String name;
        public final int duration;
        public final CustomAbility ability;

        public ShipAbility(String name, int duration, CustomAbility ability) {
            this.name = name;
            this.duration = duration;
            this.ability = ability;
        }
    }

    @Override
    public NpcExtraFlag[] values() {
        return ExtraNpcFlagsEnum.values();
    }

    @Override
    public void setConfig(ConfigSetting<SolarisIncConfig> config) {
        this.config = config.getValue();
    }
}