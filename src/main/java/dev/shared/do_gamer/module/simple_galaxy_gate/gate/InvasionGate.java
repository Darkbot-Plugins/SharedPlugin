package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.game.other.GameMap;

public class InvasionGate extends GateHandler {
    private static final int PORTAL_TYPE_ID = 43; // Portal type ID for Invasion

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
        if (this.handleTravelToGate(PORTAL_TYPE_ID)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
    }
}
