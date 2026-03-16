package dev.shared.orbithelper.module.daily_quest.handlers.quest_executions;

import dev.shared.orbithelper.config.DailyQuestConfig;
import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.Location;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles KILL_PLAYERS quest execution.
 *
 * Flow:
 * 1. Scan for enemy players within DETECTION_RADIUS (2000)
 * 2. Move towards the closest enemy (different faction only)
 * 3. Switch to offensive config, select ammo, lock and attack
 * 4. Circle at CIRCLE_RADIUS (300) while attacking
 * 5. RSB mixing if enabled
 * 6. If attack fails for 5s, retry lock + fire ammo. If 20s, ignore for 90s.
 */
public class KillPlayersHandler {

    private final DailyQuestModule module;

    private static final double DETECTION_RADIUS = 2000;
    private static final double ATTACK_RANGE = 700;
    private static final double CIRCLE_RADIUS = 300;
    private static final double CIRCLE_SPEED = 0.03;

    // Attack timeout constants
    private static final long ATTACK_RETRY_MS = 5000; // 5s — retry lock if not attacking
    private static final long ATTACK_GIVEUP_MS = 20000; // 20s — give up and ignore
    private static final long IGNORE_DURATION_MS = 90000; // 90s — how long to ignore a player

    private double circleAngle = 0;
    private Player currentTarget = null;

    private boolean offensiveConfigSet = false;

    // Attack timeout tracking
    private long targetAcquiredTime = 0;
    private long lastRetryTime = 0;

    // Temporarily ignored players: playerId -> ignoreUntilTime
    private final Map<Integer, Long> ignoredPlayers = new HashMap<>();

    public KillPlayersHandler(DailyQuestModule module) {
        this.module = module;
    }

    // =========================================================================
    // Execution
    // =========================================================================

    public void execute() {
        cleanExpiredIgnores();
        validateTarget();
        checkAttackTimeout();

        if (currentTarget == null) {
            currentTarget = findNearestEnemy();
            if (currentTarget != null) {
                targetAcquiredTime = System.currentTimeMillis();
                lastRetryTime = 0;
                offensiveConfigSet = false;
            }
        }

        if (currentTarget == null) {
            roam();
            return;
        }

        if (distanceTo(currentTarget) > ATTACK_RANGE) {
            module.movement.moveTo(Location.of(
                    currentTarget.getLocationInfo().getCurrent().getX(),
                    currentTarget.getLocationInfo().getCurrent().getY()));
            return;
        }

        attackTarget();
    }

    private void validateTarget() {
        if (currentTarget != null && !currentTarget.isValid()) {
            releaseTarget();
        }
    }

    private void checkAttackTimeout() {
        if (currentTarget == null)
            return;
        long elapsed = System.currentTimeMillis() - targetAcquiredTime;
        if (elapsed > ATTACK_GIVEUP_MS && !module.attackApi.isAttacking()) {
            ignorePlayer(currentTarget);
            releaseTarget();
        } else if (elapsed > ATTACK_RETRY_MS && !module.attackApi.isAttacking()
                && System.currentTimeMillis() - lastRetryTime > ATTACK_RETRY_MS) {
            module.attackApi.stopAttack();
            module.attackApi.setTarget(null);
            fireAmmoToAssist();
            lastRetryTime = System.currentTimeMillis();
        }
    }

    private void roam() {
        ShipMode roamMode = module.getConfigRoam().getValue();
        if (roamMode != null && !module.hero.isInMode(roamMode)) {
            module.hero.setMode(roamMode);
        }
        offensiveConfigSet = false;
        if (!module.movement.isMoving()) {
            module.movement.moveRandom();
        }
    }

    private void attackTarget() {
        if (!offensiveConfigSet) {
            ShipMode offensiveMode = module.getConfigOffensive().getValue();
            if (offensiveMode != null) {
                module.hero.setMode(offensiveMode);
            }
            offensiveConfigSet = true;
        }
        handleAmmoSelection();
        module.attackApi.setTarget(currentTarget);
        if (!module.attackApi.isLocked()) {
            currentTarget.trySelect(true);
            fireAmmoToAssist();
        }
        circleTarget(currentTarget);
    }

    /**
     * Fires the primary ammo useItem — helps initiate/continue the attack.
     * The game sometimes needs a laser fire command to actually start shooting.
     */
    private void fireAmmoToAssist() {
        if (module.heroItems == null)
            return;
        DailyQuestConfig.KillPlayersSettings settings = module.config.questTypes.killPlayersSettings;
        SelectableItem.Laser laser = mapPrimaryAmmo(settings.primaryAmmo);
        module.heroItems.useItem(laser);
    }

    /**
     * Stops attacking and resets state.
     */
    public void cleanup() {
        if (module.attackApi.hasTarget()) {
            module.attackApi.stopAttack();
            module.attackApi.setTarget(null);
        }
        currentTarget = null;
        circleAngle = 0;
        lastAmmoSwitch = 0;
        targetAcquiredTime = 0;
        lastRetryTime = 0;
        offensiveConfigSet = false;
    }

    /**
     * Release current target without ignoring.
     */
    private void releaseTarget() {
        if (module.attackApi.hasTarget()) {
            module.attackApi.stopAttack();
            module.attackApi.setTarget(null);
        }
        currentTarget = null;
        targetAcquiredTime = 0;
        lastRetryTime = 0;
        offensiveConfigSet = false;
    }

    // =========================================================================
    // Ignore List Management
    // =========================================================================

    private void ignorePlayer(Player player) {
        if (player != null) {
            ignoredPlayers.put(player.getId(), System.currentTimeMillis() + IGNORE_DURATION_MS);
        }
    }

    private boolean isIgnored(Player player) {
        Long until = ignoredPlayers.get(player.getId());
        if (until == null)
            return false;
        if (System.currentTimeMillis() > until) {
            ignoredPlayers.remove(player.getId());
            return false;
        }
        return true;
    }

    private void cleanExpiredIgnores() {
        ignoredPlayers.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue());
    }

    // =========================================================================
    // Enemy Detection
    // =========================================================================

    /**
     * Checks if a player is a valid target:
     * - Must be enemy (isEnemy)
     * - Must NOT be same faction (same company = friendly, even if enemy clan)
     */
    private boolean isValidTarget(Player player) {
        EntityInfo info = player.getEntityInfo();
        if (info == null || !info.isEnemy())
            return false;
        EntityInfo heroInfo = module.hero.getEntityInfo();
        return heroInfo == null || info.getFaction() != heroInfo.getFaction();
    }

    /**
     * Finds the nearest valid enemy player within DETECTION_RADIUS.
     * Skips same-faction players and temporarily ignored players.
     */
    private Player findNearestEnemy() {
        Collection<? extends Player> players = module.entities.getPlayers();
        if (players == null || players.isEmpty())
            return null;

        Player closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Player player : players) {
            double dist = distanceTo(player);
            if (player.isValid() && isValidTarget(player) && !isIgnored(player)
                    && dist <= DETECTION_RADIUS && dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    // =========================================================================
    // Ammo Management (Direct pattern — check current laser, switch if needed)
    // =========================================================================

    private static final long AMMO_SWITCH_COOLDOWN_MS = 500;
    private long lastAmmoSwitch = 0;

    private void handleAmmoSelection() {
        if (module.heroItems == null)
            return;

        DailyQuestConfig.KillPlayersSettings settings = module.config.questTypes.killPlayersSettings;

        // Rate-limit ammo switches to avoid spam
        if (System.currentTimeMillis() - lastAmmoSwitch < AMMO_SWITCH_COOLDOWN_MS)
            return;

        // Fire RSB every tick when available — game handles cooldown internally
        if (settings.useRsb) {
            SelectableItem.Laser rsb = SelectableItem.Laser.RSB_75;
            boolean rsbReady = module.heroItems.getItem(rsb,
                    ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE).isPresent();

            if (rsbReady) {
                module.heroItems.useItem(rsb);
                lastAmmoSwitch = System.currentTimeMillis();
                return;
            }
        }

        // Select primary ammo — only switch if current laser differs
        SelectableItem.Laser desired = mapPrimaryAmmo(settings.primaryAmmo);
        SelectableItem currentAmmo = module.hero.getLaser();

        if (currentAmmo == null || !currentAmmo.equals(desired)) {
            module.heroItems.useItem(desired);
            lastAmmoSwitch = System.currentTimeMillis();
        }
    }

    private SelectableItem.Laser mapPrimaryAmmo(DailyQuestConfig.PrimaryAmmoMethod method) {
        switch (method) {
            case LCB_10:
                return SelectableItem.Laser.LCB_10;
            case MCB_25:
                return SelectableItem.Laser.MCB_25;
            case MCB_50:
                return SelectableItem.Laser.MCB_50;
            case UCB_100:
                return SelectableItem.Laser.UCB_100;
            case A_BL:
                return SelectableItem.Laser.A_BL;
            default:
                return SelectableItem.Laser.UCB_100;
        }
    }

    // =========================================================================
    // Movement — Circle Target
    // =========================================================================

    private void circleTarget(Player target) {
        double targetX = target.getLocationInfo().getCurrent().getX();
        double targetY = target.getLocationInfo().getCurrent().getY();

        circleAngle += CIRCLE_SPEED;
        if (circleAngle > Math.PI * 2)
            circleAngle -= Math.PI * 2;

        double moveX = targetX + Math.cos(circleAngle) * CIRCLE_RADIUS;
        double moveY = targetY + Math.sin(circleAngle) * CIRCLE_RADIUS;

        module.movement.moveTo(Location.of(moveX, moveY));
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private double distanceTo(Player player) {
        double heroX = module.hero.getLocationInfo().getCurrent().getX();
        double heroY = module.hero.getLocationInfo().getCurrent().getY();
        double dx = player.getLocationInfo().getCurrent().getX() - heroX;
        double dy = player.getLocationInfo().getCurrent().getY() - heroY;
        return Math.sqrt(dx * dx + dy * dy);
    }

}