package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.LocalDateTime;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.objects.facades.EscortProxy;
import com.github.manolo8.darkbot.core.utils.ByteUtils;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.GateNpcFlag;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.GateHandler;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.ScheduledGateHelper;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.StateStore;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;

public final class MimesisMutinyGate extends GateHandler {
    private static final double TOLERANCE_DISTANCE = 1_200.0;
    private static final double MAX_RADIUS = 1_800.0;
    private static final double REPAIR_RADIUS = 900.0;
    private static final double FAR_TARGET_DISTANCE = 1_200.0;
    private static final long START_EARLY_SECONDS = 20L;
    private static final long EXTENDED_WAIT_THRESHOLD_SECONDS = 3_600L; // 1 hour
    private final ScheduledGateHelper scheduleHelper = new ScheduledGateHelper();
    private EscortProxy escort;

    public MimesisMutinyGate() {
        this.npcMap.put("-=[ Medic Mim3sis ]=-", new NpcParam(600.0, -90, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Obscured M1mes1s ]=-", new NpcParam(650.0, -80, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Mirror M1m3si5 ]=-", new NpcParam(650.0, -80, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Marker Mim3si5 ]=-", new NpcParam(650.0, -80, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Sniper M1mesi5 ]=-", new NpcParam(600.0, -70, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Piercing Mimesi5 ]=-", new NpcParam(600.0, -60, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Hounding Mim3si5 ]=-", new NpcParam(600.0, -50, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Inspirit M1mesi5 ]=-", new NpcParam(600.0, -30, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Hardy Mime5is ]=-", new NpcParam(600.0, -10, GateNpcFlag.STICK_TO_TARGET));
        this.npcMap.put("-=[ Raider Mimes1s ]=-", new NpcParam(600.0, 0));
        this.npcMap.put("-=[ Assailant M1mesis ]=-", new NpcParam(600.0, 0));
        this.npcMap.put("-=[ Warhead ]=-", new NpcParam(560.0, 10));
        this.npcMap.put("-=[ Seeker Rocket ]=-", new NpcParam(560.0, 20));
        this.npcMap.put("-=[ Mim3si5 Turret ]=-", new NpcParam(560.0, 50, NpcFlag.NO_CIRCLE));
        this.npcMap.put("-={EM Freighter}=-", new NpcParam(560.0, 100, NpcFlag.NO_CIRCLE, NpcFlag.PASSIVE));

        this.moveToCenter = false;
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
        this.skipFarTargets = false;
        this.showCompletedGates = false;
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
                this.module.lootModule.moveToTarget(guardableNpc);
                return true;
            }
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
     * Checks if the NPC name is Mirror M1m3si5
     */
    private boolean npcHasMirrorName(Npc npc) {
        return this.nameEquals(npc, "-=[ Mirror M1m3si5 ]=-");
    }

    @Override
    public boolean isStickToTarget(Npc target) {
        boolean isStick = super.isStickToTarget(target);
        if (isStick && this.npcHasMirrorName(target) && target.getHealth().getMaxHp() < 3_000_000) {
            return false; // Don't stick to Mirror clones
        }
        return isStick;
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
        return this.scheduleHelper.prepareTick(
                this.module,
                this::isGateAccessibleFromCurrentMap,
                this::isServerOffsetReady,
                this::getWaitingDurationInSeconds,
                this::setWaitingStatus,
                s -> s > EXTENDED_WAIT_THRESHOLD_SECONDS ? 180_000L : 60_000L,
                this::reset);
    }

    @Override
    public void stoppedTickModule() {
        long seconds = this.getWaitingDurationInSeconds();
        this.scheduleHelper.stoppedTick(this.module, seconds, () -> this.setWaitingStatus(seconds));
    }

    @Override
    public void reset() {
        this.resetCachedGuardableNpc();
        if (!this.scheduleHelper.isAutoStart()) {
            this.statusDetails = null; // reset status details
        }
    }
}
