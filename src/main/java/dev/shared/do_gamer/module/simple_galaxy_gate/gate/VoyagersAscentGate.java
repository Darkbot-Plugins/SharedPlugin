package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.regex.Pattern;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.util.Timer;

public final class VoyagersAscentGate extends GateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "singularitydrive_difficultyselect";
    private static final int PORTAL_TYPE_ID = 306; // Portal type ID for Voyagers Ascent
    private static final Pattern MAP_PATTERN = Pattern.compile("^[1-5]-[1-4]$");
    private Timer clickTimer = Timer.get(10_000L);
    private int clickCount = 0;
    private boolean setFlags = false;

    public VoyagersAscentGate() {
        this.defaultNpcParam = new NpcParam(580.0);
        this.jumpToNextMap = false;
        this.safeRefreshInGate = false;
    }

    @Override
    public boolean prepareTickModule() {
        // Max clicks reached
        if (this.clickCount >= 3) {
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
            return true;
        }
        return false;
    }

    @Override
    public boolean collectTickModule() {
        this.reset();
        this.closeGui(DIFFICULTY_SELECT_GUI);
        return false;
    }

    @Override
    public void reset() {
        // Reset states
        this.clickCount = 0;
        this.setFlags = false;
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

    /**
     * Handles the gate select GUI interaction
     */
    private boolean handleGui() {
        return this.getVisibleGui(DIFFICULTY_SELECT_GUI).map(gui -> {
            // Initial timer, wait for preloading the GUI
            if (!this.clickTimer.isArmed()) {
                this.clickTimer.activate(3_000L);
                return true;
            }
            // Click "Go" button if timer allows
            if (this.clickTimer.isInactive()) {
                gui.click(250, 295);
                this.clickTimer.activate();
                this.clickCount++;
            }
            return true;
        }).orElse(false);
    }
}
