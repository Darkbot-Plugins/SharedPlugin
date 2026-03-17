package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Lockable;

public class InvasionGate extends GateHandler {
    private static final int PORTAL_TYPE_ID = 43; // Portal type ID for Invasion

    public InvasionGate() {
        // No specific initialization needed
    }

    @Override
    public boolean isJumpToNextMap() {
        return false;
    }

    @Override
    public boolean isMoveToCenter() {
        return false;
    }

    @Override
    public boolean isApproachToCenter() {
        return false;
    }

    @Override
    public boolean isSkipFarTargets() {
        return false;
    }

    @Override
    public GameMap getMapForTravel() {
        if (!Maps.isGateOnCurrentMap(this.module.getConfig().gateId, this.module.starSystem)) {
            int faction = this.getHeroFractionIdx();
            if (faction == -1) {
                return null; // Unknown faction, cannot determine map
            }
            String map = String.format("%d-5", faction);
            return this.module.starSystem.getOrCreateMap(map);
        }
        return null; // Already on gate map, no need to travel
    }

    @Override
    public boolean prepareTickModule() {
        if (this.handleTravelToGate()) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
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

    @Override
    public double getTargetRadius(Lockable target) {
        double radius = super.getTargetRadius(target);
        if (radius > 0) {
            return radius; // Return stored radius if already processed
        }

        Npc npc = (Npc) target;
        NpcInfo npcInfo = npc.getInfo();

        // Populate the radius.
        radius = 640.0;
        npcInfo.setShouldKill(true);
        npcInfo.setRadius(radius);
        return radius;
    }
}
