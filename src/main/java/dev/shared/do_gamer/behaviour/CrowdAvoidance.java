package dev.shared.do_gamer.behaviour;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.shared.do_gamer.config.CrowdAvoidanceConfig;
import dev.shared.do_gamer.utils.CaptchaBoxDetector;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.shared.modules.MapModule;

@Feature(name = "Crowd Avoidance", description = "Detects crowded areas around the ship and moves away from them.")
public class CrowdAvoidance implements Behavior, Configurable<CrowdAvoidanceConfig> {

    private final BotAPI bot;
    private final HeroAPI hero;
    private final EntitiesAPI entities;
    private final MovementAPI movement;
    private CrowdAvoidanceConfig config;
    private static final double MIN_DISTANCE_TO_PORTAL = 500.0;
    private static final double MIN_DISTANCE_TO_STATION = 1000.0;
    private static final double AVOIDANCE_DISTANCE = 1500.0;
    private static final double ADJUSTMENT_FACTOR = 3000.0;

    public CrowdAvoidance(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<CrowdAvoidanceConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        if (!this.isActive()) {
            return;
        }

        List<Ship> ships = this.getShips();
        if (ships.size() >= this.config.numb) {
            this.moveAway(ships);
        }
    }

    private boolean isActive() {
        // Keep inactive if collecting
        if (this.hero.hasEffect(EntityEffect.BOX_COLLECTING) || this.hero.hasEffect(EntityEffect.BOOTY_COLLECTING)) {
            return false;
        }

        // Keep inactive while traveling
        if (this.bot.getModule() instanceof MapModule) {
            return false;
        }

        // Keep inactive if near safe points
        if (this.entities.getPortals().stream().anyMatch(this::checkPortal)
                || this.entities.getStations().stream().anyMatch(this::checkStation)) {
            return false;
        }

        // Keep inactive if captcha boxes detected
        if (CaptchaBoxDetector.hasCaptchaBoxes(this.entities)) {
            return false;
        }

        return this.config.consider.npcs || this.config.consider.enemies || this.config.consider.allies;
    }

    private boolean checkPortal(Portal portal) {
        return this.checkSafePoint(portal, MIN_DISTANCE_TO_PORTAL);
    }

    private boolean checkStation(Station station) {
        return this.checkSafePoint(station, MIN_DISTANCE_TO_STATION);
    }

    private boolean checkSafePoint(Entity entity, double minDistance) {
        return entity.distanceTo(this.hero) <= minDistance;
    }

    private boolean checkRadius(Ship ship) {
        return ship.distanceTo(this.hero) <= this.config.radius;
    }

    private List<Ship> getShips() {
        List<Ship> ships = new ArrayList<>();

        if (this.config.consider.npcs) {
            // Collect NPC ships
            this.entities.getNpcs().stream().filter(this::checkRadius).forEach(ships::add);
        }

        if (this.config.consider.enemies && this.config.consider.allies) {
            // Collect any player ships
            this.entities.getPlayers().stream().filter(this::checkRadius).forEach(ships::add);
        } else if (this.config.consider.enemies) {
            // Collect only enemy player ships
            this.entities.getPlayers().stream()
                    .filter(player -> player.getEntityInfo().isEnemy() && this.checkRadius(player))
                    .forEach(ships::add);
        } else if (this.config.consider.allies) {
            // Collect only ally player ships
            this.entities.getPlayers().stream()
                    .filter(player -> !player.getEntityInfo().isEnemy() && this.checkRadius(player))
                    .forEach(ships::add);
        }

        return ships;
    }

    private void moveAway(List<Ship> ships) {
        // Find the closest ship
        Ship closest = ships.stream().min(Comparator.comparingDouble(this.hero::distanceTo)).orElse(null);
        if (closest == null) {
            return;
        }

        double angle = closest.angleTo(this.hero);
        double speed = (double) this.hero.getSpeed();
        double distance = (double) this.config.radius + AVOIDANCE_DISTANCE; // Desired distance to keep away

        double targetX = closest.getX() - Math.cos(angle) * distance;
        double targetY = closest.getY() - Math.sin(angle) * distance;

        double overshoot = speed - this.hero.distanceTo(targetX, targetY);
        if (overshoot > 0) {
            angle += overshoot / ADJUSTMENT_FACTOR; // Adjust angle slightly based on speed
            targetX = closest.getX() - Math.cos(angle) * distance;
            targetY = closest.getY() - Math.sin(angle) * distance;
        }

        this.movement.moveTo(targetX, targetY);
    }
}
