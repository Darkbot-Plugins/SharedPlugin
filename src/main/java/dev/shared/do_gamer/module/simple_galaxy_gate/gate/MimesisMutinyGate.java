package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.LocalDateTime;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Defaults;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.util.Timer;

public final class MimesisMutinyGate extends GateHandler {
    private static final double RADIUS = 1_200.0;
    private static final double MAX_RADIUS = 1_900.0;
    private static final double REPAIR_RADIUS = 900.0;
    private static final long START_EARLY_SECONDS = 20L;
    private static final long PRE_START_WAIT_TIMEOUT = 60L;
    private final Timer stopTimer = Timer.get(10_000L);
    private boolean autoStart = false;
    private Npc cachedFreighter = null;

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
        this.safeRefreshInGate = false;
        this.skipFarTargets = false;
        this.fetchServerOffset = true;
        this.repairRadius = REPAIR_RADIUS;
    }

    private boolean isFreighter(Npc npc) {
        return this.nameEquals(npc, "-={EM Freighter}=-");
    }

    /**
     * Updates the map center coordinates and tolerance distance
     * based on the freighter's position.
     */
    private void updateMapCenter(Npc freighter) {
        if (freighter != null) {
            // Set map center to freighter's position and adjust tolerance distance
            this.mapCenterX = freighter.getX();
            this.mapCenterY = freighter.getY();
            this.toleranceDistance = RADIUS;
        } else {
            // If freighter is not found, reset to default map center and tolerance
            this.mapCenterX = Defaults.MAP_CENTER_X;
            this.mapCenterY = Defaults.MAP_CENTER_Y;
            this.toleranceDistance = Defaults.TOLERANCE_DISTANCE;
        }
    }

    /**
     * Finds the freighter NPC and updates map center/tolerance for guarding logic
     */
    private Npc getFreighter() {
        // Check if cached freighter is still valid
        if (this.cachedFreighter != null && this.module.lootModule.getNpcs().contains(this.cachedFreighter)) {
            // Update map center to cached freighter's position
            this.updateMapCenter(this.cachedFreighter);
            return this.cachedFreighter;
        }

        // Find a new freighter and update cache
        this.cachedFreighter = null;
        for (Npc npc : this.module.lootModule.getNpcs()) {
            if (this.isFreighter(npc)) {
                this.cachedFreighter = npc;
                break;
            }
        }

        this.updateMapCenter(this.cachedFreighter);
        return this.cachedFreighter;
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Only attack NPCs that are within a certain distance
        if (npc.distanceTo(this.getMapCenterX(), this.getMapCenterY()) > MAX_RADIUS) {
            return KillDecision.NO;
        }
        return KillDecision.YES;
    }

    @Override
    public boolean attackTickModule() {
        return this.isGuardingFreighter();
    }

    @Override
    public boolean collectTickModule() {
        return this.isGuardingFreighter();
    }

    /**
     * Handles guarding the freighter if it's the only NPC present.
     */
    private boolean isGuardingFreighter() {
        // If there are portal present, prioritize collecting boxes
        if (!this.module.entities.getPortals().isEmpty()) {
            return this.handleCollectWhenGuarding();
        }

        Npc freighter = this.getFreighter();
        if (freighter != null && this.npcsCount() == 1) {
            // Try to collect boxes while guarding
            if (this.handleCollectWhenGuarding()) {
                return true;
            }
            // If no boxes to collect, just guard the freighter
            StateStore.request(StateStore.State.GUARDING);
            this.module.lootModule.getAttacker().setTarget(freighter);
            this.module.lootModule.moveToNpc();
            return true;
        }

        return false; // Allow default logic to take over
    }

    /**
     * Handles collecting boxes while guarding the freighter,
     * if the box is within the allowed radius.
     */
    private boolean handleCollectWhenGuarding() {
        if (this.module.collectorModule.hasNoBox() || this.shouldIgnoreBox(this.module.collectorModule.currentBox)) {
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
     * Sets the module status to show the remaining time until the next gate opening
     */
    private void setWaitingStatus(long seconds) {
        String time = ServerTimeHelper.remainingTimeFormat(seconds);
        String status = String.format("start in %s", time);
        this.module.setStatusDetails(status);
    }

    @Override
    public boolean prepareTickModule() {
        // Check if we're in the correct map for the gate
        if (!this.isGateAccessibleFromCurrentMap()) {
            return false; // Allow default map navigation logic to take over
        }

        // Ensure server time offset is updated before calculating waiting time
        if (!ServerTimeHelper.offsetUpdated()) {
            this.module.setStatusDetails("fetching server time...");
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
                    this.handleStopping();
                }
            }
            return true;
        }

        return false; // Allow default preparation logic to take over
    }

    /**
     * Handles stopping the bot when waiting for the gate to open.
     */
    private void handleStopping() {
        // Activate the delay to allow bot refresh is needed
        if (!this.stopTimer.isArmed()) {
            this.stopTimer.activate();
            return;
        }
        if (this.stopTimer.isInactive()) {
            // Pause the bot until it's time to start preparing for the gate
            this.module.bot.setRunning(false);
            this.autoStart = true;
            this.stopTimer.disarm();
        }
    }

    @Override
    public void stoppedTickModule() {
        if (!this.autoStart) {
            return; // Only handle auto-start scenario
        }
        StateStore.request(StateStore.State.WAITING);
        long seconds = this.getWaitingDurationInSeconds();
        if (seconds <= PRE_START_WAIT_TIMEOUT) {
            // Time to start preparing for the gate, resume the bot
            this.module.setStatusDetails("preparing for gate...");
            this.module.bot.handleRefresh();
            this.module.bot.setRunning(true);
            this.autoStart = false;
            return;
        }
        this.setWaitingStatus(seconds);
    }

    @Override
    public void reset() {
        this.cachedFreighter = null;
    }
}
