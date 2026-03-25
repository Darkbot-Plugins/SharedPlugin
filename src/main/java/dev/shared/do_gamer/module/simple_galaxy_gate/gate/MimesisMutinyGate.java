package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.LocalDateTime;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.objects.facades.EscortProxy;
import com.github.manolo8.darkbot.core.utils.ByteUtils;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.util.Timer;

public final class MimesisMutinyGate extends GateHandler {
    private static final double TOLERANCE_DISTANCE = 1_200.0;
    private static final double MAX_RADIUS = 1_800.0;
    private static final double REPAIR_RADIUS = 800.0;
    private static final double FAR_TARGET_DISTANCE = 1_200.0;
    private static final long START_EARLY_SECONDS = 20L;
    private static final long PRE_START_WAIT_TIMEOUT = 60L;
    private static final long EXTENDED_WAIT_THRESHOLD_SECONDS = 3_600L; // 1 hour
    private final Timer stopTimer = Timer.get();
    private boolean autoStart = false;
    private EscortProxy escort;

    public MimesisMutinyGate() {
        this.npcMap.put("-=[ Warhead ]=-", new NpcParam(560.0, -100));
        this.npcMap.put("-=[ Medic Mim3sis ]=-", new NpcParam(600.0, -90));
        this.npcMap.put("-=[ Obscured M1mes1s ]=-", new NpcParam(650.0, -80));
        this.npcMap.put("-=[ Mirror M1m3si5 ]=-", new NpcParam(650.0, -80));
        this.npcMap.put("-=[ Marker Mim3si5 ]=-", new NpcParam(650.0, -80));
        this.npcMap.put("-=[ Sniper M1mesi5 ]=-", new NpcParam(600.0, -70));
        this.npcMap.put("-=[ Piercing Mimesi5 ]=-", new NpcParam(600.0, -60));
        this.npcMap.put("-=[ Hounding Mim3si5 ]=-", new NpcParam(600.0, -50));
        this.npcMap.put("-=[ Inspirit M1mesi5 ]=-", new NpcParam(600.0, -30));
        this.npcMap.put("-=[ Hardy Mime5is ]=-", new NpcParam(600.0, -10));
        this.npcMap.put("-=[ Raider Mimes1s ]=-", new NpcParam(600.0, 0));
        this.npcMap.put("-=[ Assailant M1mesis ]=-", new NpcParam(600.0, 0));
        this.npcMap.put("-=[ Seeker Rocket ]=-", new NpcParam(560.0, 20));
        this.npcMap.put("-=[ Mim3si5 Turret ]=-", new NpcParam(560.0, 50, NpcFlag.NO_CIRCLE));
        this.npcMap.put("-={EM Freighter}=-", new NpcParam(560.0, 100, NpcFlag.NO_CIRCLE, NpcFlag.PASSIVE));

        this.moveToCenter = false;
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
        this.skipFarTargets = false;
        this.stickToTarget = true;
        this.fetchServerOffset = true;
        this.useGuardableNpcAsSearchLocation = true;
        this.toleranceDistance = TOLERANCE_DISTANCE;
        this.repairRadius = REPAIR_RADIUS;
        this.farTargetDistance = FAR_TARGET_DISTANCE;
        // Probably will never use Kamikaze in this gate,
        // but set offset to 0 just in case
        this.kamikazeOffsetX = 0.0;
        this.kamikazeOffsetY = 0.0;

        this.escort = Main.INSTANCE.facadeManager.escort;
    }

    /**
     * Checks if the NPC's name matches the freighter's name.
     */
    @Override
    protected boolean npcHasGuardableName(Npc npc) {
        return this.nameEquals(npc, "-={EM Freighter}=-");
    }

    /**
     * Updates the map center coordinates based on the freighter's position.
     */
    private void updateMapCenter(Npc guardableNpc) {
        this.mapCenterX = guardableNpc.getX();
        this.mapCenterY = guardableNpc.getY();
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Only attack NPCs that are within a certain distance
        if (npc.distanceTo(this.getMapCenterX(), this.getMapCenterY()) > MAX_RADIUS) {
            return KillDecision.NO;
        }
        // Never attack the freighter
        if (this.isGuardableNpc(npc)) {
            return KillDecision.NO;
        }
        return KillDecision.YES;
    }

    @Override
    public boolean attackTickModule() {
        return this.handleGateTick();
    }

    @Override
    public boolean collectTickModule() {
        return this.handleGateTick();
    }

    /**
     * Handles the gate logic for both attacking and collecting ticks
     */
    private boolean handleGateTick() {
        // If there are portal present, prioritize collecting boxes
        if (!this.module.entities.getPortals().isEmpty()) {
            this.module.hero.setRunMode();
            if (!this.handleCollectBoxes(false)) {
                this.module.jumpToNextMap(); // Exit the gate
            }
            return true;
        }

        Npc guardableNpc = this.getGuardableNpc();
        if (guardableNpc != null) {
            // Update map center to cached freighter's position
            this.updateMapCenter(guardableNpc);

            if (this.npcsCount() == 1) {
                // Try to collect boxes while guarding
                if (this.handleCollectBoxes(true)) {
                    return true;
                }
                // If no boxes to collect, just guard the freighter
                StateStore.request(StateStore.State.GUARDING);
                this.module.lootModule.getAttacker().setTarget(guardableNpc);
                this.module.lootModule.moveToAnSafePosition();
                return true;
            }
            // Update stick to target
            this.handleStickToTarget();
        } else {
            // If no freighter found, try to collect boxes if any
            return this.handleCollectBoxes(false);
        }

        return false;
    }

    /**
     * Handles collecting boxes if available
     */
    private boolean handleCollectBoxes(boolean hasFreighter) {
        if (this.module.collectorModule.hasNoBox()
                || (hasFreighter && this.shouldIgnoreBox(this.module.collectorModule.currentBox))) {
            return false;
        }

        StateStore.request(StateStore.State.COLLECTING);
        this.module.collectorModule.collectIfAvailable();
        return true;
    }

    /**
     * Counts the number of NPCs within the maximum radius from the map center
     */
    private int npcsCount() {
        int count = 0;
        for (Npc npc : this.module.lootModule.getNpcs()) {
            if (npc.distanceTo(this.getMapCenterX(), this.getMapCenterY()) <= MAX_RADIUS) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean shouldIgnoreBox(Box box) {
        return box == null || box.distanceTo(this.getMapCenterX(), this.getMapCenterY()) > MAX_RADIUS;
    }

    /**
     * Determines whether to stick to the current target
     */
    private void handleStickToTarget() {
        Npc target = this.module.lootModule.getAttacker().getTargetAs(Npc.class);
        if (target != null) {
            // Stick to target if has high HP, otherwise allow switching targets
            int maxHp = target.getHealth().getMaxHp();
            this.stickToTarget = (maxHp > 2_400_000);
        } else {
            // Default sticking to the target. For example, if an NPC uses a skill
            // to reset the targeting, then need to keep the same target.
            this.stickToTarget = true;
        }
    }

    /**
     * Calculates the waiting duration in seconds until next gate opening
     */
    private long getWaitingDurationInSeconds() {
        LocalDateTime now = ServerTimeHelper.currentDateTime();
        int hour = now.getHour();
        int minute = now.getMinute();
        // Gate is open for 5 minutes at the beginning of each hour from 10:00 to 23:59
        if (hour >= 10 && hour <= 23 && minute <= 4) {
            return 0;
        }
        // Calculate seconds until the next gate opening
        long seconds;
        if (hour >= 10 && hour < 23) {
            seconds = ServerTimeHelper.durationUntilTime(hour + 1, 0);
        } else {
            // Next gate opens at 10:00 the next day
            seconds = ServerTimeHelper.durationUntilTime(10, 0);
        }
        return Math.max(seconds - START_EARLY_SECONDS, 0); // Start early
    }

    /**
     * Reads the number of keys required for the next gate opening
     * Note: This method should be removed when fix added to Darkbot.
     */
    private double getKeys() {
        long data = Main.API.readLong(this.escort.getAddress() + 48) & ByteUtils.ATOM_MASK;
        // Note: this fix should be added to Darkbot
        // Original: API.readInt(API.readLong(data + 88) + 40)
        return Main.API.readDouble(Main.API.readLong(data + 88) + 56);
    }

    /**
     * Sets the module status to show the remaining time until the next gate opening
     */
    private void setWaitingStatus(long seconds) {
        String time = ServerTimeHelper.remainingTimeFormat(seconds);
        this.statusDetails = String.format("start in %s (%.0f keys)", time, this.getKeys());
    }

    @Override
    public boolean prepareTickModule() {
        // Check if we're in the correct map for the gate
        if (!this.isGateAccessibleFromCurrentMap()) {
            return false; // Allow default map navigation logic to take over
        }

        // Ensure server time offset is updated before calculating waiting time
        if (!ServerTimeHelper.offsetUpdated()) {
            this.statusDetails = "fetching server time...";
            return true; // Wait until server time offset is updated
        }

        // Calculate waiting time until the next gate opening
        long seconds = this.getWaitingDurationInSeconds();
        if (seconds > 0) {
            if (this.module.moveToRefinery()) {
                StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
            } else {
                StateStore.request(StateStore.State.WAITING);
                this.setWaitingStatus(seconds);
                if (seconds > PRE_START_WAIT_TIMEOUT) {
                    this.handleStopping(seconds);
                }
            }
            return true;
        }

        this.reset();
        return false; // Allow default preparation logic to take over
    }

    /**
     * Handles stopping the bot when waiting for the gate to open.
     */
    private void handleStopping(long seconds) {
        // Activate the delay to allow bot refresh is needed
        if (!this.stopTimer.isArmed()) {
            // Use 1m delay normally, 3m when wait exceeds extended threshold.
            long delay = seconds > EXTENDED_WAIT_THRESHOLD_SECONDS ? 180_000L : 60_000L;
            this.stopTimer.activate(delay);
            return;
        }
        if (this.stopTimer.isInactive()) {
            // Pause the bot until it's time to start preparing for the gate
            this.module.bot.setRunning(false);
            this.autoStart = true;
        }
    }

    @Override
    public void stoppedTickModule() {
        if (!this.autoStart) {
            return; // Only handle auto-start scenario
        }
        StateStore.request(StateStore.State.WAITING);
        long seconds = this.getWaitingDurationInSeconds();
        this.setWaitingStatus(seconds);
        if (seconds <= PRE_START_WAIT_TIMEOUT) {
            // Time to start preparing for the gate, resume the bot
            this.module.bot.handleRefresh();
            this.module.bot.setRunning(true);
            this.autoStart = false;
        }
        this.stopTimer.disarm();
    }

    @Override
    public void reset() {
        this.resetCachedGuardableNpc();
        if (!this.autoStart) {
            this.statusDetails = null; // reset status details
        }
    }
}
