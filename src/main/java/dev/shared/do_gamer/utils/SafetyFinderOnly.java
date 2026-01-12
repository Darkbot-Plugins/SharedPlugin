package dev.shared.do_gamer.utils;

import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;

/**
 * Customized SafetyFinder: Avoid portal jumps and move only to safe areas.
 */
public class SafetyFinderOnly extends SafetyFinder {

    public SafetyFinderOnly(HeroAPI hero, AttackAPI attacker, HeroItemsAPI items, MovementAPI movement,
            StarSystemAPI starSystem, ConfigAPI config, EntitiesAPI entities,
            MapTraveler traveler, PortalJumper portalJumper) {
        super(hero, attacker, items, movement, starSystem, config, entities, traveler, portalJumper);
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
