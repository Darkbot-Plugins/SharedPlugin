package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Npc;
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
        return this.getFactionMapForTravel(5);
    }

    @Override
    public boolean prepareTickModule() {
        if (this.handleTravelToGate(PORTAL_TYPE_ID)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
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
