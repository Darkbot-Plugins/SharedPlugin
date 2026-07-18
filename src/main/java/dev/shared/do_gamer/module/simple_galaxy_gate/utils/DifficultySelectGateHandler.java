package dev.shared.do_gamer.module.simple_galaxy_gate.utils;

import java.util.regex.Pattern;

import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.util.Timer;

/**
 * Base handler for gates that display a difficulty-select GUI with a single
 * "Go" button,
 * clicked a fixed number of times before falling back to the default logic.
 */
public abstract class DifficultySelectGateHandler extends GateHandler {
    private static final Pattern MAP_PATTERN = Pattern.compile("^[1-5]-[1-4]$");
    private static final int MAX_CLICKS = 3;

    private final String difficultySelectGui;
    private final int portalTypeId;
    private final int goButtonX;
    private final int goButtonY;
    protected Timer clickTimer = Timer.get(10_000L);
    protected int clickCount = 0;
    private boolean setFlags = false;

    protected DifficultySelectGateHandler(String difficultySelectGui, int portalTypeId, int goButtonX,
            int goButtonY) {
        this.difficultySelectGui = difficultySelectGui;
        this.portalTypeId = portalTypeId;
        this.goButtonX = goButtonX;
        this.goButtonY = goButtonY;
    }

    @Override
    public final boolean prepareTickModule() {
        // Max clicks reached
        if (this.clickCount >= MAX_CLICKS) {
            if (this.onMaxClicksReached()) {
                return true; // Subclass wants to retry (e.g. select another option)
            }
            if (!this.setFlags) {
                this.setFlags = true; // Ensure flags are only set once
                this.module.setShouldMoveToRefinery(true);
                this.module.setCanSwitchProfile(true);
            }
            this.closeGui(this.difficultySelectGui);
            return false; // Allow default logic to take over
        }
        // Handle GUI interaction or traveling to gate
        if (this.handleGui() || this.handleTravelToGate(this.portalTypeId)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
    }

    @Override
    public boolean collectTickModule() {
        this.reset();
        this.closeGui(this.difficultySelectGui);
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
        super.reset();
    }

    @Override
    public final GameMap getMapForTravel() {
        String currentMapName = this.module.starSystem.getCurrentMap().getShortName();
        // Check if current map x-4 (include PvP and Pirates)
        boolean toLowMap = MAP_PATTERN.matcher(currentMapName).matches();
        return this.getFactionMapForTravel(toLowMap ? 1 : 8); // travel to map x-1 or x-8
    }

    /**
     * Handles the gate select GUI interaction
     */
    private boolean handleGui() {
        return this.getVisibleGui(this.difficultySelectGui).map(gui -> {
            // Initial timer, wait for preloading the GUI
            if (!this.clickTimer.isArmed()) {
                this.clickTimer.activate(3_000L);
                return true;
            }
            // Hook for subclasses needing extra GUI interaction before clicking "Go"
            if (this.beforeGoClick(gui)) {
                return true;
            }
            // Click "Go" button if timer allows
            if (this.clickTimer.isInactive()) {
                gui.click(this.goButtonX, this.goButtonY);
                this.clickTimer.activate();
                this.clickCount++;
            }
            return true;
        }).orElse(false);
    }

    /**
     * Called once the max click count has been reached, before falling back to the
     * default logic.
     * Return true to retry (e.g. after resetting the click count to select another
     * option).
     */
    protected boolean onMaxClicksReached() {
        return false;
    }

    /**
     * Called before clicking the "Go" button, allowing subclasses to perform extra
     * GUI interaction.
     * Return true to skip clicking "Go" for this tick.
     */
    protected boolean beforeGoClick(Gui gui) {
        return false;
    }
}
