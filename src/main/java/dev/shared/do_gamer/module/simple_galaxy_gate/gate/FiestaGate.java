package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.utils.DifficultySelectGateHandler;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.util.Timer;

public final class FiestaGate extends DifficultySelectGateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "difficultySelection";
    private static final int PORTAL_TYPE_ID = 303; // Portal type ID for Fiesta
    private static final int GO_BUTTON_X = 240;
    private static final int GO_BUTTON_Y = 276;

    // Coordinates for the gate level dropdown selector
    private static final int LEVEL_SELECT_X = 310;
    private static final int LEVEL_SELECT_Y = 100;
    private static final int LEVEL_ROW_HEIGHT = 19;

    // Timer used to wait between opening the selector and clicking the chosen level
    private final Timer selectTimer = Timer.get(1_000L);
    private int selectStep = 0;

    public FiestaGate() {
        super(DIFFICULTY_SELECT_GUI, PORTAL_TYPE_ID, GO_BUTTON_X, GO_BUTTON_Y);
        this.defaultNpcParam = new NpcParam(580.0);
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
        this.showCompletedGates = false;
    }

    @Override
    protected boolean beforeGoClick(Gui gui) {
        return this.selectGateLevel(gui);
    }

    @Override
    public void reset() {
        this.selectStep = 0;
        if (this.selectTimer.isArmed()) {
            this.selectTimer.disarm();
        }
        super.reset();
    }

    /**
     * Handles the gate level selection process.
     */
    private boolean selectGateLevel(Gui gui) {
        switch (this.selectStep) {
            case 0:
                // Open level selection menu
                gui.click(LEVEL_SELECT_X, LEVEL_SELECT_Y);
                this.selectTimer.activate();
                this.selectStep = 1;
                return true;
            case 1:
                if (this.selectTimer.isInactive()) {
                    // Click configured gate level row
                    int levelSelectHeight = LEVEL_ROW_HEIGHT * this.module.getConfig().fiesta.level.value;
                    gui.click(LEVEL_SELECT_X, LEVEL_SELECT_Y + levelSelectHeight);
                    this.selectTimer.activate();
                    this.selectStep = 2;
                }
                return true;
            default:
                // Wait until the selection click has finished processing
                return this.selectTimer.isActive();
        }
    }
}
