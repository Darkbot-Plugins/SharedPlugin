package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.List;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.game.other.GameMap;

public class InvasionGate extends GateHandler {
    // Portal type IDs for Invasion (one of the three possible portals can appear)
    private static final List<Integer> PORTAL_TYPE_IDS = List.of(
            PortalType.INVASION_1.getId(),
            PortalType.INVASION_2.getId(),
            PortalType.INVASION_3.getId());

    public InvasionGate() {
        this.defaultNpcParam = new NpcParam(640.0);
        this.jumpToNextMap = false;
        this.moveToCenter = false;
        this.approachToCenter = false;
        this.skipFarTargets = false;
    }

    @Override
    public GameMap getMapForTravel() {
        return this.getFactionMapForTravel(5); // travel to map x-5
    }

    @Override
    public boolean prepareTickModule() {
        if (this.handleTravelToGate(PORTAL_TYPE_IDS)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
    }
}
