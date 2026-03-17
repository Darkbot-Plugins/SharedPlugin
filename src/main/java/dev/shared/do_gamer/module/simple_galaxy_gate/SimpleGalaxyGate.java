package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.entities.ShipInfo;
import com.github.manolo8.darkbot.config.NpcExtraFlag;
import com.github.manolo8.darkbot.config.types.suppliers.BrowserApi;
import com.github.manolo8.darkbot.core.itf.NpcExtraProvider;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.KamikazeNpcFlag;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.GateHandler;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.FeatureInfo;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.galaxy.GalaxyGate;
import eu.darkbot.api.game.galaxy.GalaxyInfo;
import eu.darkbot.api.game.galaxy.GateInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.ExtensionsAPI;
import eu.darkbot.api.managers.GalaxySpinnerAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.util.Timer;

@Feature(name = "Simple Galaxy Gate", description = "Automates Galaxy Gate building and farming.")
public class SimpleGalaxyGate implements Module, Task, Configurable<SimpleGalaxyGateConfig>, NpcExtraProvider {
    public final HeroAPI hero;
    public final MovementAPI movement;
    private final PetAPI pet;
    public final EntitiesAPI entities;
    public final CustomLootModule lootModule;
    public final CustomCollectorModule collectorModule;
    public final StarSystemAPI starSystem;
    public final MapTraveler traveler;
    public final PortalJumper jumper;
    private final GalaxySpinnerAPI galaxyManager;
    public final BotAPI bot;
    private final BackpageManager backpageManager;
    private final ExtensionsAPI extensionsAPI;
    private final ConfigAPI configApi;
    public final GameScreenAPI gameScreenApi;

    public final ConfigSetting<BrowserApi> botBrowserApi;

    private final Timer placeTimer = Timer.get(3_000L);
    private final Timer spinTimer = Timer.get();
    private final Timer moveShipTimer = Timer.get(60_000L);
    private final Timer stuckInGateTimer = Timer.get();
    private final Timer switchProfileTimer = Timer.get(30_000L);
    private boolean triedReloadOnStuck = false;
    private boolean shouldMoveToRefinery = false;
    private boolean shipOffsetPositive = true;
    private int galaxyInfoFailCount = 0;
    private int shipSwitchAttempts = 0;
    private boolean isSwitchingShip = false;
    private boolean updateHangarData = true;
    private boolean gateVisited = false;
    private boolean fetchServerOffset = false;

    private enum buildState {
        NONE, // not building
        PREPARE, // switch to build ship
        BUILD, // building progress
        END, // switch back to gate ship
        EXIT // finished building
    }

    private buildState currentBuildState = buildState.NONE;

    private SimpleGalaxyGateConfig config;
    private String statusDetails = null;

    public SimpleGalaxyGate(PluginAPI api) {
        this.hero = api.requireAPI(HeroAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.lootModule = new CustomLootModule(api);
        this.collectorModule = new CustomCollectorModule(api);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.traveler = api.requireInstance(MapTraveler.class);
        this.jumper = api.requireInstance(PortalJumper.class);
        this.galaxyManager = api.requireAPI(GalaxySpinnerAPI.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.backpageManager = api.requireInstance(BackpageManager.class);
        this.extensionsAPI = api.requireAPI(ExtensionsAPI.class);
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.gameScreenApi = api.requireAPI(GameScreenAPI.class);

        this.lootModule.setCollector(this.collectorModule); // Link collector module

        this.botBrowserApi = this.configApi.requireConfig("bot_settings.api_config.browser_api");
    }

    @Override
    public void setConfig(ConfigSetting<SimpleGalaxyGateConfig> config) {
        this.config = config.getValue();
        // Make sure the modules receive the same configuration instance.
        this.lootModule.setModuleConfig(this.config);
    }

    @Override
    public NpcExtraFlag[] values() {
        return KamikazeNpcFlag.values();
    }

    @Override
    public String getStatus() {
        StringBuilder status = new StringBuilder(
                String.format("Simple Galaxy Gate | %s", StateStore.current().message));

        switch (StateStore.current()) {
            case TRAVELING_TO_GATE:
                if (this.isSwitchingShip) {
                    status.append(": Switching Ship");
                } else {
                    status.append(String.format(": %s", Maps.mapNameForGate(this.config.gateId)));
                }
                break;
            case BUILDING:
                if (this.isSwitchingShip) {
                    status.append(": Switching Ship");
                } else if (this.currentBuildState == buildState.BUILD) {
                    status.append(String.format(": %s", Maps.mapNameForGate(this.config.gateId)));
                } else {
                    status.append(": Waiting...");
                }
                break;
            case ATTACKING:
            case COLLECTING:
            case KAMIKAZE:
            case GUARDING:
                status.append(String.format(" | NPC: %d", this.lootModule.getNpcs().size()));
                if (this.statusDetails != null) {
                    if (!this.statusDetails.isEmpty()) {
                        status.append(String.format(" | %s", this.statusDetails));
                    }
                } else {
                    status.append(String.format(" | Box: %d", this.collectorModule.count()));
                }
                break;
            default:
                break;
        }

        this.appendDebugInfo(status);
        return status.toString();
    }

    /**
     * Debug information only for dev needs
     */
    private void appendDebugInfo(StringBuilder status) {
        switch (this.config.other.debugInfo) {
            case POSITION:
                double heroX = this.hero.getX();
                double heroY = this.hero.getY();
                String heroAction = "Idle";
                if (this.movement.isMoving()) {
                    heroAction = "Moving";
                } else if (this.entities.getPortals().stream().anyMatch(Portal::isJumping)) {
                    heroAction = "Jumping";
                }
                status.append(String.format("%nPosition: X: %.0f, Y: %.0f | %s", heroX, heroY, heroAction));
                break;
            case PORTALS:
                for (Portal p : this.entities.getPortals()) {
                    String mapName = p.getTargetMap().map(GameMap::getName).orElse("Unknown");
                    int mapId = p.getTargetMap().map(GameMap::getId).orElse(-1);
                    int typeId = p.getTypeId();
                    status.append(String.format("%nPortal: %s (MapID: %d, TypeID: %d)", mapName, mapId, typeId));
                }
                break;
            default:
                // No debug info
                break;
        }

    }

    @Override
    public boolean canRefresh() {
        return (!this.isMapGG() && StateStore.current() == StateStore.State.WAITING)
                || (this.lootModule.getNpcs().isEmpty()
                        && this.canJump()
                        && this.hero.distanceTo(Maps.getMapCenterX(), Maps.getMapCenterY()) <= Maps
                                .getToleranceDistance());
    }

    public void setShouldMoveToRefinery(boolean shouldMoveToRefinery) {
        this.shouldMoveToRefinery = shouldMoveToRefinery;
    }

    public void setGateVisited(boolean gateVisited) {
        this.gateVisited = gateVisited;
    }

    public SimpleGalaxyGateConfig getConfig() {
        return config;
    }

    public void setStatusDetails(String statusDetails) {
        this.statusDetails = statusDetails;
    }

    @Override
    public void onTickTask() {
        // logic implemented in onBackgroundTick
    }

    @Override
    public void onBackgroundTick() {
        if (this.fetchServerOffset) {
            ServerTimeHelper.fetchServerOffset(this.backpageManager);
        }

        if (this.updateHangarData) {
            this.backpageManager.legacyHangarManager.updateHangarData(500);
            this.updateHangarData = false;
        }

        // Populate ship dropdown if empty
        Map<String, String> ships = SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShips();
        if (ships.isEmpty()) {
            List<ShipInfo> shipInfos = this.backpageManager.legacyHangarManager.getShipInfos();
            if (shipInfos.isEmpty()) {
                this.updateHangarData = true;
                return;
            }
            shipInfos.stream()
                    .filter(si -> si.getOwned() == 1)
                    .sorted(Comparator.comparing(ShipInfo::getFav).reversed())
                    .forEach(si -> ships.put(si.getHangarId(), si.getLootId()));
        }
    }

    @Override
    public void onTickStopped() {
        this.currentBuildState = buildState.NONE; // Reset build state
        this.isSwitchingShip = false; // Reset switching flag
        this.shipSwitchAttempts = 0; // Reset fail count after refresh

        // Reset gate handler state if needed
        this.createGateHandler().reset();
    }

    @Override
    public void onTickModule() {
        if (this.config == null) {
            return;
        }

        // Resolve conflicts
        this.conflictResolver();
        // Apply previous state requests
        StateStore.apply();

        if (StateStore.current() != StateStore.State.WAITING_IN_GATE && this.stuckInGateTimer.isArmed()) {
            this.stuckInGateTimer.disarm(); // Reset stuck timer when not waiting in gate
            this.triedReloadOnStuck = false;
        }

        // Create gate handler instance
        GateHandler gateHandler = this.createGateHandler();

        // Handle Galaxy Gate map
        if (this.isMapGG()) {
            this.shouldMoveToRefinery = true;
            this.currentBuildState = buildState.NONE; // Reset build state
            this.gateVisited = true; // Mark gate as visited
            this.handleGalaxyGate(gateHandler);
            return;
        }

        this.statusDetails = null; // Clear status details

        // Handle profile switching
        if (this.switchProfile()) {
            return;
        }
        // Execute prepare tick logic if provided by gate handler
        if (gateHandler.prepareTickModule()) {
            return;
        }

        // Handle traveling to the Galaxy Gate
        if (this.handleTravelToGalaxyGate(gateHandler)) {
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return;
        }

        // Move to refinery if needed
        if (this.shouldMoveToRefinery && this.moveToRefinery()) {
            StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
            return;
        }

        // Handle building the Galaxy Gate
        if (this.handleGateBuilding()) {
            StateStore.request(StateStore.State.BUILDING);
            this.moveToRefinery(); // Stay near refinery while building
            this.moveShipPeriodically(); // Move ship to avoid AFK
            this.pet.setEnabled(false); // Disable pet while building
            return;
        }

        StateStore.request(StateStore.State.WAITING);
    }

    /**
     * Create a GateHandler instance.
     */
    private GateHandler createGateHandler() {
        GateHandler handler = Maps.getGateHandler(this.config.gateId, this);
        Maps.setMapCenterX(handler.getMapCenterX());
        Maps.setMapCenterY(handler.getMapCenterY());
        Maps.setToleranceDistance(handler.getToleranceDistance());
        this.fetchServerOffset = handler.fetchServerOffset();
        return handler;
    }

    /**
     * Handles the logic when in a Galaxy Gate map.
     */
    private void handleGalaxyGate(GateHandler gateHandler) {
        this.lootModule.setGateHandler(gateHandler); // Link gate handler to loot module

        // Attack NPCs
        if (!this.lootModule.getNpcs().isEmpty()) {
            StateStore.request(StateStore.State.ATTACKING);
            this.lootModule.onTickModule();
            return;
        }

        // No NPCs, collect boxes or jump to next map
        StateStore.request(StateStore.State.COLLECTING);
        if (gateHandler.collectTickModule()) {
            return; // Gate handler took action, skip default collection
        }
        this.collectorModule.onTickModule();

        if (gateHandler.isJumpToNextMap() && this.canJump()) {
            // Jump to next map
            StateStore.request(StateStore.State.JUMPING);
            this.jumpToNextMap();
            return;
        }
        if (this.collectorModule.hasNoBox()) {
            // No boxes to collect, move to center
            StateStore.request(StateStore.State.WAITING_IN_GATE);
            this.moveToCenter(gateHandler);
        }
    }

    /**
     * Moves the hero to the center of the map.
     * If stuck in the gate for too long, move to radiation
     */
    private void moveToCenter(GateHandler gateHandler) {
        double shift = 500.0;
        double x = (Maps.getMapCenterX() - shift);
        double y = (Maps.getMapCenterY() - shift);

        if (!this.handleStuckInGate(x) && gateHandler.isMoveToCenter()) {
            this.moveToPosition(x, y);
        }

        if (StateStore.current() == StateStore.State.WAITING_IN_GATE && !this.stuckInGateTimer.isArmed()) {
            this.activateStuckInGateTimer(); // Activate stuck timer
        }
    }

    /**
     * Moves the hero to the specified position
     * if far enough and movement is possible.
     */
    public void moveToPosition(double x, double y) {
        double gap = 500.0;
        if (this.hero.distanceTo(x, y) > gap && this.movement.canMove(x, y)) {
            this.movement.moveTo(x, y);
        }
    }

    /**
     * Handles the logic for when the hero is stuck in the gate.
     */
    private boolean handleStuckInGate(double x) {
        if (this.stuckInGateTimer.isArmed() && this.stuckInGateTimer.isInactive()) {
            if (!this.triedReloadOnStuck) {
                // First try to reload the game
                this.triedReloadOnStuck = true;
                System.out.println("Ship seems stuck in gate, refreshing the game...");
                this.bot.handleRefresh();
                this.activateStuckInGateTimer();
                return true;
            } else {
                // Else move to radiation to destroy the ship
                this.moveToPosition(x, 0);
                return true;
            }
        }
        return false;
    }

    /**
     * Activates the stuck in gate timer if configured.
     */
    private void activateStuckInGateTimer() {
        if (this.config.other.stuckInGateTimerMinutes > 0) {
            this.stuckInGateTimer.activate(this.config.other.stuckInGateTimerMinutes * 60_000L);
        }
    }

    /**
     * Determines if the current map is a Galaxy Gate map (not a general map).
     */
    private boolean isMapGG() {
        GameMap currentMap = this.starSystem.getCurrentMap();
        if (currentMap == null) {
            return false;
        }
        String name = currentMap.getShortName();
        // Except general maps like 1-1, 2-3, 3BL, etc.
        return !name.matches("^[1-5]-[1-8]$") && !name.matches("^[1-3]BL$");
    }

    /**
     * Handles the logic to travel to the Galaxy Gate map
     * based on the gate handler's instructions or default logic.
     */
    private boolean handleTravelToGalaxyGate(GateHandler gateHandler) {
        if (StateStore.current() == StateStore.State.BUILDING) {
            return false; // Do not travel while building
        }

        GameMap map = gateHandler.getMapForTravel();
        // If gate handler provides a specific map for travel, use it.
        // Otherwise, use default logic to find the gate map.
        if (map == null) {
            map = this.getMapForTravel();
        }
        // If we have a target map to travel to, do it.
        if (map != null) {
            // Switch ship if needed
            if (!this.switchToGateShip()) {
                this.travelToGalaxyGate(map);
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the appropriate map for traveling to the Galaxy Gate.
     */
    private GameMap getMapForTravel() {
        Integer gateId = this.config.gateId;
        if (gateId == null) {
            return null;
        }

        if (!Maps.isGateOnCurrentMap(gateId, this.starSystem)) {
            // Not on current map, return configured gate ID or Alpha gate ID for ABG
            int map = gateId == 0 ? Maps.ABG_IDS.get(0) : gateId;
            return this.starSystem.getOrCreateMap(map);
        }

        // Handle ABG gate ID
        if (gateId == 0) {
            // Try Alpha, Beta, Gamma in order
            for (int id : Maps.ABG_IDS) {
                if (this.isGateAvailable(id)) {
                    return this.starSystem.getOrCreateMap(id); // Found existing gate
                }
            }
            return null; // None accessible
        }

        // Regular gate ID: check if any portal leads to it
        return this.isGateAvailable(gateId) ? this.starSystem.getOrCreateMap(gateId) : null;
    }

    /**
     * Checks if a Galaxy Gate is available on the current map.
     */
    private boolean isGateAvailable(int gateId) {
        return this.entities.getPortals().stream()
                .anyMatch(p -> p.getTargetMap().map(m -> m.getId() == gateId).orElse(false));
    }

    /**
     * Travels to the Galaxy Gate and jump when arrived.
     */
    private void travelToGalaxyGate(GameMap target) {
        if (target != null && !this.entities.getPortals().isEmpty()) {
            this.traveler.setTarget(target);
            if (!this.traveler.isDone()) {
                this.traveler.tick();
            }
        }
    }

    /**
     * Move to the refinery station if not already nearby.
     */
    private boolean moveToRefinery() {
        Station refinery = this.entities.getStations().stream()
                .filter(Station.Refinery.class::isInstance)
                .findFirst()
                .orElse(null);

        if (refinery != null) {
            if (this.hero.distanceTo(refinery) > 500) {
                this.hero.setRunMode();
                this.movement.moveTo(refinery);
                return true;
            }
            // Reached, set flag to false
            this.shouldMoveToRefinery = false;
        }
        return false;
    }

    /**
     * Checks if the hero can jump to the next map.
     */
    private boolean canJump() {
        return this.collectorModule.hasNoBox() && !this.entities.getPortals().isEmpty();
    }

    /**
     * Handles building the configured gate when not inside a GG map.
     */
    private boolean handleGateBuilding() {
        if (!this.config.builder.enabled) {
            return false; // Building disabled
        }

        StateStore.State current = StateStore.current();
        if (current != StateStore.State.WAITING && current != StateStore.State.BUILDING) {
            return false; // Not in a state to build
        }

        GalaxyGate targetGate = Maps.resolveBuildGate(this.config.gateId);
        if (targetGate == null) {
            return false; // Not buildable gate
        }

        if (this.spinTimer.isActive()) {
            return true; // In timeout period, skip building
        }

        if (this.currentBuildState == buildState.EXIT) {
            return false; // Finished building
        }

        if (this.currentBuildState == buildState.END) {
            this.handleShipSwitch(this.config.builder.switchShip.shipForGate, buildState.EXIT);
            return true; // Switch back to gate ship after building
        }

        if (this.currentBuildState == buildState.NONE) {
            // Wait before start build (also helps to prevent the builder stuck)
            this.spinTimer.activate(5_000L);
            this.currentBuildState = buildState.PREPARE;
            return true;
        }

        Boolean updated = this.galaxyManager.updateGalaxyInfos(500);
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
            this.currentBuildState = buildState.END;
            return true; // Gate already built
        }

        GalaxyGate gateToPlace = this.findGateToPlace(info, targetGate);
        if (gateToPlace != null) {
            this.handleGatePlacement(gateToPlace);
            return true; // Placing gate
        }

        if (!this.canBuildGG(info)) {
            this.currentBuildState = buildState.END;
            System.out.println("Cannot build gate due to insufficient resources, skipping building.");
            return true; // Cannot build gate due to resources
        }

        if (this.currentBuildState == buildState.PREPARE) {
            this.handleShipSwitch(this.config.builder.switchShip.shipForBuild, buildState.BUILD);
            return true; // Switch to build ship before starting to build
        }

        double progress = this.getProgress(info, targetGate);
        SpinOption spinOption = this.getSpinOption(progress);
        long waitTime = (spinOption.waitMs * this.config.builder.speed.multiplier);
        this.spinTimer.activate(waitTime);
        this.galaxyManager.spinGate(targetGate, this.config.builder.useMultiAt, spinOption.spins, 10);
        return true;
    }

    /**
     * Handles switching to the gate ship if needed.
     */
    private boolean switchToGateShip() {
        if (this.config.builder.enabled
                && this.config.builder.switchShip.enabled
                && this.currentBuildState != buildState.EXIT) {
            if (this.spinTimer.isInactive()) {
                this.handleShipSwitch(this.config.builder.switchShip.shipForGate, buildState.EXIT);
            }
            return true; // In switching process, wait for next tick
        }
        return false; // No switching needed
    }

    /**
     * Handles ship switching for build states.
     */
    private void handleShipSwitch(String hangarId, buildState successState) {
        if (this.switchToShip(hangarId)) {
            this.currentBuildState = successState;
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
            this.bot.handleRefresh();
            this.shipSwitchAttempts = 0; // Reset fail count after refresh
            this.isSwitchingShip = false; // Reset switching flag after refresh
            this.currentBuildState = buildState.PREPARE; // Retry switching to build ship after refresh
        }
    }

    /**
     * Switches to the specified ship if ship switching is enabled and ship is
     * different.
     */
    private boolean switchToShip(String hangarId) {
        if (!this.config.builder.switchShip.enabled) {
            return true; // No switching needed
        }

        if (SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShips().isEmpty() || hangarId == null) {
            return false; // No ship available to switch, wait for next update
        }

        String currentHangar = this.backpageManager.legacyHangarManager.getActiveHangar();
        if (hangarId.equals(currentHangar)) {
            if (this.isActiveShip(hangarId)) {
                this.isSwitchingShip = false;
                return true; // Already in the correct hangar and ship active
            }
            if (this.isSwitchingShip) {
                return false; // Hangar matches but ship not active yet; still switching
            }
        } else if (this.isSwitchingShip) {
            this.updateHangarData = true;
            return false; // Still switching to some other hangar
        }

        // Attempt to switch hangar
        this.isSwitchingShip = true;
        this.backpageManager.legacyHangarManager.changeHangar(hangarId);
        return false; // Switching in progress
    }

    /**
     * Checks if the specified hangar ID corresponds to the active ship.
     */
    private boolean isActiveShip(String hangarId) {
        String shipName = SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShipName(hangarId);
        if (shipName == null) {
            return false;
        }
        String shipType = this.hero.getShipType();
        return shipType.equals(shipName) || shipType.startsWith(shipName + "_design");
    }

    /**
     * Handles the logic to jump to the next map.
     */
    private void jumpToNextMap() {
        Portal portal = this.findNextPortal();
        if (portal != null) {
            this.jumper.travelAndJump(portal);
        }
    }

    /**
     * Finds the next portal to jump through.
     */
    private Portal findNextPortal() {
        Collection<? extends Portal> portals = this.entities.getPortals();
        if (portals.isEmpty()) {
            return null;
        }

        // If only one portal, take it.
        if (portals.size() == 1) {
            return portals.iterator().next();
        }

        // Known home map portals by type ID
        List<Integer> homeTypeIds = List.of(
                1, // Standard type for Home Map
                302 // DSE portal type to Home Map
        );

        return portals.stream()
                .filter(p -> !homeTypeIds.contains(p.getPortalType().getId()))
                .findFirst()
                .orElse(null);
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
        SimpleGalaxyGateConfig.BuilderSettings builder = this.config.builder;
        if (info.getFreeEnergy() > 0) {
            return true;
        }
        if (builder.useExtraEnergyOnly) {
            return false;
        }
        if (info.getEnergyCost() > builder.maxSpinCost) {
            return false;
        }
        return info.getUridium() >= builder.minUriBalance;
    }

    /**
     * Gets the overall progress ratio for the target gate(s).
     */
    private double getProgress(GalaxyInfo info, GalaxyGate targetGate) {
        if (targetGate == GalaxyGate.ALPHA) {
            // For ABG, take the minimum progress among Alpha, Beta, Gamma
            double alphaProgress = this.getGateProgress(info, GalaxyGate.ALPHA);
            double betaProgress = this.getGateProgress(info, GalaxyGate.BETA);
            double gammaProgress = this.getGateProgress(info, GalaxyGate.GAMMA);
            return Math.min(alphaProgress, Math.min(betaProgress, gammaProgress));
        } else {
            // For other gates, just the target gate's progress
            return this.getGateProgress(info, targetGate);
        }
    }

    /**
     * Gets the progress ratio for a specific gate.
     */
    private double getGateProgress(GalaxyInfo info, GalaxyGate gate) {
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
                new SpinOption(this.config.builder.spins100, 100,
                        SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_100),
                new SpinOption(this.config.builder.spins10, 10,
                        SimpleGalaxyGateConfig.BuilderSettings.WAIT_TIME_SPIN_10),
                new SpinOption(this.config.builder.spins5, 5,
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
        StateStore.State state = StateStore.current();
        if (state == StateStore.State.BUILDING
                && this.currentBuildState == buildState.BUILD
                && this.moveShipTimer.isInactive()) {
            double targetX = this.shipOffsetPositive ? hero.getX() + 100 : hero.getX() - 100;
            movement.moveTo(targetX, hero.getY());
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
            this.bot.handleRefresh();
            this.galaxyInfoFailCount = 0; // Reset fail count after refresh
        }
    }

    /**
     * Resolves conflicts with other Galaxy Gate related features by disabling them.
     */
    private void conflictResolver() {
        Set<String> conflicts = Set.of(
                "eu.darkbot.popcorn.don.GGSpinner",
                "com.pikapika.behaviour.gateSpinShipChanger.GateSpinShipChanger",
                "com.pikapika.behaviour.refreshGateComplete.RefreshGateComplete");

        for (String featureId : conflicts) {
            FeatureInfo<?> featureInfo = this.extensionsAPI.getFeatureInfo(featureId);
            if (featureInfo != null && featureInfo.isEnabled()) {
                featureInfo.setEnabled(false); // Disable conflicting feature
            }
        }
    }

    /**
     * Checks if the gate was visited then switches to the specified profile.
     */
    private boolean switchProfile() {
        if (!this.gateVisited) {
            return false;
        }

        if (this.config.other.onlyWhenNotAvailable && this.shouldDelayProfileSwitch()) {
            return false;
        }

        this.gateVisited = false; // Reset for next time
        this.switchProfileTimer.disarm(); // Reset timer
        if (this.config.other.botProfile != null) {
            // Switch to the specified profile
            this.configApi.setConfigProfile(this.config.other.botProfile);
            return true;
        }
        return false;
    }

    /**
     * Returns true when switching should be delayed to ensure no more activity.
     */
    private boolean shouldDelayProfileSwitch() {
        if (StateStore.current() != StateStore.State.WAITING) {
            this.switchProfileTimer.disarm(); // Reset timer
            return false;
        }

        // Activate timer to make sure there is no more activity before switching
        if (!this.switchProfileTimer.isArmed()) {
            this.switchProfileTimer.activate();
            return true; // Wait before checking availability again
        }

        return this.switchProfileTimer.isActive(); // Still waiting to check availability
    }

}
