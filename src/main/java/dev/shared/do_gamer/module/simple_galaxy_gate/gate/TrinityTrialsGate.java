package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import dev.shared.do_gamer.module.simple_galaxy_gate.utils.DifficultySelectGateHandler;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.util.Timer;

public final class TrinityTrialsGate extends DifficultySelectGateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "trinitytrials_difficultyselect";
    private static final int PORTAL_TYPE_ID = 304; // Portal type ID for Trinity Trials
    private static final int GO_BUTTON_X = 305;
    private static final int GO_BUTTON_Y = 345;
    private boolean trySelectSecondGate = false;
    private Timer selectTimer = Timer.get(1_000L);
    private int selectStep = 0;

    public TrinityTrialsGate() {
        super(DIFFICULTY_SELECT_GUI, PORTAL_TYPE_ID, GO_BUTTON_X, GO_BUTTON_Y);
        this.npcMap.put("..::{ Pyrospire }::..", new NpcParam(620.0));
        this.npcMap.put("..::{ Vinespire }::..", new NpcParam(620.0));
        this.defaultNpcParam = new NpcParam(580.0);
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
        this.fetchServerOffset = true;
    }

    @Override
    protected boolean onMaxClicksReached() {
        if (this.isSunday() && !this.trySelectSecondGate) {
            // Set flag to try selecting second gate and reset counters
            this.trySelectSecondGate = true;
            this.clickCount = 0;
            this.selectStep = 0;
            return true; // Try selecting second gate
        }
        return false;
    }

    @Override
    protected boolean beforeGoClick(Gui gui) {
        // Selecting second gate
        return this.trySelectSecondGate && this.selectGateType(gui, 2);
    }

    @Override
    public void reset() {
        // Reset states
        this.trySelectSecondGate = false;
        this.selectStep = 0;
        super.reset();
    }

    /**
     * Handles the gate type selection process
     */
    private boolean selectGateType(Gui gui, int index) {
        switch (this.selectStep) {
            case 0:
                gui.click(360, 225); // open select
                this.selectTimer.activate();
                this.selectStep = 1;
                return true;
            case 1:
                if (this.selectTimer.isInactive()) {
                    gui.click(360, 225 + 17 * index); // select gate type
                    this.selectTimer.activate();
                    this.selectStep = 2;
                }
                return true;
            default:
                // Wait for select timer to finish before allowing next action
                return this.selectTimer.isActive();
        }
    }

    private boolean isSunday() {
        LocalDateTime now = ServerTimeHelper.currentDateTime();
        return now.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
