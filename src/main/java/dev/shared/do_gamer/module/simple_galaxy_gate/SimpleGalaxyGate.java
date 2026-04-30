package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.github.manolo8.darkbot.backpage.entities.ShipInfo;
import com.github.manolo8.darkbot.config.NpcExtraFlag;
import com.github.manolo8.darkbot.config.types.suppliers.BrowserApi;
import com.github.manolo8.darkbot.core.itf.NpcExtraProvider;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.GateNpcFlag;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.GateHandler;
import dev.shared.do_gamer.utils.BackpageHelper;
import dev.shared.do_gamer.utils.PetGearHelper;
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
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.ExtensionsAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.util.Timer;

@Feature(name = "Simple Galaxy Gate", description = "Supports ABG, Delta, Epsilon, Zeta, Hades, Kuiper, LoW, Invasion, Trinity, DSE, Mimesis and EBG.")
public final class SimpleGalaxyGate implements Module, Task,
        Configurable<SimpleGalaxyGateConfig>,
        NpcExtraProvider {

    private final PluginAPI api;
    public final HeroAPI hero;
    public final MovementAPI movement;
    public final EntitiesAPI entities;
    public final CustomLootModule lootModule;
    public final CustomCollectorModule collectorModule;
    public final StarSystemAPI starSystem;
    public final MapTraveler traveler;
    public final PortalJumper jumper;
    public final BotAPI bot;
    public final BackpageHelper backpageHelper;
    private final ExtensionsAPI extensionsAPI;
    private final ConfigAPI configApi;
    public final GameScreenAPI gameScreenApi;
    private final RepairAPI repairAPI;

    public final ConfigSetting<BrowserApi> botBrowserApi;

    private static final Pattern GENERAL_MAP_PATTERN = Pattern.compile("^([1-5]-[1-8]|[1-3]BL)$");
    private final Timer stuckInGateTimer = Timer.get();
    private final Timer switchProfileTimer = Timer.get(120_000L);
    private final Timer gateCompletionDelayTimer = Timer.get(3_000L);
    private boolean triedReloadOnStuck = false;
    private boolean isStuckInGate = false;
    private boolean shouldMoveToRefinery = false;
    private boolean updateHangarData = true;
    private boolean gateVisited = false;
    private boolean canSwitchProfile = false;
    private boolean fetchServerOffset = false;
    private boolean safeRefreshInGate = false;
    private boolean showBoxCount = true;
    private boolean showCompletedGates = true;
    private int completedGates = 0;

    private final GateBuilder gateBuilder;
    public final PetGearHelper petGearHelper;

    private SimpleGalaxyGateConfig config;
    private String statusDetails = null;

    public PluginAPI getApi() {
        return this.api;
    }

    public SimpleGalaxyGate(PluginAPI api) {
        this.api = api;
        this.hero = api.requireAPI(HeroAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.lootModule = new CustomLootModule(api);
        this.collectorModule = new CustomCollectorModule(api);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.traveler = api.requireInstance(MapTraveler.class);
        this.jumper = api.requireInstance(PortalJumper.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.backpageHelper = new BackpageHelper(api);
        this.extensionsAPI = api.requireAPI(ExtensionsAPI.class);
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.gameScreenApi = api.requireAPI(GameScreenAPI.class);
        this.repairAPI = api.requireAPI(RepairAPI.class);

        this.botBrowserApi = this.configApi.requireConfig("bot_settings.api_config.browser_api");
        this.lootModule.setCollector(this.collectorModule); // Link collector module
        this.gateBuilder = new GateBuilder(this, api);
        this.petGearHelper = new PetGearHelper(api);
    }

    @Override
    public void setConfig(ConfigSetting<SimpleGalaxyGateConfig> config) {
        this.config = config.getValue();
        // Make sure the modules receive the same configuration instance.
        this.lootModule.setModuleConfig(this.config);
        this.collectorModule.setModuleConfig(this.config);
    }

    @Override
    public NpcExtraFlag[] values() {
        return GateNpcFlag.values();
    }

    @Override
    public String getStatus() {
        String state = StateStore.current().message;
        StringBuilder status = new StringBuilder(String.format("Simple GG | %s", state));

        switch (StateStore.current()) {
            case TRAVELING_TO_GATE:
                this.appendTravelingStatus(status);
                break;
            case BUILDING:
                this.appendBuildingStatus(status);
                break;
            case ATTACKING:
            case COLLECTING:
            case KAMIKAZE:
            case GUARDING:
            case REPAIRING:
                this.appendGateStatus(status);
                break;
            case WAITING:
                this.appendWaitingStatus(status);
                break;
            default:
                break;
        }

        this.appendCompletedGatesStatus(status);
        this.appendDebugInfo(status);
        return status.toString();
    }

    private void appendTravelingStatus(StringBuilder status) {
        if (this.gateBuilder.isSwitchingShip()) {
            status.append(": Switching Ship");
        } else {
            status.append(String.format(": %s", Maps.mapNameForGate(this.config.gateId)));
        }
    }

    private void appendBuildingStatus(StringBuilder status) {
        if (this.gateBuilder.isSwitchingShip()) {
            status.append(": Switching Ship");
        } else if (this.gateBuilder.isBuildState()) {
            status.append(String.format(": %s", Maps.mapNameForGate(this.config.gateId)));
        } else {
            status.append(": Waiting...");
        }
    }

    private void appendGateStatus(StringBuilder status) {
        status.append(String.format(" | NPC: %d", this.lootModule.getNpcs().size()));
        // Show box count if enabled in gate handler
        if (this.showBoxCount) {
            status.append(String.format(" | Box: %d", this.collectorModule.count()));
        }
        // Show additional status details if provided by gate handler
        if (this.statusDetails != null && !this.statusDetails.isEmpty()) {
            status.append(String.format(" | %s", this.statusDetails));
        }
    }

    private void appendWaitingStatus(StringBuilder status) {
        if (this.statusDetails != null && !this.statusDetails.isEmpty()) {
            status.append(String.format(": %s", this.statusDetails));
        }
    }

    /**
     * Appends the number of completed gates.
     */
    private void appendCompletedGatesStatus(StringBuilder status) {
        if (this.completedGates > 0 && this.showCompletedGates) {
            status.append(String.format("%nCompleted: %d", this.completedGates));
        }
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
                int heroMapId = this.hero.getMap().getId();
                status.append(String.format("%nPosition: X: %.0f, Y: %.0f | %s | MapID: %d",
                        heroX, heroY, heroAction, heroMapId));
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
        return (!this.isMapGG() && StateStore.current() != StateStore.State.BUILDING) || this.safeRefreshInGate;
    }

    public void setShouldMoveToRefinery(boolean shouldMoveToRefinery) {
        this.shouldMoveToRefinery = shouldMoveToRefinery;
    }

    public void setUpdateHangarData(boolean updateHangarData) {
        this.updateHangarData = updateHangarData;
    }

    public void setCanSwitchProfile(boolean canSwitchProfile) {
        this.canSwitchProfile = canSwitchProfile;
    }

    public SimpleGalaxyGateConfig getConfig() {
        return this.config;
    }

    @Override
    public void onTickTask() {
        // Reset gate visited and stuck timer if ship is destroyed
        if (this.repairAPI.isDestroyed()) {
            this.gateVisited = false;
            this.deactivateStuckInGateTimer();
            this.gateCompletionDelayTimer.disarm();
        }
    }

    @Override
    public void onBackgroundTick() {
        if (!this.backpageHelper.isValid()) {
            return; // BackpageManager not valid, skip background tasks
        }

        if (this.fetchServerOffset) {
            ServerTimeHelper.fetchServerOffset(this.backpageHelper.getInstance());
        }

        if (this.updateHangarData) {
            this.backpageHelper.getLegacyHangarManager().updateHangarData(500);
            this.setUpdateHangarData(false);
        }

        // Populate ship dropdown if empty
        Map<String, String> ships = SimpleGalaxyGateConfig.BuilderSettings.ShipDropdown.getShips();
        if (ships.isEmpty()) {
            List<ShipInfo> shipInfos = this.backpageHelper.getLegacyHangarManager().getShipInfos();
            if (shipInfos.isEmpty()) {
                this.setUpdateHangarData(true);
                return;
            }
            shipInfos.stream()
                    .filter(si -> si.getOwned() == 1)
                    .sorted(Comparator.comparing(ShipInfo::getFav).reversed())
                    .forEach(si -> ships.put(si.getHangarId(), si.getLootId()));
        }
    }

    @Override
    public String getStoppedStatus() {
        if (this.statusDetails != null && !this.statusDetails.isEmpty()) {
            String state = StateStore.State.WAITING.message;
            return String.format("Simple GG (paused) | %s: %s", state, this.statusDetails);
        }
        return null;
    }

    @Override
    public void onTickStopped() {
        if (this.config == null) {
            return;
        }
        this.stuckInGateTimer.disarm();
        this.switchProfileTimer.disarm();
        this.gateCompletionDelayTimer.disarm();
        this.gateBuilder.reset();
        // Call stopped tick logic for the current gate
        GateHandler gateHandler = this.createGateHandler();
        gateHandler.stoppedTickModule();
        gateHandler.reset();
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

        // Create gate handler instance
        GateHandler gateHandler = this.createGateHandler();

        // Handle Galaxy Gate map
        if (this.isMapGG()) {
            this.setShouldMoveToRefinery(true);
            this.gateBuilder.reset(); // Reset build state
            this.gateVisited = true; // Mark gate as visited
            this.setCanSwitchProfile(true); // Allow profile switching after visiting gate
            this.handleGalaxyGate(gateHandler);
            return;
        }

        // Reset stuck timer when not in gate map
        this.deactivateStuckInGateTimer();

        // Handle gate completion if we have visited a gate
        if (this.handleGateCompletion()) {
            return;
        }

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
        if (this.gateBuilder.tick()) {
            StateStore.request(StateStore.State.BUILDING);
            this.moveToRefinery(); // Stay near refinery while building
            this.petGearHelper.disable(); // Disable pet while building
            return;
        }

        StateStore.request(StateStore.State.WAITING);
    }

    /**
     * Create a GateHandler instance.
     */
    private GateHandler createGateHandler() {
        Integer gateId = this.config != null ? this.config.gateId : null;
        GateHandler handler = Maps.getGateHandler(gateId, this);
        Maps.setMapCenterX(handler.getMapCenterX());
        Maps.setMapCenterY(handler.getMapCenterY());
        Maps.setToleranceDistance(handler.getToleranceDistance());
        this.fetchServerOffset = handler.isFetchServerOffset();
        this.safeRefreshInGate = handler.canSafeRefreshInGate();
        this.showBoxCount = handler.isShowBoxCount();
        this.showCompletedGates = handler.isShowCompletedGates();
        this.statusDetails = handler.getStatusDetails();
        return handler;
    }

    /**
     * Handles the logic when in a Galaxy Gate map.
     */
    private void handleGalaxyGate(GateHandler gateHandler) {
        this.lootModule.setGateHandler(gateHandler); // Link gate handler to loot module

        // Reset stuck timer when not waiting in gate
        if (StateStore.current() != StateStore.State.WAITING_IN_GATE) {
            this.deactivateStuckInGateTimer();
        }

        // Attack NPCs
        if (!this.lootModule.getNpcs().isEmpty()) {
            StateStore.request(StateStore.State.ATTACKING);
            this.lootModule.onTickModule();
            return;
        }

        // No NPCs, collect boxes or jump to next map
        StateStore.request(StateStore.State.COLLECTING);
        this.hero.setRunMode();
        if (gateHandler.collectTickModule()) {
            return; // Gate handler took action, skip default collection
        }
        this.collectorModule.onTickModule();

        if (gateHandler.isJumpToNextMap() && this.canJump()) {
            // Jump to next map
            this.jumpToNextMap();
            return;
        }
        if (this.collectorModule.hasNoBox()) {
            // No boxes to collect, move to center
            StateStore.request(StateStore.State.WAITING_IN_GATE);
            if (!this.handleStuckInGate() && gateHandler.isMoveToCenter()) {
                this.moveToCenter();
            }
        }
    }

    /**
     * Moves the hero to the center of the map.
     */
    private void moveToCenter() {
        double offset = 500.0;
        double x = (Maps.getMapCenterX() - offset);
        double y = (Maps.getMapCenterY() - offset);
        this.moveToPosition(x, y, 250.0);
    }

    /**
     * Moves the hero to the specified position
     * if far enough and movement is possible.
     */
    public boolean moveToPosition(double x, double y, double gap) {
        if (this.hero.distanceTo(x, y) > gap && this.movement.canMove(x, y)) {
            this.movement.moveTo(x, y);
            return true;
        }
        return false; // Already close enough or cannot move, do nothing
    }

    /**
     * Handles the logic for when the hero is stuck in the gate.
     */
    private boolean handleStuckInGate() {
        if (!this.stuckInGateTimer.isArmed()) {
            if (StateStore.current() == StateStore.State.WAITING_IN_GATE && !this.movement.isMoving()) {
                this.activateStuckInGateTimer(false); // Activate stuck timer
            }
            return false;
        }

        if (this.stuckInGateTimer.isInactive()) {
            this.isStuckInGate = true; // Mark as stuck when timer expires
            if (!this.triedReloadOnStuck) {
                // First try to reload the game
                this.triedReloadOnStuck = true;
                System.out.println("Ship seems stuck in gate, refreshing the game...");
                this.bot.handleRefresh();
                this.activateStuckInGateTimer(false);
                return true;
            } else {
                // Else move to radiation to destroy the ship
                if (!this.moveToPosition(Maps.getMapCenterX(), 0, 50.0) && !this.movement.isMoving()) {
                    // Remain stuck after moving to radiation,
                    // try refreshing again on next timer expiration
                    this.triedReloadOnStuck = false;
                    this.activateStuckInGateTimer(true); // Activate extended timer
                }

                return true;
            }
        }

        // If hero is moving, reset the stuck timer
        if (this.movement.isMoving()) {
            this.deactivateStuckInGateTimer();
        }

        // Return whether we are currently considered stuck in gate
        return this.isStuckInGate;
    }

    /**
     * Activates the stuck in gate timer if configured.
     *
     * @param extended if true, the timer duration is doubled
     */
    private void activateStuckInGateTimer(boolean extended) {
        if (this.config.other.stuckInGateTimerMinutes > 0) {
            long timeout = this.config.other.stuckInGateTimerMinutes * 60_000L;
            if (extended) {
                timeout *= 2;
            }
            this.stuckInGateTimer.activate(timeout);
        }
    }

    /**
     * Deactivates the stuck in gate timer and resets related flag.
     */
    private void deactivateStuckInGateTimer() {
        this.stuckInGateTimer.disarm();
        this.triedReloadOnStuck = false;
        this.isStuckInGate = false;
    }

    /**
     * Determines if the current map is a Galaxy Gate map (not a general map).
     */
    public boolean isMapGG() {
        GameMap currentMap = this.starSystem.getCurrentMap();
        if (currentMap == null) {
            return false;
        }
        String name = currentMap.getShortName();
        // Except general maps like 1-1, 2-3, 3BL, etc.
        return !GENERAL_MAP_PATTERN.matcher(name).matches();
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
            if (!this.gateBuilder.switchToGateShip()) {
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

        if (!Maps.isGateAccessibleFromCurrentMap(gateId, this.starSystem)) {
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
    public boolean isGateAvailable(int gateId) {
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
    public boolean moveToRefinery() {
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
            this.setShouldMoveToRefinery(false);
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
     * Handles the logic to jump to the next map.
     */
    public void jumpToNextMap() {
        Portal portal = this.findNextPortal();
        if (portal != null) {
            StateStore.request(StateStore.State.JUMPING);
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
     * Resolves conflicts with other Galaxy Gate related features by disabling them.
     */
    private void conflictResolver() {
        Set<String> conflicts = Set.of(
                "eu.darkbot.popcorn.don.GGSpinner",
                "com.pikapika.behaviour.gateSpinShipChanger.GateSpinShipChanger",
                "com.pikapika.behaviour.refreshGateComplete.RefreshGateComplete",
                "eu.darkbot.leanon00.botFeatures.TempReturnWindowFix",
                "eu.darkbot.leanon00.botFeatures.Debug",
                // old version of LeanPlugin
                "eu.darkbot.leanon00.Main.Features.TempReturnWindowFix",
                "eu.darkbot.leanon00.Main.Features.Debug");

        for (String featureId : conflicts) {
            FeatureInfo<?> featureInfo = this.extensionsAPI.getFeatureInfo(featureId);
            if (featureInfo != null && featureInfo.isEnabled()) {
                featureInfo.setEnabled(false); // Disable conflicting feature
            }
        }
    }

    /**
     * Handles the logic for when a gate has been visited.
     */
    private boolean handleGateCompletion() {
        if (this.gateVisited) {
            // Activate completion delay timer
            if (!this.gateCompletionDelayTimer.isArmed()) {
                this.gateCompletionDelayTimer.activate();
            }
            if (this.gateCompletionDelayTimer.isInactive()) {
                if (this.showCompletedGates) {
                    this.completedGates++; // Increment completed gates count
                } else {
                    this.completedGates = 0; // Reset if not showing
                }
                this.gateVisited = false; // Reset for next gate
                this.gateCompletionDelayTimer.disarm();
            }
            StateStore.request(StateStore.State.WAITING);
            return true; // In completion delay
        }
        return false;
    }

    /**
     * Checks if the gate was visited then switches to the specified profile.
     */
    private boolean switchProfile() {
        if (!this.canSwitchProfile) {
            return false;
        }

        if (this.config.other.onlyWhenNotAvailable) {
            if (StateStore.current() != StateStore.State.WAITING) {
                this.switchProfileTimer.disarm(); // Reset timer
                return false;
            }
            // activate timer to make sure there is no more activity before switching
            if (!this.switchProfileTimer.isArmed()) {
                this.switchProfileTimer.activate();
                return false; // Wait before checking availability again
            }

            if (this.switchProfileTimer.isActive()) {
                return false; // Still waiting to check availability
            }
        }

        this.setCanSwitchProfile(false); // Reset for next time
        this.switchProfileTimer.disarm(); // Reset timer
        if (this.config.other.botProfile != null) {
            // Switch to the specified profile
            this.configApi.setConfigProfile(this.config.other.botProfile);
            return true;
        }
        return false;
    }

}
