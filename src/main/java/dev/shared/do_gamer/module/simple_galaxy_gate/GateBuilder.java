package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.List;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.galaxy.GalaxyGate;
import eu.darkbot.api.game.galaxy.GalaxyInfo;
import eu.darkbot.api.game.galaxy.GateInfo;
import eu.darkbot.api.managers.GalaxySpinnerAPI;
import eu.darkbot.util.Timer;

public final class GateBuilder {
    public enum BuildState {
        NONE,
        PREPARE,
        BUILD,
        END,
        EXIT
    }

    private final SimpleGalaxyGate module;
    private final GalaxySpinnerAPI galaxyManager;

    private final Timer placeTimer = Timer.get(3_000L);
    private final Timer spinTimer = Timer.get();
    private final Timer moveShipTimer = Timer.get(30_000L);
    private final Timer globalTimer = Timer.get(60_000L);

    private int galaxyInfoFailCount = 0;
    private int shipSwitchAttempts = 0;
    private boolean switchingShip = false;
    private boolean shipOffsetPositive = true;
    private BuildState state = BuildState.NONE;

    public GateBuilder(SimpleGalaxyGate module, PluginAPI api) {
        this.module = module;
        this.galaxyManager = api.requireAPI(GalaxySpinnerAPI.class);
    }

    public boolean isBuildState() {
        return this.state == BuildState.BUILD;
    }

    public boolean isSwitchingShip() {
        return this.switchingShip;
    }

    public void reset() {
        this.state = BuildState.NONE;
        this.switchingShip = false;
        this.shipSwitchAttempts = 0;
        this.galaxyInfoFailCount = 0;
        this.spinTimer.disarm();
        this.placeTimer.disarm();
        this.moveShipTimer.disarm();
        this.globalTimer.disarm();
    }

    public boolean tick() {
        if (this.module.getConfig() == null || !this.module.getConfig().builder.enabled) {
            return false; // Building disabled
        }

        if (StateStore.current() != StateStore.State.WAITING && StateStore.current() != StateStore.State.BUILDING) {
            return false; // Not in a state to build
        }

        GalaxyGate targetGate = Maps.resolveBuildGate(this.module.getConfig().gateId);
        if (targetGate == null) {
            return false; // Not buildable gate
        }

        this.handleGlobalTimeout();
        if (this.spinTimer.isActive()) {
            return true; // In timeout period, skip building
        }

        if (this.state == BuildState.EXIT) {
            return false; // Finished building
        }

        if (this.state == BuildState.END) {
            this.handleShipSwitch(this.module.getConfig().builder.switchShip.shipForGate, BuildState.EXIT);
            return true; // Switch back to gate ship after building
        }

        if (this.state == BuildState.NONE) {
            // Wait before start build (also helps to prevent the builder stuck)
            this.spinTimer.activate(5_000L);
            this.state = BuildState.PREPARE;
            return true;
        }

        Boolean updated = this.galaxyManager.updateGalaxyInfos(10_000);
        if (Boolean.FALSE.equals(updated)) {
            this.spinTimer.activate(1_000L);
            this.handleGalaxyInfoFetchFailure();
            return true;
        }

        GalaxyInfo info = this.galaxyManager.getGalaxyInfo();
        if (info == null) {
            this.spinTimer.activate(1_000L);
            this.handleGalaxyInfoFetchFailure();
            return true;
        }

        this.galaxyInfoFailCount = 0; // Reset fail count on success

        if (this.isGateBuiltOnMap(info, targetGate)) {
            this.state = BuildState.END;
            return true; // Gate already built
        }

        GalaxyGate gateToPlace = this.findGateToPlace(info, targetGate);
        if (gateToPlace != null) {
            this.handleGatePlacement(gateToPlace);
            return true; // Placing gate
        }

        if (!this.canBuildGG(info)) {
            this.state = BuildState.END;
            System.out.println("Cannot build gate due to insufficient resources, skipping building.");
            return true; // Cannot build gate due to resources
        }

        if (this.state == BuildState.PREPARE) {
            this.handleShipSwitch(this.module.getConfig().builder.switchShip.shipForBuild, BuildState.BUILD);
            return true; // Switch to build ship before starting to build
        }

        double progress = this.getProgress(info, targetGate);
        SpinOption spinOption = this.getSpinOption(progress);
        long waitTime = (spinOption.waitMs * this.module.getConfig().builder.speed.multiplier);
        this.spinTimer.activate(waitTime);
        this.galaxyManager.spinGate(targetGate, this.module.getConfig().builder.useMultiAt, spinOption.spins, 10);
        this.moveShipPeriodically(); // Move ship to avoid AFK
        this.globalTimer.disarm(); // Reset global timeout after successful spin
        return true;
    }

    /**
     * Handles switching to the gate ship if needed.
     */
    public boolean switchToGateShip() {
        if (this.module.getConfig() != null
                && this.module.getConfig().builder.enabled
                && this.module.getConfig().builder.switchShip.enabled
                && this.state != BuildState.EXIT) {
            if (this.spinTimer.isInactive()) {
                this.handleShipSwitch(this.module.getConfig().builder.switchShip.shipForGate, BuildState.EXIT);
            }
            return true; // In switching process, wait for next tick
        }
        return false; // No switching needed
    }

    /**
     * Handles ship switching for build states.
     */
    private void handleShipSwitch(String hangarId, BuildState successState) {
        if (this.switchToShip(hangarId)) {
            this.state = successState;
            this.spinTimer.activate(5_000L); // Wait after switching
            this.shipSwitchAttempts = 0; // Reset fail count on successful switch
            return;
        }
        this.spinTimer.activate(3_000L); // Wait before retrying
        this.handleShipSwitchFailure();
    }

    /**
     * Handles ship switch failure by incrementing attempt count
     * and refreshing the game if too many failures occur.
     */
    private void handleShipSwitchFailure() {
        this.shipSwitchAttempts++;
        if (this.shipSwitchAttempts > 10) {
            System.out.println("Failed to switch ship for 10 consecutive attempts, refreshing the game...");
            this.module.bot.handleRefresh();
            this.shipSwitchAttempts = 0; // Reset fail count after refresh
            this.switchingShip = false; // Reset switching flag after refresh
            this.state = BuildState.PREPARE; // Retry switching to build ship after refresh
        }
    }

    /**
     * Switches to the specified ship if enabled and ship is different.
     */
    private boolean switchToShip(String hangarId) {
        if (!this.module.getConfig().builder.switchShip.enabled) {
            return true; // No switching needed
        }

        if (SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShips().isEmpty() || hangarId == null) {
            return false; // No ship available to switch, wait for next update
        }

        String currentHangar = this.module.backpageManager.legacyHangarManager.getActiveHangar();

        if (hangarId.equals(currentHangar)) {
            if (this.isActiveShip(hangarId)) {
                this.switchingShip = false;
                return true; // Already in the correct hangar and ship active
            }
            if (this.switchingShip) {
                return false; // Hangar matches but ship not active yet; still switching
            }
        } else if (this.switchingShip) {
            this.module.setUpdateHangarData(true);
            return false; // Still switching to some other hangar
        }

        this.switchingShip = true;
        this.module.backpageManager.legacyHangarManager.changeHangar(hangarId);
        return false;
    }

    /**
     * Checks if the specified hangar ID corresponds to the active ship.
     */
    private boolean isActiveShip(String hangarId) {
        String shipName = SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShipName(hangarId);
        if (shipName == null) {
            return false;
        }
        String shipType = this.module.hero.getShipType();
        return shipType.equals(shipName) || shipType.startsWith(shipName + "_design");
    }

    /**
     * Checks if the target gate (or ABG gates) is already built on the map.
     */
    private boolean isGateBuiltOnMap(GalaxyInfo info, GalaxyGate targetGate) {
        if (targetGate == GalaxyGate.ALPHA) {
            GalaxyGate[] abgGates = { GalaxyGate.ALPHA, GalaxyGate.BETA, GalaxyGate.GAMMA };
            int onMapCount = 0;
            for (GalaxyGate gate : abgGates) {
                GateInfo gi = info.getGateInfo(gate);
                if (gi.isOnMap()) {
                    // If any ABG gates are also completed(ready for place), consider it's built.
                    if (gi.isCompleted()) {
                        return true;
                    }
                    onMapCount++;
                }
            }
            return onMapCount == 3;
        }
        return info.getGateInfo(targetGate).isOnMap();
    }

    /**
     * Checks if a gate is completed but not yet placed on the map.
     */
    private boolean isGateReadyToPlace(GalaxyInfo info, GalaxyGate gate) {
        return info.getGateInfo(gate).isCompleted() && !info.getGateInfo(gate).isOnMap();
    }

    /**
     * Finds which gate to place based on the configuration.
     */
    private GalaxyGate findGateToPlace(GalaxyInfo info, GalaxyGate targetGate) {
        // ABG gates
        if (targetGate == GalaxyGate.ALPHA) {
            for (GalaxyGate gate : new GalaxyGate[] { GalaxyGate.ALPHA, GalaxyGate.BETA, GalaxyGate.GAMMA }) {
                if (this.isGateReadyToPlace(info, gate)) {
                    return gate;
                }
            }
            return null;
        }

        // Regular gate
        if (this.isGateReadyToPlace(info, targetGate)) {
            return targetGate;
        }
        return null;
    }

    /**
     * Handles the placement of the Galaxy Gate.
     */
    private void handleGatePlacement(GalaxyGate gate) {
        if (this.placeTimer.isInactive()) {
            if (this.galaxyManager.placeGate(gate, 100)) {
                this.spinTimer.activate(5_000L);
            } else {
                this.placeTimer.activate(1_000L);
            }
        }
    }

    /**
     * Checks if can build the Galaxy Gate based on energy and uridium.
     */
    private boolean canBuildGG(GalaxyInfo info) {
        if (info.getFreeEnergy() > 0) {
            return true;
        }
        if (this.module.getConfig().builder.useExtraEnergyOnly) {
            return false;
        }
        if (info.getEnergyCost() > this.module.getConfig().builder.maxSpinCost) {
            return false;
        }
        return info.getUridium() >= this.module.getConfig().builder.minUriBalance;
    }

    /**
     * Gets the overall progress ratio for the target gate(s).
     */
    private double getProgress(GalaxyInfo info, GalaxyGate targetGate) {
        if (targetGate == GalaxyGate.ALPHA) {
            // For ABG, take the minimum progress among Alpha, Beta, Gamma
            double alphaProgress = this.calculateProgress(info, GalaxyGate.ALPHA);
            double betaProgress = this.calculateProgress(info, GalaxyGate.BETA);
            double gammaProgress = this.calculateProgress(info, GalaxyGate.GAMMA);
            return Math.min(alphaProgress, Math.min(betaProgress, gammaProgress));
        } else {
            // For other gates, just the target gate's progress
            return this.calculateProgress(info, targetGate);
        }
    }

    /**
     * Calculate the progress ratio for a specific gate.
     */
    private double calculateProgress(GalaxyInfo info, GalaxyGate gate) {
        return (double) info.getGateInfo(gate).getCurrentParts()
                / (double) info.getGateInfo(gate).getTotalParts();
    }

    /**
     * Determines the spin option (number of spins and wait time)
     * based on the current progress of the gate building.
     */
    private SpinOption getSpinOption(double progress) {
        // Define spin options in descending order of threshold
        List<SpinOption> options = List.of(
                new SpinOption(this.module.getConfig().builder.spins100, 100,
                        SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_100),
                new SpinOption(this.module.getConfig().builder.spins10, 10,
                        SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_10),
                new SpinOption(this.module.getConfig().builder.spins5, 5,
                        SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_5));

        return options.stream()
                .filter(option -> option.threshold > progress)
                .findFirst()
                .orElse(new SpinOption(0.0, 1, SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_1));
    }

    /**
     * Helper class to hold spin options based on progress thresholds.
     */
    private static final class SpinOption {
        private final double threshold;
        private final int spins;
        private final int waitMs;

        private SpinOption(double threshold, int spins, int waitMs) {
            this.threshold = threshold;
            this.spins = spins;
            this.waitMs = waitMs;
        }
    }

    /**
     * Moves the ship periodically to avoid AFK detection.
     * Works only in BUILD state and when building is in progress.
     */
    private void moveShipPeriodically() {
        if (this.moveShipTimer.isInactive()) {
            double targetX = this.module.hero.getX() + (this.shipOffsetPositive ? 100 : -100);
            this.module.movement.moveTo(targetX, this.module.hero.getY());
            this.shipOffsetPositive = !this.shipOffsetPositive;
            this.moveShipTimer.activate();
        }
    }

    /**
     * Handles failures in fetching galaxy info by counting consecutive failures
     * and refreshing the game if too many occur.
     */
    private void handleGalaxyInfoFetchFailure() {
        this.galaxyInfoFailCount++;
        if (this.galaxyInfoFailCount > 10) {
            System.out.println("Failed to fetch galaxy info for 10 consecutive times, refreshing the game...");
            this.module.bot.handleRefresh();
            this.galaxyInfoFailCount = 0;
        }
    }

    /**
     * Handles the global timeout by refreshing the game and resetting the state
     * if the builder has been active for too long without completing the build.
     */
    private void handleGlobalTimeout() {
        if (!this.globalTimer.isArmed()) {
            this.globalTimer.activate();
        } else if (this.globalTimer.isInactive()) {
            System.out.println("Global timeout reached, resetting builder state and refreshing the game...");
            this.module.bot.handleRefresh();
            this.reset();
        }
    }
}
