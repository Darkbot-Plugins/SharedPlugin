package dev.shared.do_gamer.utils;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;

/**
 * Customized SafetyFinder: Avoid portal jumps and move only to safe areas.
 */
public class SafetyFinderOnly extends SafetyFinder {
    private final MapTraveler traveler;

    /**
     * Initializes required APIs and utilities.
     */
    private static final class Init {
        private final HeroAPI hero;
        private final AttackAPI attacker;
        private final HeroItemsAPI items;
        private final MovementAPI movement;
        private final StarSystemAPI starSystem;
        private final ConfigAPI config;
        private final EntitiesAPI entities;
        private final PetAPI pet;
        private final EventBrokerAPI events;
        private final MapTraveler traveler;
        private final PortalJumper portalJumper;

        private Init(PluginAPI api) {
            this.hero = api.requireAPI(HeroAPI.class);
            this.attacker = api.requireAPI(AttackAPI.class);
            this.items = api.requireAPI(HeroItemsAPI.class);
            this.movement = api.requireAPI(MovementAPI.class);
            this.starSystem = api.requireAPI(StarSystemAPI.class);
            this.config = api.requireAPI(ConfigAPI.class);
            this.entities = api.requireAPI(EntitiesAPI.class);
            this.pet = api.requireAPI(PetAPI.class);
            this.events = api.requireAPI(EventBrokerAPI.class);

            this.portalJumper = new PortalJumper(api);
            this.traveler = new MapTraveler(this.pet, this.hero, this.starSystem, this.movement,
                    this.portalJumper, this.entities, this.events);
        }
    }

    /**
     * Creates an instance of SafetyFinderOnly.
     */
    public static SafetyFinderOnly create(PluginAPI api) {
        Init init = new Init(api);
        return new SafetyFinderOnly(init);
    }

    private SafetyFinderOnly(Init init) {
        super(init.hero, init.attacker, init.items, init.movement, init.starSystem,
                init.config, init.entities, init.traveler, init.portalJumper);
        this.traveler = init.traveler;

        // Register event listeners
        init.events.registerListener(this.traveler);
        init.events.registerListener(this);
    }

    public MapTraveler getTraveler() {
        return this.traveler;
    }

    @Override
    public boolean tick() {
        if (this.shouldTimeout()) {
            return false;
        }

        this.jumpState = JumpState.CURRENT_MAP;
        this.activeTick();

        if (this.safety == null) {
            this.escape = Escaping.NONE; // No valid safety to reach, mark as done to avoid loops
            return true;
        }

        if (this.escape == Escaping.NONE) {
            return true;
        }

        if (this.hero.getLocationInfo().distanceTo(this.safety) > this.safety.getRadius()) {
            this.moveToSafety(this.safety);
            return false;
        }

        this.escape = Escaping.WAITING;
        if (!this.refreshing && !this.hasEnemy()) {
            this.escape = Escaping.NONE;
            return true;
        }

        return false;
    }

    /**
     * Runs the no-jump safety routine until the ship reaches a safe spot.
     */
    public boolean reachSafety() {
        Escaping escapeState = this.state();
        if (escapeState != Escaping.WAITING && escapeState != Escaping.NONE) {
            this.setRefreshing(true);
        } else if (escapeState == Escaping.WAITING) {
            this.setRefreshing(false);
        }

        if (!this.tick()) {
            return false;
        }

        if (this.state() != Escaping.NONE) {
            return false;
        }

        this.setRefreshing(false);
        return true;
    }
}
