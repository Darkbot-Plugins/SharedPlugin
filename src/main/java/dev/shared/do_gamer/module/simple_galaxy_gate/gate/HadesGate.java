package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;

public class HadesGate extends GateHandler {

    // The Hades map is twice smaller than the other gates.
    private static final double MAP_CENTER_X = 5_240.0;
    private static final double MAP_CENTER_Y = 3_360.0;
    private static final double TOLERANCE_DISTANCE = 2_500.0;
    private static final double KAMIKAZE_OFFSET_X = -1_000.0;
    private static final double KAMIKAZE_OFFSET_Y = -500.0;

    public HadesGate() {
        this.mapCenterX = MAP_CENTER_X;
        this.mapCenterY = MAP_CENTER_Y;
        this.toleranceDistance = TOLERANCE_DISTANCE;
        this.kamikazeOffsetX = KAMIKAZE_OFFSET_X;
        this.kamikazeOffsetY = KAMIKAZE_OFFSET_Y;
        this.moveToCenter = false;
        this.approachToCenter = false;
        this.skipFarTargets = false;
    }

    @Override
    public boolean collectTickModule() {
        if (this.module.collectorModule.hasNoBox() && this.module.entities.getPortals().size() == 1) {
            this.moveToWaitingSpot();
            return true;
        }
        return false;
    }

    private void moveToWaitingSpot() {
        StateStore.request(StateStore.State.WAITING_IN_GATE);
        this.module.moveToPosition(Maps.getMapCenterX(), Maps.getMapCenterY() - 100.0, 50.0);
    }

}