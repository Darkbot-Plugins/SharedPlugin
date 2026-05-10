package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.regex.Pattern;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig.TrinityTrialsSettings;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig.TrinityTrialsSettings.TrinityDifficulty;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.util.Timer;

public final class TrinityTrialsGate extends GateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "trinitytrials_difficultyselect";
    private static final int PORTAL_TYPE_ID = 304; // Portal type ID for Trinity Trials
    private static final Pattern MAP_PATTERN = Pattern.compile("^[1-5]-[1-4]$");

    /** Click coordinates inside the difficulty-select GUI. */
    private static final int GATE_DROPDOWN_X = 360;
    private static final int GATE_DROPDOWN_Y = 225;
    private static final int DIFFICULTY_DROPDOWN_X = 360;
    private static final int DIFFICULTY_DROPDOWN_Y = 270;
    private static final int DROPDOWN_ITEM_STRIDE = 17;
    private static final int GO_BUTTON_X = 305;
    private static final int GO_BUTTON_Y = 345;

    /** Number of "Go" attempts before triggering fallback / giving up. */
    private static final int MAX_GO_CLICKS = 3;

    private final Timer clickTimer = Timer.get(10_000L);
    private final Timer selectTimer = Timer.get(1_000L);
    private int clickCount = 0;
    private boolean setFlags = false;

    /** Selection-step machine for the GUI:
     *   0 = open gate dropdown
     *   1 = click gate item
     *   2 = open difficulty dropdown
     *   3 = click difficulty item
     *   4 = ready to click "Go"
     */
    private int selectStep = 0;
    private boolean selectionComplete = false;

    /** Effective gate / difficulty for the current run — start from config
     *  values and degrade through fallback when a run fails to start. */
    private int activeGateIndex = 0;
    private TrinityDifficulty activeDifficulty = null;
    private boolean triedAlternateGate = false;

    /** Difficulty of the last successfully completed run, surfaced in
     *  the status bar so the user can see whether a fallback kicked in. */
    private TrinityDifficulty lastCompletedDifficulty = null;

    public TrinityTrialsGate() {
        this.npcMap.put("..::{ Pyrospire }::..", new NpcParam(620.0));
        this.npcMap.put("..::{ Vinespire }::..", new NpcParam(620.0));
        this.defaultNpcParam = new NpcParam(580.0);
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
        this.fetchServerOffset = true;
    }

    @Override
    public boolean prepareTickModule() {
        ensureRunPlanInitialized();
        // Max clicks reached
        if (this.clickCount >= MAX_GO_CLICKS) {
            // Try fallback (lower difficulty, then alternate gate) before giving up
            if (tryFallback()) return true;

            if (!this.setFlags) {
                this.setFlags = true; // Ensure flags are only set once
                this.module.setShouldMoveToRefinery(true);
                this.module.setCanSwitchProfile(true);
            }
            this.closeGui(DIFFICULTY_SELECT_GUI);
            return false; // Allow default logic to take over
        }
        // Handle GUI interaction or traveling to gate
        if (this.handleGui() || this.handleTravelToGate(PORTAL_TYPE_ID)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            updateStatus();
            return true;
        }
        return false;
    }

    @Override
    public boolean collectTickModule() {
        if (this.activeDifficulty != null) {
            this.lastCompletedDifficulty = this.activeDifficulty;
        }
        this.reset();
        updateStatus();
        this.closeGui(DIFFICULTY_SELECT_GUI);
        return false;
    }

    @Override
    public void reset() {
        // Reset states
        this.selectStep = 0;
        this.selectionComplete = false;
        this.clickCount = 0;
        this.setFlags = false;
        this.activeDifficulty = null;
        this.activeGateIndex = 0;
        this.triedAlternateGate = false;
        if (this.clickTimer.isArmed()) {
            this.clickTimer.disarm();
        }
    }

    @Override
    public GameMap getMapForTravel() {
        String currentMapName = this.module.starSystem.getCurrentMap().getShortName();
        // Check if current map x-4 (include PvP and Pirates)
        boolean toLowMap = MAP_PATTERN.matcher(currentMapName).matches();
        return this.getFactionMapForTravel(toLowMap ? 1 : 8); // travel to map x-1 or x-8
    }

    /** Initializes the active gate / difficulty from config on the first
     *  GUI tick of a new run. Subsequent ticks reuse those values until
     *  collect/reset, possibly stepped down by {@link #tryFallback()}. */
    private void ensureRunPlanInitialized() {
        if (this.activeDifficulty != null && this.activeGateIndex > 0) return;
        TrinityTrialsSettings cfg = this.module.getConfig().trinityTrials;
        this.activeDifficulty = cfg.difficulty != null ? cfg.difficulty : TrinityDifficulty.NORMAL;
        this.activeGateIndex = Math.max(1, cfg.gateChoice);
    }

    /**
     * Step down the run plan one notch when 3 "Go" clicks failed: first
     * try a lower difficulty, then switch to the alternate gate. Returns
     * true if a retry was set up; false if we ran out of fallbacks.
     */
    private boolean tryFallback() {
        if (!this.module.getConfig().trinityTrials.fallbackOnFailure) return false;

        TrinityDifficulty easier = this.activeDifficulty != null
                ? this.activeDifficulty.oneStepEasier()
                : null;
        if (easier != null) {
            this.activeDifficulty = easier;
            restartSelection();
            return true;
        }
        if (!this.triedAlternateGate) {
            this.triedAlternateGate = true;
            this.activeGateIndex = (this.activeGateIndex == 1) ? 2 : 1;
            this.activeDifficulty = this.module.getConfig().trinityTrials.difficulty;
            restartSelection();
            return true;
        }
        return false;
    }

    private void restartSelection() {
        this.selectStep = 0;
        this.selectionComplete = false;
        this.clickCount = 0;
        if (this.clickTimer.isArmed()) {
            this.clickTimer.disarm();
        }
    }

    /**
     * Drives the difficulty-select GUI: opens both dropdowns, clicks the
     * configured gate and difficulty, then clicks the "Go" button. The
     * selectStep state machine guarantees one click per tick with a
     * 1-second pause between clicks for the UI to settle.
     */
    private boolean handleGui() {
        return this.getVisibleGui(DIFFICULTY_SELECT_GUI).map(gui -> {
            // Initial timer, wait for preloading the GUI
            if (!this.clickTimer.isArmed()) {
                this.clickTimer.activate(3_000L);
                return true;
            }
            // Selecting gate + difficulty
            if (!this.selectionComplete) {
                runSelectionStep(gui);
                return true;
            }
            // Click "Go" button if timer allows
            if (this.clickTimer.isInactive()) {
                gui.click(GO_BUTTON_X, GO_BUTTON_Y);
                this.clickTimer.activate();
                this.clickCount++;
            }
            return true;
        }).orElse(false);
    }

    private void runSelectionStep(Gui gui) {
        switch (this.selectStep) {
            case 0:
                gui.click(GATE_DROPDOWN_X, GATE_DROPDOWN_Y); // open gate select
                this.selectTimer.activate();
                this.selectStep = 1;
                return;
            case 1:
                if (this.selectTimer.isInactive()) {
                    gui.click(GATE_DROPDOWN_X,
                            GATE_DROPDOWN_Y + DROPDOWN_ITEM_STRIDE * this.activeGateIndex); // select gate type
                    this.selectTimer.activate();
                    this.selectStep = 2;
                }
                return;
            case 2:
                if (this.selectTimer.isInactive()) {
                    gui.click(DIFFICULTY_DROPDOWN_X, DIFFICULTY_DROPDOWN_Y); // open difficulty select
                    this.selectTimer.activate();
                    this.selectStep = 3;
                }
                return;
            case 3:
                if (this.selectTimer.isInactive()) {
                    gui.click(DIFFICULTY_DROPDOWN_X,
                            DIFFICULTY_DROPDOWN_Y
                                    + DROPDOWN_ITEM_STRIDE * this.activeDifficulty.dropdownIndex); // select difficulty
                    this.selectTimer.activate();
                    this.selectStep = 4;
                }
                return;
            default:
                // Wait for select timer to finish before allowing next action
                if (this.selectTimer.isInactive()) {
                    this.selectionComplete = true;
                }
                return;
        }
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        if (this.activeDifficulty != null && this.activeGateIndex > 0) {
            sb.append("gate ").append(this.activeGateIndex)
              .append(" / ").append(this.activeDifficulty.label);
        }
        if (this.lastCompletedDifficulty != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("last: ").append(this.lastCompletedDifficulty.label);
        }
        this.statusDetails = sb.length() > 0 ? sb.toString() : null;
    }
}
