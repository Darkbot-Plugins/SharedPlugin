package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import eu.darkbot.api.config.types.BoxInfo;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.util.Timer;

public class DseGate extends GateHandler {
    private static final String MAIN_GUI = "expedition";
    private static final String SHIP_HANGAR_GUI = "expedition_shiphangar";
    private static final String SHIP_WARP_GUI = "expedition_shipwarp";
    private static final int PORTAL_TYPE_ID = 300; // Portal type ID for DSE
    private static final int COMMAND_HALL_MAP_ID = 473; // Map ID for Command Center
    private static final double REPAIR_RADIUS = 3_000.0;
    private Timer jumpTimer = Timer.get(20_000L);

    public DseGate() {
        this.npcRadiusMap.put("-=[ Emperor Sibelon ]=-", 630.0);
        this.npcRadiusMap.put("-=[ Convict ]=-", 590.0);
        this.npcRadiusMap.put("<=< Boss Kucurbium >=>", 630.0);
        this.npcRadiusMap.put("..::{ Boss Lordakium }::...", 590.0);
        this.npcRadiusMap.put("\\\\ Find VII //", 580.0);
    }

    @Override
    public boolean prepareTickModule() {
        // Handle GUI interaction or traveling to gate
        if (this.handleTravelToGate()) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            this.reset();
            return true;
        }
        return false;
    }

    @Override
    public boolean isApproachToCenter() {
        return false;
    }

    @Override
    public void reset() {
        this.jumpTimer.disarm();
    }

    @Override
    public GameMap getMapForTravel() {
        if (!Maps.isGateOnCurrentMap(this.module.getConfig().gateId, this.module.starSystem)) {
            int faction = this.module.getHeroFractionIdx();
            if (faction == -1) {
                return null; // Unknown faction, cannot determine map
            }

            String map = String.format("%d-1", faction);
            return this.module.starSystem.getOrCreateMap(map);
        }
        return null; // Already on gate map, no need to travel
    }

    /**
     * Handles traveling to the gate portal if it's visible
     */
    private boolean handleTravelToGate() {
        // Check for portal and travel if found
        Portal portal = this.getPortalByTypeId(PORTAL_TYPE_ID);
        if (portal != null) {
            this.module.jumper.travelAndJump(portal);
            return true;
        }
        return false; // Not traveling, allow default logic
    }

    private boolean isGuardableNpc(Npc npc) {
        return this.nameEquals(npc, "-=[ Transport Ship ]=-")
                || this.nameEquals(npc, "-=[ Command Center ]=-");
    }

    private boolean isMissileStorm(Npc npc) {
        return this.nameEquals(npc, "-=[ Missile-Storm ]=-");
    }

    @Override
    public double getTargetRadius(Lockable target) {
        double radius = super.getTargetRadius(target);
        if (radius > 0) {
            return radius; // Return stored radius if already processed
        }

        Npc npc = (Npc) target;
        NpcInfo npcInfo = npc.getInfo();

        // Populate the radius.
        radius = 560.0;
        if (this.isGuardableNpc(npc)) {
            radius = 400.0;
            npcInfo.setPriority(100);
            npcInfo.setExtraFlag(NpcFlag.NO_CIRCLE, true);
            npcInfo.setExtraFlag(NpcFlag.PASSIVE, true);
        }
        npcInfo.setShouldKill(true);
        npcInfo.setRadius(radius);
        return radius;
    }

    @Override
    public double getRepairRadius() {
        return REPAIR_RADIUS;
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Kill first the Missile-Storm if present
        if (this.hasNearbyMissileStorm(npc, this.module.hero)) {
            return KillDecision.NO;
        }
        return super.shouldKillNpc(npc);
    }

    /**
     * Checks if there is a nearby Missile-Storm NPC.
     */
    private boolean hasNearbyMissileStorm(Npc npc, Locatable location) {
        return !this.isMissileStorm(npc) && this.module.lootModule.getNpcs().stream()
                .anyMatch(n -> this.isMissileStorm(n) && n.distanceTo(location) < 2_000.0);
    }

    /**
     * Finds the guardable NPC if present
     */
    private Npc getGuardableNpc() {
        return this.module.lootModule.getNpcs().stream()
                .filter(this::isGuardableNpc)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the closest NPC to the given location
     */
    private Npc findClosestNpcTo(Locatable location) {
        return this.module.lootModule.getNpcs().stream()
                .filter(n -> !this.isGuardableNpc(n) && !this.hasNearbyMissileStorm(n, location))
                .min(this.module.lootModule.getNpcComparator(location))
                .orElse(null);
    }

    @Override
    public boolean attackTickModule() {
        Npc guardableNpc = this.getGuardableNpc();
        if (guardableNpc != null) {
            // If only one NPC is present, follow the guardable NPC
            if (this.module.lootModule.getNpcs().size() == 1) {
                StateStore.request(StateStore.State.GUARDING);
                this.module.lootModule.getAttacker().setTarget(guardableNpc);
                this.module.lootModule.moveToNpc();
                return true;
            }

            // Attack the closest NPC to the guardable one
            Npc closestNpc = this.findClosestNpcTo(guardableNpc);
            if (closestNpc != null) {
                StateStore.request(StateStore.State.ATTACKING);
                this.module.lootModule.getAttacker().setTarget(closestNpc);
                this.module.lootModule.moveToNpc();
                this.module.lootModule.getAttacker().tryLockAndAttack();
                return true;
            }
        }
        return false; // Allow default logic to take over
    }

    @Override
    public boolean collectTickModule() {
        // Check if we're in Command Hall to handle box populate and gate reset logic
        if (this.module.starSystem.getCurrentMap().getId() == COMMAND_HALL_MAP_ID) {
            // Ensure boxes are marked for collection
            this.populateBoxes();

            // Wait manual selection of ship or reset gate
            if (this.getVisibleGui(SHIP_HANGAR_GUI).isPresent()
                    || this.getVisibleGui(SHIP_WARP_GUI).isPresent()
                    || (this.jumpTimer.isArmed() && this.jumpTimer.isInactive())) {
                StateStore.request(StateStore.State.WAITING_IN_GATE);
                return true;
            }

            // Start jusp timer to detect timeout
            if (StateStore.current() == StateStore.State.JUMPING && !this.jumpTimer.isArmed()) {
                this.jumpTimer.activate();
            }
            return false;
        }

        this.reset();
        this.closeGui(MAIN_GUI);
        return false; // Allow default logic to take over
    }

    /**
     * Populates the collect boxes in Commad Hall
     */
    private void populateBoxes() {
        if (this.module.collectorModule.count() > 0) {
            for (Box box : this.module.collectorModule.getBoxes()) {
                if (!box.getTypeName().endsWith("_ECHO_BOX")) {
                    continue; // Only handle DSE boxes
                }
                BoxInfo boxInfo = box.getInfo();
                if (!boxInfo.shouldCollect()) {
                    boxInfo.setShouldCollect(true);
                    boxInfo.setWaitTime(6500);
                }
            }
        }
    }
}
