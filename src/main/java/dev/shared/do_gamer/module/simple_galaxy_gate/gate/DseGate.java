package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.util.Timer;

public final class DseGate extends GateHandler {
    private static final String MAIN_GUI = "expedition";
    private static final String SHIP_HANGAR_GUI = "expedition_shiphangar";
    private static final String SHIP_WARP_GUI = "expedition_shipwarp";
    private static final int PORTAL_TYPE_ID = 300; // Portal type ID for DSE
    private static final int COMMAND_HALL_MAP_ID = 473; // Map ID for Command Center
    private static final double REPAIR_RADIUS = 3_000.0;
    private Timer jumpTimer = Timer.get(20_000L);
    private Timer loadDelay = Timer.get(3_000L);
    private Timer petDisableDelay = Timer.get(7_000L);

    public DseGate() {
        this.npcMap.put("-={ Gygerim Overlord }=-", new NpcParam(590.0, -95));
        this.npcMap.put("-=[ Convict ]=-", new NpcParam(590.0, -95));
        this.npcMap.put("<=< Boss Kucurbium >=>", new NpcParam(630.0, -95));
        this.npcMap.put("..::{ Boss Lordakium }::...", new NpcParam(590.0, -95));
        this.npcMap.put("-=[ Emperor Sibelon ]=-", new NpcParam(630.0));
        this.npcMap.put("-=[ Transport Ship ]=-", new NpcParam(400.0, 100, NpcFlag.NO_CIRCLE, NpcFlag.PASSIVE));
        this.npcMap.put("-=[ Command Center ]=-", new NpcParam(400.0, 100, NpcFlag.NO_CIRCLE, NpcFlag.PASSIVE));
        this.defaultNpcParam = new NpcParam(580.0);
        this.repairRadius = REPAIR_RADIUS;
        this.approachToCenter = false;
        this.jumpToNextMap = false;
        this.useGuardableNpcAsSearchLocation = true;
        this.showCompletedGates = false;
    }

    @Override
    public boolean prepareTickModule() {
        // Handle GUI interaction or traveling to gate
        if (this.handleTravelToGate(PORTAL_TYPE_ID)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            this.reset();
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
        this.jumpTimer.disarm();
        this.loadDelay.disarm();
        this.petDisableDelay.disarm();
        this.resetCachedGuardableNpc();
    }

    @Override
    public GameMap getMapForTravel() {
        return this.getFactionMapForTravel(1); // travel to map x-1
    }

    /**
     * Checks if the given NPC has the name of the guardable NPCs
     * (Transport Ship or Command Center).
     */
    @Override
    protected boolean npcHasGuardableName(Npc npc) {
        return this.nameEquals(npc, "-=[ Transport Ship ]=-")
                || this.nameEquals(npc, "-=[ Command Center ]=-");
    }

    /**
     * Checks if the given NPC has the name of the Missile-Storm,
     * which should be prioritized for killing.
     */
    private boolean npcHasMissileStormName(Npc npc) {
        return this.nameEquals(npc, "-=[ Missile-Storm ]=-");
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Kill first the Missile-Storm if present
        if (this.hasNearbyMissileStorm(npc)) {
            return KillDecision.NO;
        }
        // Never kill the guardable NPCs
        if (this.isGuardableNpc(npc)) {
            return KillDecision.NO;
        }

        // If a guardable NPC is under attack by another NPC, don't kill this NPC
        if (this.isGuardableNpcAttackedByOtherNpc(npc)) {
            return KillDecision.NO;
        }

        return super.shouldKillNpc(npc);
    }

    /**
     * Checks whether a guardable NPC is currently under attack by another NPC.
     */
    private boolean isGuardableNpcAttackedByOtherNpc(Npc npc) {
        Npc guardableNpc = this.getGuardableNpc();
        return guardableNpc != null
                && !npc.isAttacking(guardableNpc)
                && this.module.lootModule.getNpcs().stream()
                        .anyMatch(n -> !this.isGuardableNpc(n) && n.isAttacking(guardableNpc));
    }

    /**
     * Checks if there is a nearby Missile-Storm NPC.
     */
    private boolean hasNearbyMissileStorm(Npc npc) {
        return !this.npcHasMissileStormName(npc) && this.module.lootModule.getNpcs().stream()
                .anyMatch(n -> this.npcHasMissileStormName(n) && n.distanceTo(this.getNpcSearchLocation()) < 2_000.0);
    }

    @Override
    public double getTargetRadius(Lockable target) {
        double radius = super.getTargetRadius(target);
        if (target != null && this.getGuardableNpc() != null) {
            Npc npc = (Npc) target;
            if (!this.isGuardableNpc(npc) && !npc.isAttacking(this.module.hero)) {
                // Reduce radius for target not attacking hero when have guardable NPC
                return radius * 0.75;
            }
        }
        return radius;
    }

    @Override
    public boolean attackTickModule() {
        this.showBoxCount = false; // Hide box count when in gate

        // If we have a guardable NPC and it's the only one left, follow it.
        Npc guardableNpc = this.getGuardableNpc();
        if (guardableNpc != null && this.module.lootModule.getNpcs().size() == 1) {
            StateStore.request(StateStore.State.GUARDING);
            this.module.lootModule.moveToTarget(guardableNpc);
            return true;
        }
        return false; // Allow default logic to take over
    }

    @Override
    public boolean collectTickModule() {
        // Check if we're in Command Hall to handle box populate and gate reset logic
        if (this.module.starSystem.getCurrentMap().getId() == COMMAND_HALL_MAP_ID) {
            this.showBoxCount = true; // Show box count in Command Hall
            if (!this.loadDelay.isArmed()) {
                this.loadDelay.activate(); // Activate load delay timer when entering Command Hall
            }

            // Waiting conditions
            if (this.getVisibleGui(SHIP_HANGAR_GUI).isPresent()
                    || this.getVisibleGui(SHIP_WARP_GUI).isPresent()
                    || this.loadDelay.isActive()
                    || this.ensurePetDeactivated()
                    || (this.jumpTimer.isArmed() && this.jumpTimer.isInactive())) {
                StateStore.request(StateStore.State.WAITING_IN_GATE);
                return true;
            }

            // Start jusp timer to detect timeout
            if (StateStore.current() == StateStore.State.JUMPING && !this.jumpTimer.isArmed()) {
                this.jumpTimer.activate();
            }

            // Collect boxes if available, otherwise wait for jump or timeout
            if (this.module.collectorModule.collectIfAvailable()) {
                StateStore.request(StateStore.State.COLLECTING);
                return true;
            }

            this.module.jumpToNextMap();
            return true;
        }

        this.showBoxCount = false; // Hide box count when in gate
        this.reset();
        this.closeGui(MAIN_GUI);
        return false; // Allow default logic to take over
    }

    /**
     * Keep waiting while PET is still active or timer hasn't finished
     */
    private boolean ensurePetDeactivated() {
        // Disable PET to prevent it from becoming bugged
        this.module.petGearHelper.disable();

        // Activate delay timer to wait for PET to be disabled
        if (!this.petDisableDelay.isArmed()) {
            this.petDisableDelay.activate();
        }

        // Wait until PET is deactivated and the delay has elapsed
        return this.module.petGearHelper.isActive() && this.petDisableDelay.isActive();
    }
}
