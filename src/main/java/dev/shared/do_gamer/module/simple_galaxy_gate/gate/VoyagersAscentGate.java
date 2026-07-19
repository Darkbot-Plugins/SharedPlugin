package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.utils.DifficultySelectGateHandler;

public final class VoyagersAscentGate extends DifficultySelectGateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "singularitydrive_difficultyselect";
    private static final int PORTAL_TYPE_ID = 306; // Portal type ID for Voyagers Ascent
    private static final int GO_BUTTON_X = 250;
    private static final int GO_BUTTON_Y = 295;

    public VoyagersAscentGate() {
        super(DIFFICULTY_SELECT_GUI, PORTAL_TYPE_ID, GO_BUTTON_X, GO_BUTTON_Y);
        this.defaultNpcParam = new NpcParam(600.0);
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
    }
}
