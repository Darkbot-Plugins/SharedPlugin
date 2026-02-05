package dev.shared.do_gamer.behaviour;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.shared.do_gamer.config.CrowdAvoidanceConfig;
import dev.shared.do_gamer.utils.PetGearHelper;
import dev.shared.utils.CaptchaBoxDetector;
import dev.shared.utils.TemporalModuleDetector;
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
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem.Special;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;

@Feature(name = "Crowd Avoidance", description = "Detects crowded areas around the ship and moves away from them.")
public class CrowdAvoidance implements Behavior, Configurable<CrowdAvoidanceConfig> {

    private final BotAPI bot;
    private final HeroAPI hero;
    private final EntitiesAPI entities;
    private final MovementAPI movement;
    private final HeroItemsAPI items;
    private final AttackAPI attacker;
    private final GroupAPI groupAPI;
    private final PetGearHelper petGearHelper;
    private CrowdAvoidanceConfig config;
    private static final double MIN_DISTANCE_TO_PORTAL = 500.0;
    private static final double MIN_DISTANCE_TO_STATION = 1000.0;
    private static final double AVOIDANCE_DISTANCE = 1500.0;
    private static final double ADJUSTMENT_FACTOR = 3000.0;
    private static final double BOXES_MARK_RADIUS = 500.0;
    private static final long ATTACK_STOP_DURATION_MS = 10_000L;
    private static final int USE_RETRY_DELAY_MS = 250;

    public CrowdAvoidance(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
        this.groupAPI = api.requireAPI(GroupAPI.class);
        this.petGearHelper = new PetGearHelper(api);
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
        if (ships.isEmpty()) {
            return; // No ships to consider
        }

        // Handle Draw Fire avoidance if enabled and affected
        if (this.config.avoidDrawFire.enabled && this.isDrawFireActive(ships)) {
            this.handleDrawFireAvoidance();
        }

        // Move away if crowded and not near safe points
        if (ships.size() >= this.config.numb && !this.isNearSafePoints()) {
            this.moveAway(ships);
        }
    }

    private boolean isActive() {
        // Keep inactive if collecting
        if (this.hero.hasEffect(EntityEffect.BOX_COLLECTING) || this.hero.hasEffect(EntityEffect.BOOTY_COLLECTING)) {
            return false;
        }

        // Keep inactive while traveling
        if (TemporalModuleDetector.using(this.bot).isMapModule()) {
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

    private boolean isNearSafePoints() {
        return this.entities.getPortals().stream().anyMatch(this::checkPortal)
                || this.entities.getStations().stream().anyMatch(this::checkStation);
    }

    private boolean checkRadius(Ship ship) {
        return ship.distanceTo(this.hero) <= this.config.radius;
    }

    private boolean isSameClan(Ship player) {
        int heroClanId = this.hero.getEntityInfo().getClanId();
        int playerClanId = player.getEntityInfo().getClanId();
        return heroClanId != 0 && heroClanId == playerClanId;
    }

    private boolean isSameGroup(Ship player) {
        return this.groupAPI.hasGroup() && this.groupAPI.getMember(player) != null;
    }

    private boolean isValidAlly(Ship player) {
        return !player.getEntityInfo().isEnemy() && !player.isBlacklisted() && this.checkRadius(player)
                && !(this.isSameClan(player) || this.isSameGroup(player));
    }

    private boolean isValidEnemy(Ship player) {
        return (player.getEntityInfo().isEnemy() || player.isBlacklisted()) && this.checkRadius(player)
                && !this.isSameGroup(player);
    }

    private List<Ship> getShips() {
        List<Ship> ships = new ArrayList<>();

        // Collect NPC ships
        if (this.config.consider.npcs) {
            this.entities.getNpcs().stream().filter(this::checkRadius).forEach(ships::add);
        }

        // Collect player ships
        if (this.config.consider.enemies || this.config.consider.allies) {
            this.entities.getPlayers().stream()
                    .filter(p -> (this.config.consider.enemies && this.isValidEnemy(p)) ||
                            (this.config.consider.allies && this.isValidAlly(p)))
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

        // Enable run mode for faster evasion during crowd avoidance
        if (this.config.other.runMode) {
            this.hero.setRunMode();
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

        this.markBoxesAsCollected(closest);
        this.movement.moveTo(targetX, targetY);
    }

    // Check if any ship has Draw Fire effect active
    private boolean isDrawFireActive(List<Ship> ships) {
        return ships.stream().anyMatch(ship -> ship.hasEffect(EntityEffect.DRAW_FIRE));
    }

    // Check if EMP can be used
    private boolean canUseEmp() {
        return this.items.getItem(Special.EMP_01, ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE)
                .filter(item -> item.getQuantity() > 0).isPresent();
    }

    private void handleDrawFireAvoidance() {
        // Stop attacking to reduce threat
        if (this.attacker.hasTarget()) {
            this.attacker.stopAttack();
            this.attacker.setBlacklisted(ATTACK_STOP_DURATION_MS);
            this.hero.setLocalTarget((Lockable) null);
        }

        // Set PET to passive mode to avoid drawing fire
        if (this.petGearHelper.isEnabled() && this.petGearHelper.isActive()) {
            this.petGearHelper.setPassive();
        }

        // Optionally use EMP if configured
        if (this.config.avoidDrawFire.useEmp && this.canUseEmp()) {
            this.items.useItem(Special.EMP_01, USE_RETRY_DELAY_MS,
                    ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED);
        }
    }

    /**
     * Marks boxes as collected to prevent interference during avoidance maneuvers
     */
    private void markBoxesAsCollected(Ship ship) {
        this.entities.getBoxes().stream()
                .filter(box -> box.distanceTo(ship) <= BOXES_MARK_RADIUS && !box.isCollected())
                .forEach(box -> {
                    box.setCollected();
                    // Re-mark every 3 retries to avoid instant attempts
                    // (see "getNextWait" method in "Box" entity)
                    if (box.getRetries() % 3 == 0) {
                        box.setCollected();
                    }
                });
    }
}
