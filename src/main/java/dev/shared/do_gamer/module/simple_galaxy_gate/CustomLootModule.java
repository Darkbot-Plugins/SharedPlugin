package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.GateHandler;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.PercentRange;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.game.entities.Barrier;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.shared.modules.LootModule;

public final class CustomLootModule extends LootModule {

    protected final ConfigSetting<PercentRange> repairHpRange;
    protected final ConfigSetting<Double> repairRoamingHp;
    protected final ConfigSetting<Double> repairToShield;
    protected final ConfigSetting<ShipMode> repairMode;
    protected final ConfigSetting<Integer> collectRadius;
    protected final Collection<? extends Barrier> barriers;

    private CustomCollectorModule collector;
    private GateHandler gateHandler;
    private boolean repair = false;
    private boolean approachingCenter = false;

    private final KamikazeHandler kamikazeHandler;

    CustomLootModule(PluginAPI api) {
        super(api);

        EntitiesAPI entities = api.requireAPI(EntitiesAPI.class);
        this.barriers = entities.getBarriers();

        ConfigAPI configApi = api.requireAPI(ConfigAPI.class);
        this.repairHpRange = configApi.requireConfig("general.safety.repair_hp_range");
        this.repairRoamingHp = configApi.requireConfig("general.safety.repair_hp_no_npc");
        this.repairToShield = configApi.requireConfig("general.safety.repair_to_shield");
        this.repairMode = configApi.requireConfig("general.safety.repair");
        this.collectRadius = configApi.requireConfig("collect.radius");

        this.kamikazeHandler = new KamikazeHandler(this, api);
    }

    /**
     * Sets the collector module reference.
     */
    public void setCollector(CustomCollectorModule collector) {
        this.collector = collector;
    }

    /**
     * Sets the gate handler reference.
     */
    public void setGateHandler(GateHandler gateHandler) {
        this.gateHandler = gateHandler;
        this.kamikazeHandler.setGateHandler(gateHandler);
    }

    /**
     * Sets the module config reference.
     */
    public void setModuleConfig(SimpleGalaxyGateConfig config) {
        this.kamikazeHandler.setConfig(config);
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    /**
     * Gets the list of valid NPCs based on gate handler filtering logic.
     */
    public List<Npc> getNpcs() {
        if (this.npcs == null || this.gateHandler == null) {
            return Collections.emptyList();
        }
        List<Npc> npcs = new ArrayList<>(this.npcs);
        return this.gateHandler.getFilteredNpcs(npcs);
    }

    @Override
    protected boolean findTarget() {
        Locatable location = this.gateHandler.getNpcSearchLocation();
        this.attack.setTarget(this.closestNpc(location));
        return this.attack.hasTarget();
    }

    @Override
    public void onTickModule() {
        this.pet.setEnabled(true);
        if (this.kamikazeHandler.tick()) {
            return; // Skip rest of logic if handling kamikaze
        }
        this.repairHandler();

        // Specific gate attack logic
        if (this.gateHandler.attackTickModule()) {
            return;
        }
        if (this.findTarget()) {
            // Try to collect boxes while attacking
            if (this.collector.isNotWaiting()) {
                this.collector.findBox();
                Box box = this.collector.currentBox;
                Npc npc = this.attack.getTargetAs(Npc.class);
                if (box != null && box.isValid() && this.shouldCollectWhileAttacking(npc, box)) {
                    StateStore.request(StateStore.State.COLLECTING);
                    this.collector.tryCollectNearestBox();
                    if (this.isFarTarget(npc)) {
                        this.hero.setRoamMode(); // If target is far, switch to roam mode
                    }
                } else {
                    this.moveToAnSafePosition();
                }
            }
            // Attack target
            this.attack.tryLockAndAttack();
        }
    }

    /**
     * Determines if the bot should try to collect the box while attacking the NPC.
     */
    private boolean shouldCollectWhileAttacking(Npc npc, Box box) {
        double radius = this.collectRadius.getValue();
        return npc != null && !this.shouldIgnoreBox(npc, box)
                && (box.distanceTo(this.hero) <= radius || this.isFarTarget(npc));
    }

    /**
     * Determines if the bot should ignore the box.
     */
    private boolean shouldIgnoreBox(Npc npc, Box box) {
        return npc.getInfo().hasExtraFlag(NpcFlag.IGNORE_BOXES)
                || this.gateHandler.shouldIgnoreBox(box)
                || npc.distanceTo(box) < this.getRadius(npc);
    }

    /**
     * Determines if the target NPC is considered far from the hero.
     */
    private boolean isFarTarget(Npc npc) {
        return this.hero.distanceTo(npc) >= this.gateHandler.getFarTargetDistance();
    }

    @Override
    protected void setConfig(Locatable direction) {
        if (this.repair) {
            return; // Skip while repairing
        }
        Npc target = (Npc) this.attack.getTargetAs(Npc.class);
        if (target != null && target.isValid()) {
            if (this.hero.distanceTo(target) > this.gateHandler.getFarTargetDistance()) {
                this.hero.setRoamMode(); // If target is far, switch to roam mode
            } else {
                this.hero.setAttackMode(target);
            }
        } else {
            this.hero.setRoamMode();
        }
    }

    /**
     * Skips far target logic when low HP and others are better targets.
     */
    private boolean skipFarTarget(Npc target) {
        if (!this.gateHandler.isSkipFarTargets() || this.gateHandler.isStickToTarget(target)) {
            return false; // Skip disabled
        }

        double targetDist = target.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        double heroDist = this.hero.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        return targetDist > Maps.getToleranceDistance()
                && targetDist > heroDist
                && target.getHealth().hpPercent() <= 0.25
                && this.getNpcs().stream().anyMatch(npc -> this.isBetterTarget(npc, targetDist, target));
    }

    /**
     * Determines if the given NPC is a better target than the current one
     * based on HP, distance to center, and position.
     */
    private boolean isBetterTarget(Npc npc, double distance, Npc target) {
        return npc.getHealth().hpPercent() > 0.3
                && this.shouldKill(npc)
                && npc.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY()) < (distance - 800.0)
                && Math.signum(npc.getX() - Maps.getMapCenterX()) == Math.signum(target.getX() - Maps.getMapCenterX())
                && Math.signum(npc.getY() - Maps.getMapCenterY()) == Math.signum(target.getY() - Maps.getMapCenterY());
    }

    /**
     * Gets a comparator for NPCs based on priority, distance and HP percentage.
     */
    public Comparator<Npc> getNpcComparator(Locatable location) {
        return Comparator.<Npc>comparingInt(n -> n.getInfo().getPriority())
                .thenComparingDouble(n -> n.distanceTo(location))
                .thenComparingDouble(n -> n.getHealth().hpPercent());
    }

    @Override
    protected Npc closestNpc(Locatable location) {
        Npc target = this.attack.getTargetAs(Npc.class);
        Npc best = this.getNpcs().stream()
                .filter(this::shouldKill)
                .min(this.getNpcComparator(location))
                .orElse(null);

        if (target != null && target.isValid()) {
            // If current target is still the best, keep it
            if (best == null || Objects.equals(target, best)) {
                return target;
            }
            // Skip far target if needed and return best
            if (this.skipFarTarget(target)) {
                return best;
            }
            // Check if need to prioritize current target
            if (this.shouldKill(target) && this.shouldPreferCurrentTarget(target, best, location)) {
                return target;
            }
        }

        return best;
    }

    /**
     * Determines if the module should keep the current target
     * instead of switching to the best target.
     */
    private boolean shouldPreferCurrentTarget(Npc target, Npc best, Locatable location) {
        // Stick to target if enabled and it has higher or equal priority than best
        if (this.gateHandler.isStickToTarget(target)
                && target.getInfo().getPriority() <= best.getInfo().getPriority()) {
            return true;
        }

        // Prefer current target if it's attacking us and best is not significantly
        // better in terms of distance to location
        if (this.hero.isAttacking(target)) {
            double offset = 100.0;
            double targetDist = target.distanceTo(location);
            double bestDist = best.distanceTo(location);
            if (targetDist < (bestDist + offset)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the NPC has ISH effect.
     */
    private boolean hasIshEffect(Npc npc) {
        return npc.hasEffect(EntityEffect.NPC_ISH) || npc.hasEffect(EntityEffect.ISH);
    }

    @Override
    protected boolean shouldKill(Npc npc) {
        // Ignore in specials cases
        if (npc.getInfo().hasExtraFlag(NpcFlag.PASSIVE)
                || (this.hasIshEffect(npc) && this.getNpcs().stream().anyMatch(n -> !this.hasIshEffect(n)))
                || (npc.isBlacklisted() && this.getNpcs().stream().anyMatch(n -> !n.isBlacklisted()))) {
            return false;
        }
        // Use gate handler logic
        GateHandler.KillDecision gateDecision = this.gateHandler.shouldKillNpc(npc);
        if (gateDecision != GateHandler.KillDecision.DEFAULT) {
            return gateDecision == GateHandler.KillDecision.YES;
        }
        // Default behavior
        return super.shouldKill(npc) && (!this.onlyKillPreferred.getValue() || this.movement.isInPreferredZone(npc));
    }

    @Override
    protected boolean isAttackedByOthers(Npc npc) {
        return false; // Ignore being attacked by others
    }

    @Override
    protected double getRadius(Lockable target) {
        // While repairing, keep distance
        if (this.repair) {
            return this.gateHandler.getRepairRadius();
        }
        // If no target, use default radius
        if (target == null) {
            return 560.0;
        }

        // Use specific radius for gate targets if defined, otherwise default NPC radius
        double radius = this.gateHandler.getTargetRadius(target);

        Npc npc = (Npc) target;
        if (radius == 0.0) {
            // Default radius based on NPC info
            radius = npc.getInfo().getRadius();
        }

        // Stay closer to low HP NPCs if no others are nearby
        if (npc.getHealth().hpPercent() <= 0.25 && !npc.getInfo().hasExtraFlag(NpcFlag.AGGRESSIVE_FOLLOW)) {
            double distance = npc.distanceTo(this.hero) + 100.0; // Add small buffer to distance
            if (this.getNpcs().stream()
                    .filter(n -> !Objects.equals(n, npc))
                    .noneMatch(n -> n.distanceTo(this.hero) < distance)) {
                radius *= 0.75;
            }
        }

        return this.attack.modifyRadius(radius);
    }

    /**
     * Handles the repair logic.
     */
    private void repairHandler() {
        if (this.repair && this.doneRepairing()) {
            this.repair = false;
        } else if (!this.repair && this.needsRepairing()) {
            this.repair = true;
        }
    }

    /**
     * Determines if the hero needs repairing.
     */
    private boolean needsRepairing() {
        return this.hero.getHealth().hpPercent() < ((PercentRange) this.repairHpRange.getValue()).getMin()
                || this.hero.getHealth().hpPercent() < (Double) this.repairRoamingHp.getValue()
                        && (!this.attack.hasTarget() || this.attack.getTarget().getHealth().hpPercent() > 0.9);
    }

    /**
     * Determines if the hero is done repairing.
     */
    private boolean doneRepairing() {
        if (!this.hero.isInMode((ShipMode) this.repairMode.getValue())
                && (this.hero.getHealth().hpIncreasedIn(1_000) || this.hero.getHealth().hpPercent() == 1.0)
                && (this.hero.getHealth().shieldDecreasedIn(1_000) || this.hero.getHealth().shieldPercent() == 0.0)) {
            this.hero.setMode((ShipMode) this.repairMode.getValue());
        }

        return this.hero.getHealth().shieldPercent() >= (Double) this.repairToShield.getValue()
                && this.hero.setMode((ShipMode) this.repairMode.getValue())
                && this.hero.getHealth().hpPercent() >= ((PercentRange) this.repairHpRange.getValue()).getMax();
    }

    /**
     * Determines if need to change direction to approach the center of the map.
     */
    private boolean approachToCenter(Npc target) {
        if (target == null || !this.gateHandler.isApproachToCenter() || !this.barriers.isEmpty()) {
            return false; // No need to approach
        }
        double distanceHero = this.hero.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        double distanceTarget = target.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        double tolerance = Maps.getToleranceDistance();
        double buffer = 800;
        boolean closeEnough = distanceHero < (tolerance - buffer) && distanceTarget < (tolerance - buffer);
        boolean farEnough = distanceHero > tolerance && distanceTarget > tolerance;

        // Stop approaching center if close enough, start if far enough
        this.approachingCenter = this.approachingCenter ? !closeEnough : farEnough;

        if (!this.approachingCenter) {
            return false; // Not approaching center
        }

        // Calculate angle when approaching
        double angleHero = this.hero.angleTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        double angleTarget = target.angleTo(Maps.getMapCenterX(), Maps.getMapCenterY());
        double angleDiffDeg = Math.toDegrees(angleHero - angleTarget);

        if (Math.abs(angleDiffDeg) < 2.0) {
            this.backwards = angleDiffDeg < 0;
        }
        return true;
    }

    @Override
    protected Location getBestDir(Locatable targetLoc, double angle, double angleDiff, double distance) {
        Npc target = this.attack.getTargetAs(Npc.class);
        if (this.approachToCenter(target) || this.repair) {
            return Location.of(targetLoc, angle + angleDiff * (double) (this.backwards ? -1 : 1), distance);
        }
        return super.getBestDir(targetLoc, angle, angleDiff, distance);
    }

    @Override
    protected double score(Locatable loc) {
        double base = this.movement.canMove(loc) ? 0 : -1_000;
        double sum = 0.0;
        for (Npc npc : this.getNpcs()) {
            if (this.attack.getTarget() == npc) {
                continue;
            }
            sum += Math.max(0.0, npc.getInfo().getRadius() - npc.distanceTo(loc));
        }
        return base - sum;
    }

    /**
     * Moves towards the target NPC while maintaining a safe distance.
     */
    public void moveToTarget(Lockable target) {
        this.attack.setTarget(target);
        this.moveToAnSafePosition();
    }

    // Make searchValidLocation accessible publicly
    @Override
    public void searchValidLocation(Location direction, Location targetLoc, double angle, double distance) {
        super.searchValidLocation(direction, targetLoc, angle, distance);
    }
}
