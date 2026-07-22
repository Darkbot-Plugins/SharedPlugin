package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.LocalDateTime;
import java.util.Comparator;

import dev.shared.do_gamer.module.simple_galaxy_gate.utils.GateHandler;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.ScheduledGateHelper;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.StateStore;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.game.entities.Npc;

public final class TreacherousDomainGate extends GateHandler {
    private static final long START_EARLY_SECONDS = 10L;
    private static final long OPEN_WINDOW_DURATION_MINUTES = 10L;
    private final ScheduledGateHelper scheduleHelper = new ScheduledGateHelper();
    private static final int[] OPEN_WINDOWS = { 9, 11, 16, 19, 20, 22 };

    private enum NPC_MAP {
        BOSS("Fortress"),
        TOWER("Guardian");

        private final String name;

        NPC_MAP(String name) {
            this.name = name;
        }

        public String getName() {
            return "-=[ Dreadfyre " + this.name + " ]=-";
        }
    }

    public TreacherousDomainGate() {
        this.defaultNpcParam = new NpcParam(500.0);
        this.moveToCenter = false;
        this.approachToCenter = false;
        this.skipFarTargets = false;
        this.safeRefreshInGate = false;
        this.showCompletedGates = false;
        this.fetchServerOffset = true;
    }

    @Override
    public boolean attackTickModule() {
        // Prioritize attacking Tower first, then Boss
        if (this.handleTowerAttack() || this.handleBossAttack()) {
            return true;
        }
        this.statusDetails = null;
        return false;
    }

    @Override
    public boolean collectTickModule() {
        this.statusDetails = null;
        return (this.getWaitingDurationInSeconds() == 0 && this.module.collectorModule.hasNoBox());
    }

    /**
     * Handles the attack logic for a given NPC (Tower or Boss).
     */
    private boolean handleAttack(Npc npc, String label) {
        if (npc != null) {
            this.statusDetails = "Targeting " + label;
            StateStore.request(StateStore.State.ATTACKING);
            this.module.lootModule.moveToTarget(npc);
            this.module.lootModule.getAttacker().tryLockAndAttack();
            return true;
        }
        return false;
    }

    /**
     * Determines whether a Tower NPC is alive and should be targeted.
     */
    private boolean isAliveTowerNpc(Npc npc) {
        return this.nameEquals(npc, NPC_MAP.TOWER.getName()) && npc.getHealth().getHp() > 1; // Min HP 1
    }

    /**
     * Retrieves the first alive Tower NPC from the loot module.
     */
    protected Npc getTowerNpc() {
        return this.module.lootModule.getNpcs().stream()
                .filter(this::isAliveTowerNpc)
                .min(Comparator.comparingDouble(npc -> npc.distanceTo(this.module.hero)))
                .orElse(null); // Return null if no alive Tower is found
    }

    /**
     * Handles the attack on the Tower NPC.
     */
    private boolean handleTowerAttack() {
        return this.handleAttack(this.getTowerNpc(), "Tower");
    }

    /**
     * Determines whether the given NPC is a Boss.
     */
    private boolean isBossNpc(Npc npc) {
        return this.nameEquals(npc, NPC_MAP.BOSS.getName());
    }

    /**
     * Retrieves the Boss NPC from the loot module.
     */
    private Npc getBossNpc() {
        return this.module.lootModule.getNpcs().stream()
                .filter(this::isBossNpc)
                .findFirst()
                .orElse(null);
    }

    /**
     * Handles the attack on the Boss NPC.
     */
    private boolean handleBossAttack() {
        return this.handleAttack(this.getBossNpc(), "Boss");
    }

    @Override
    public boolean prepareTickModule() {
        return this.scheduleHelper.prepareTick(
                this.module,
                this::isGateAccessibleFromCurrentMap,
                this::isServerOffsetReady,
                this::getWaitingDurationInSeconds,
                this::setWaitingStatus,
                s -> 180_000L,
                this::reset);
    }

    @Override
    public void stoppedTickModule() {
        long seconds = this.getWaitingDurationInSeconds();
        this.scheduleHelper.stoppedTick(this.module, seconds, () -> this.setWaitingStatus(seconds));
    }

    /**
     * Calculates the waiting duration in seconds until the next gate opening.
     */
    private long getWaitingDurationInSeconds() {
        LocalDateTime now = ServerTimeHelper.currentDateTime();
        int hour = now.getHour();
        int minute = now.getMinute();

        int nextStartHour = OPEN_WINDOWS[0];
        for (int startHour : OPEN_WINDOWS) {
            long elapsedMinutes = (hour - startHour) * 60L + minute;
            if (elapsedMinutes >= 0 && elapsedMinutes < OPEN_WINDOW_DURATION_MINUTES) {
                return 0; // Gate is currently open
            }
            if (hour < startHour) {
                nextStartHour = startHour;
                break; // Found the next opening hour
            }
        }

        long secondsUntilNextStart = ServerTimeHelper.durationUntilTime(nextStartHour, 0);
        return Math.max(secondsUntilNextStart - START_EARLY_SECONDS, 0);
    }

    /**
     * Updates the module status details with the remaining waiting time.
     */
    private void setWaitingStatus(long seconds) {
        this.statusDetails = String.format("start in %s", ServerTimeHelper.remainingTimeFormat(seconds));
    }

    @Override
    public void reset() {
        if (!this.scheduleHelper.isAutoStart()) {
            this.statusDetails = null;
        }
    }
}
