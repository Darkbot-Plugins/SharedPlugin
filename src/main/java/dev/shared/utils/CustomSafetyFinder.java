package dev.shared.utils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.types.SafetyInfo;
import eu.darkbot.api.game.entities.BattleStation;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;

/**
 * Customized SafetyFinder: provide no-jump safety finding functionality.
 */
public class CustomSafetyFinder extends SafetyFinder {

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
            this.events = api.requireAPI(EventBrokerAPI.class);
            this.traveler = api.requireInstance(MapTraveler.class);
            this.portalJumper = new PortalJumper(api);
        }
    }

    /**
     * Creates an instance of SafetyFinderOnly.
     */
    public static CustomSafetyFinder create(PluginAPI api) {
        Init init = new Init(api);
        return new CustomSafetyFinder(init);
    }

    private CustomSafetyFinder(Init init) {
        super(init.hero, init.attacker, init.items, init.movement, init.starSystem,
                init.config, init.entities, init.traveler, init.portalJumper);

        // Register event listener
        init.events.registerListener(this);
    }

    /**
     * Executes a single tick of the no-jump safety routine.
     */
    public boolean noJumpTick() {
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

    @Override
    protected void moveToSafety(SafetyInfo safety) {
        super.moveToSafety(safety);
        // Stop attacking while moving to safety
        if (this.attacker.isAttacking() && !(this.escape == Escaping.SIGHT && this.stopRunningNoSight.getValue())) {
            this.attacker.stopAttack();
        }
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

        if (!this.noJumpTick()) {
            return false;
        }

        if (escapeState != Escaping.NONE) {
            return false;
        }

        this.setRefreshing(false);
        return true;
    }

    // ############################
    // Temporal fix for CBS issue
    // ############################

    @Override
    protected SafetyInfo getSafety() {
        List<SafetyInfo> safeties = config.getLegacy()
                .getSafeties(starSystem.getCurrentMap())
                .stream()
                .filter(s -> s.getEntity().map(Entity::isValid).orElse(false))
                .filter(this::canUse) // Use custom "canUse" method
                .map(s -> {
                    s.setDistance(Math.max(0, movement.getDistanceBetween(hero, s) - s.getRadius()));
                    return s;
                })
                .sorted(Comparator.comparingDouble(SafetyInfo::getDistance))
                .collect(Collectors.toList());
        if (safeties.isEmpty())
            return null;

        SafetyInfo best = safeties.get(0);

        if (escape == Escaping.REPAIR || escape == Escaping.REFRESH ||
                runClosestDistance.getValue() == 0 ||
                best.getDistance() < runClosestDistance.getValue())
            return best;

        List<Ship> enemies = ships.stream().filter(this::runFrom).collect(Collectors.toList());

        return safeties.stream()
                .filter(s -> s.getDistance() < enemies.stream()
                        .mapToDouble(enemy -> movement.getDistanceBetween(enemy, s))
                        .min().orElse(Double.POSITIVE_INFINITY))
                .findFirst()
                .orElse(best);
    }

    private boolean canUse(SafetyInfo safety) {
        if (safety.getRunMode() == SafetyInfo.RunMode.NEVER) {
            return false; // Always skip if run mode is NEVER
        }
        if (safety.getType() == SafetyInfo.Type.CBS) {
            return safety.getEntity()
                    .map(c -> c instanceof BattleStation.Hull ? (BattleStation.Hull) c : null)
                    .map(cbs -> !(cbs.getEntityInfo().isEnemy()
                            || (cbs.getHullId() == 0 && safety.getCbsMode() == SafetyInfo.CbsMode.ALLY)))
                    .orElse(false);
        }
        return safety.getRunMode().ordinal() <= this.escape.ordinal();
    }

}
