package dev.shared.orbithelper.behaviours.fast_travel;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import dev.shared.orbithelper.config.FastTravelConfig;
import dev.shared.utils.CaptchaBoxDetector;
import dev.shared.utils.TemporalModuleDetector;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.items.Item;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.ItemTimer;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.game.stats.Stats;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.shared.modules.TemporalModule;
import eu.darkbot.util.Timer;

@Feature(name = "Fast Travel", description = "Fast travel between maps using Jump CPU (AJP-01).")
public class FastTravel extends TemporalModule implements Behavior, Configurable<FastTravelConfig> {
    private final Random random = new Random();
    private final ConfigAPI configApi;
    private final StarSystemAPI starSystem;
    private final HeroAPI hero;
    private final HeroItemsAPI items;
    private final StatsAPI stats;
    private final MovementAPI movement;
    private final EntitiesAPI entities;
    private final GameScreenAPI gameScreen;
    private final AttackAPI attack;
    private static final long VALIDATION_RETRY_INTERVAL_MS = 5_000L;
    private static final int MAX_CONSECUTIVE_GLOBAL_TIMEOUTS = 3;

    private FastTravelConfig config;
    private final Timer timer = Timer.get();

    // State Tracking
    private State state = State.VALIDATING;
    private long cpuStartTime = 0;
    private boolean selectedRandom = false;
    private int consecutiveGlobalTimeouts = 0;

    public FastTravel(PluginAPI api) {
        super(api.requireAPI(BotAPI.class));
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.gameScreen = api.requireAPI(GameScreenAPI.class);
        this.attack = api.requireAPI(AttackAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<FastTravelConfig> config) {
        this.config = config.getValue();
    }

    // Behavior Logic (Runs Always)
    @Override
    public void onTickBehavior() {
        if (this.config == null
                || !this.config.enabled
                || this.isRestrictedModule() // Restricted Module active
                || CaptchaBoxDetector.hasCaptchaBoxes(this.entities) // Captcha is active
                || this.attack.isAttacking() // Currently Attacking
                || this.isUnderAttack() // Is under attack
                || this.isMovingOrJumping() // Currently moving or jumping
        ) {
            this.resetState();
            return;
        }

        if (this.state != State.VALIDATING) {
            this.bot.setModule(this); // Keep control
            return;
        }

        this.handleValidating();
    }

    private void resetState() {
        if (this.state == State.VALIDATING) {
            return;
        }

        Gui spacemap = this.getSpaceMap();
        if (spacemap != null) {
            spacemap.setVisible(false);
        }

        this.state = State.VALIDATING;
        this.cpuStartTime = 0;
        this.selectedRandom = false;
        this.timer.disarm();
        this.goBack();
    }

    private enum State {
        VALIDATING,
        SAFE_POSITIONING,
        OPENING_CPU,
        SELECTING_MAP,
        JUMPING,
        WAITING_FOR_JUMP
    }

    // Module Logic (Runs when we are active)
    @Override
    public void onTickModule() {
        switch (this.state) {
            case SAFE_POSITIONING:
                this.handleSafePositioning();
                break;
            case OPENING_CPU:
                this.handleOpeningCpu();
                break;
            case SELECTING_MAP:
                this.handleSelectingMap();
                break;
            case JUMPING:
                this.handleJumping();
                break;
            case WAITING_FOR_JUMP:
                this.handleWaitingForJump();
                break;
            default:
                this.resetState();
        }
    }

    @Override
    public String getStatus() {
        String msg;
        switch (this.state) {
            case SAFE_POSITIONING:
                msg = "Moving to safe spot...";
                break;
            case OPENING_CPU:
                msg = "Opening CPU...";
                break;
            case SELECTING_MAP:
                msg = "Selecting map...";
                break;
            case JUMPING:
                msg = "Jumping...";
                break;
            case WAITING_FOR_JUMP:
                msg = "Waiting for jump...";
                break;
            default:
                msg = this.state.name();
        }
        return "Fast Travel: " + msg;
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    private Constants.Coordinate getMapCoordinates(String mapName) {
        return Constants.MAP_COORDINATES.get(mapName);
    }

    private String currentMap() {
        return this.starSystem.getCurrentMap().getShortName();
    }

    private String destinationMap() {
        int mapId = this.configApi.getConfigValue("general.working_map");
        String map = this.starSystem.getOrCreateMap(mapId).getShortName();

        if (map.matches("^5-[1-4]$")) {
            // Special case for 5-x maps to go to 4-5
            map = "4-5";
        } else if (map.matches("^[1-3]BL$") && !this.currentMap().matches("^[1-3]-8$")) {
            // Special case for BL maps to go to x-8
            map = map.charAt(0) + "-8";
        } else if (map.matches("^[1-3]-1$")) {
            // Special case for enemy x-1 map to go to enemy x-2
            char factionChar = map.charAt(0);
            EntityInfo.Faction faction = this.hero.getEntityInfo().getFaction();
            if ((faction == EntityInfo.Faction.MMO && factionChar != '1')
                    || (faction == EntityInfo.Faction.EIC && factionChar != '2')
                    || (faction == EntityInfo.Faction.VRU && factionChar != '3')) {
                // Is enemy home map, go to enemy x-2 instead
                map = factionChar + "-2";
            }
        }

        return map;
    }

    private void handleValidating() {
        // Cooldown check
        if (this.timer.isActive()) {
            return;
        }

        if (TemporalModuleDetector.using(this.bot).isTemporalNotMap()) {
            this.resetState();
            return; // Avoid conflicts with other temporal modules
        }

        if (this.isValid()) {
            this.state = State.SAFE_POSITIONING;
            this.bot.setModule(this); // Take control
            return;
        }

        this.timer.activate(VALIDATION_RETRY_INTERVAL_MS); // Recheck in 5s
    }

    private void handleSafePositioning() {
        Entity safeSpot = this.findNearestSafeSpot();
        if (safeSpot == null) {
            this.resetState();
            return;
        }

        if (this.hero.distanceTo(safeSpot) < 200) {
            if (this.hero.isMoving()) {
                this.movement.stop(false);
                return;
            }
            this.state = State.OPENING_CPU;
            this.cpuStartTime = System.currentTimeMillis();
        } else {
            this.movement.moveTo(safeSpot);
        }
    }

    private void handleOpeningCpu() {
        if (this.isGlobalTimeout()) {
            return;
        }

        if (this.getSpaceMap() != null) {
            this.state = State.SELECTING_MAP;
            this.timer.activate(2000); // 2s delay
            return;
        }

        if (this.timer.isActive() || !this.isAvailableCpu()) {
            return;
        }

        this.items.useItem(SelectableItem.Cpu.AJP_01, ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE,
                ItemFlag.NOT_SELECTED);

        this.timer.activate(2500);
    }

    private void handleSelectingMap() {
        if (this.isGlobalTimeout() || this.timer.isActive()) {
            return;
        }

        Gui spacemap = this.getSpaceMap();
        if (spacemap == null) {
            this.state = State.OPENING_CPU; // Re-open if closed
            return;
        }

        String destMap = this.destinationMap();

        if (!this.selectedRandom) {
            // Find random map
            String currentMap = this.currentMap();
            List<String> candidates = Constants.ALLOWED_MAPS.stream()
                    .filter(m -> !m.equals(currentMap) && !m.equals(destMap))
                    .collect(Collectors.toList());

            String randomMap = candidates.get(this.random.nextInt(candidates.size()));

            // Click random map first
            Constants.Coordinate p = this.getMapCoordinates(randomMap);
            spacemap.click(p.x, p.y);

            this.selectedRandom = true;
            return;
        }

        // Click destMap
        Constants.Coordinate p = this.getMapCoordinates(destMap);
        spacemap.click(p.x, p.y);

        this.state = State.JUMPING;
        this.timer.activate(1000);
        this.selectedRandom = false;
    }

    private void handleJumping() {
        if (this.isGlobalTimeout() || this.timer.isActive()) {
            return;
        }

        Gui spacemap = this.getSpaceMap();
        if (spacemap == null) {
            this.state = State.OPENING_CPU; // Re-open if closed
            return;
        }

        Constants.Coordinate jumpP = this.getMapCoordinates("JUMP");

        spacemap.click(jumpP.x, jumpP.y);

        this.state = State.WAITING_FOR_JUMP;
        this.timer.activate(2000); // Wait bit before checking map
    }

    private void handleWaitingForJump() {
        if (this.isGlobalTimeout() || this.timer.isActive()) {
            return;
        }

        String currentMap = this.currentMap();
        String destMap = this.destinationMap();

        if (currentMap.equals(destMap)) {
            // Reset the consecutive timeout counter when we've arrived
            this.consecutiveGlobalTimeouts = 0;
            this.resetState();
            return;
        }

        // Loop check every 1s
        this.timer.activate(1000);
    }

    private boolean isGlobalTimeout() {
        if (this.cpuStartTime > 0
                && (System.currentTimeMillis() - this.cpuStartTime) > (this.config.maxJumpAttemptTime * 1_000L)) {
            this.resetState();
            // After 3 consecutive global timeouts, suggest a game refresh
            if (this.consecutiveGlobalTimeouts >= MAX_CONSECUTIVE_GLOBAL_TIMEOUTS) {
                System.out.println("Fast Travel: Requested game refresh due to consecutive timeouts.");
                this.consecutiveGlobalTimeouts = 0; // Reset counter
                this.bot.handleRefresh();
                return true;
            }
            // Increment consecutive timeout counter
            this.consecutiveGlobalTimeouts++;
            return true;
        }
        return false;
    }

    private Entity findNearestSafeSpot() {
        // Find nearest Portal that is NOT a Galaxy Gate
        Portal nearestPortal = this.entities.getPortals().stream()
                .filter(p -> p.getTargetMap().map(m -> !m.isGG()).orElse(false))
                .min((p1, p2) -> Double.compare(this.hero.distanceTo(p1), this.hero.distanceTo(p2)))
                .orElse(null);

        // Find nearest Base Station (Refinery/Repair)
        Station nearestStation = this.entities.getStations().stream()
                .filter(s -> s instanceof Station.Refinery || s instanceof Station.Repair)
                .min((s1, s2) -> Double.compare(this.hero.distanceTo(s1), this.hero.distanceTo(s2)))
                .orElse(null);

        if (nearestPortal == null) {
            return nearestStation;
        }
        if (nearestStation == null) {
            return nearestPortal;
        }

        // Return the closer one
        return this.hero.distanceTo(nearestPortal) < this.hero.distanceTo(nearestStation) ? nearestPortal
                : nearestStation;
    }

    private boolean isValid() {
        String currentMap = this.currentMap();
        String destMap = this.destinationMap();

        if (currentMap.equals(destMap) // Already in destination
                || !Constants.ALLOWED_MAPS.contains(currentMap) // Current map not allowed
                || !Constants.ALLOWED_MAPS.contains(destMap) // Destination map not allowed
                || this.isSiblingMap(destMap) // Sibling map (use portal instead)
                || !this.levelAccessible(destMap) // Level restriction
                || !this.isAvailableCpu() // CPU not available
                || !this.canJump() // Cannot afford jump
        ) {
            return false;
        }

        // Check jump distance
        int jumps = this.getShortestPath(currentMap, destMap);
        return (jumps >= this.config.minJumps);
    }

    private int getShortestPath(String start, String end) {
        if (!Constants.MAP_CONNECTIONS.containsKey(start) || !Constants.MAP_CONNECTIONS.containsKey(end)) {
            return -1;
        }

        if (start.equals(end)) {
            return 0;
        }

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> distance = new java.util.HashMap<>();

        queue.add(start);
        visited.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distance.get(current);

            if (current.equals(end)) {
                return currentDist;
            }

            for (String neighbor : Constants.MAP_CONNECTIONS.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distance.put(neighbor, currentDist + 1);
                    queue.add(neighbor);

                    if (neighbor.equals(end)) {
                        return currentDist + 1;
                    }
                }
            }
        }

        return -1; // No path found
    }

    private boolean canJump() {
        int coupons = (int) this.stats.getStat(Stats.General.TELEPORT_BONUS_AMOUNT).getCurrent();
        if (this.config.useJumpCoupon) {
            // Use only coupons
            return coupons > 0;
        }

        // Use coupons or uridium if no coupons (max jump cost is 850 uridium)
        int uri = (int) this.stats.getStat(Stats.General.URIDIUM).getCurrent();
        return coupons > 0 || uri >= 850;
    }

    private Optional<Item> getCpuItem() {
        return this.items.getItem(SelectableItem.Cpu.AJP_01, ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE,
                ItemFlag.NOT_SELECTED);
    }

    private boolean isAvailableCpu() {
        Optional<Item> itemOpt = this.getCpuItem();
        if (itemOpt.isEmpty()) {
            return false;
        }
        ItemTimer itemTimer = itemOpt.get().getTimer();
        if (itemTimer == null) {
            return true; // Timer not found, assume available
        }
        double availableIn = itemTimer.getAvailableIn();
        if (availableIn > 0) {
            this.timer.activate((long) (availableIn * 1000) + 500);
            return false;
        }
        return true;
    }

    private Gui getSpaceMap() {
        Gui spacemap = this.gameScreen.getGui("spacemap");
        if (spacemap != null && spacemap.isVisible()) {
            return spacemap;
        }
        return null;
    }

    // Check if under attack
    private boolean isUnderAttack() {
        return this.entities.getShips().stream().anyMatch(ship -> ship.isAttacking(this.hero))
                || this.entities.getNpcs().stream().anyMatch(npc -> npc.isAttacking(this.hero));
    }

    // Check if currently moving or jumping (to avoid interrupting)
    private boolean isMovingOrJumping() {
        if (this.state != State.VALIDATING && this.state != State.SAFE_POSITIONING) {
            return this.hero.isMoving() || this.entities.getPortals().stream().anyMatch(Portal::isJumping);
        }
        return false; // Only consider moving/jumping if we are in the process of fast traveling
    }

    // Check if destination map is sibling to current map
    private boolean isSiblingMap(String destMap) {
        return this.entities.getPortals().stream()
                .anyMatch(p -> p.getTargetMap().map(m -> m.getShortName().equals(destMap)).orElse(false));
    }

    // Check if the player's level allows access to the destination map
    private boolean levelAccessible(String destMap) {
        int level = this.stats.getLevel();
        if (level >= 17) {
            return true;
        }

        Integer pvpLevel = Constants.PVP_LEVELS.get(destMap);
        if (pvpLevel != null) {
            return level >= pvpLevel;
        }

        EntityInfo.Faction faction = this.hero.getEntityInfo().getFaction();
        Map<String, Integer> factionMap = Constants.FACTION_LEVELS.get(faction);
        if (factionMap == null) {
            return false;
        }
        Integer reqLevel = factionMap.get(destMap);
        return reqLevel != null && level >= reqLevel;
    }

    // Check if current module is restricted for fast travel
    private boolean isRestrictedModule() {
        String module = this.configApi.getConfigValue("general.current_module");
        if (module.isEmpty()) {
            return false;
        }
        String[] parts = module.split("\\.");
        String name = parts[parts.length - 1];
        return Constants.RESTRICTED_MODULES.contains(name);
    }
}
