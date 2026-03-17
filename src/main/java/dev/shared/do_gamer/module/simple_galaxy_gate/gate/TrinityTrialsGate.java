package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.util.Timer;

public class TrinityTrialsGate extends GateHandler {
    private static final String DIFFICULTY_SELECT_GUI = "trinitytrials_difficultyselect";
    private static final int PORTAL_TYPE_ID = 304; // Portal type ID for Trinity Trials
    private Timer clickTimer = Timer.get(10_000L);
    private int clickCount = 0;
    private boolean trySelectSecondGate = false;
    private Timer selectTimer = Timer.get(1_000L);
    private int selectStep = 0;
    private boolean setFlags = false;

    public TrinityTrialsGate() {
        this.npcRadiusMap.put("..::{ Pyrospire }::..", 620.0);
        this.npcRadiusMap.put("..::{ Vinespire }::..", 620.0);
    }

    @Override
    public boolean isJumpToNextMap() {
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
        radius = 580.0;
        npcInfo.setShouldKill(true);
        npcInfo.setRadius(radius);
        return radius;
    }

    @Override
    public boolean prepareTickModule() {
        // Max clicks reached
        if (this.clickCount >= 3) {
            if (this.isSunday() && !this.trySelectSecondGate) {
                // Set flag to try selecting second gate and reset counters
                this.trySelectSecondGate = true;
                this.clickCount = 0;
                this.selectStep = 0;
                return true; // Try selecting second gate
            }
            if (!this.setFlags) {
                this.setFlags = true; // Ensure flags are only set once
                this.module.setShouldMoveToRefinery(true);
                this.module.setGateVisited(true);
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
        this.trySelectSecondGate = false;
        this.selectStep = 0;
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
        boolean toLowMap = currentMapName.matches("^[1-5]-[1-4]$");
        return this.getFactionMapForTravel(toLowMap ? 1 : 8);
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
            // Selecting second gate
            if (this.trySelectSecondGate && this.selectGateType(gui, 2)) {
                return true;
            }
            // Click "Go" button if timer allows
            if (this.clickTimer.isInactive()) {
                gui.click(305, 345);
                this.clickTimer.activate();
                this.clickCount++;
            }
            return true;
        }).orElse(false);
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

    @Override
    public boolean fetchServerOffset() {
        return true;
    }

    private boolean isSunday() {
        LocalDateTime now = ServerTimeHelper.currentDateTime();
        return now.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
