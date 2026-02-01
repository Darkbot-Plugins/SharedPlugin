package dev.shared.do_gamer.module.spaceball;

import java.time.Duration;
import java.time.LocalDateTime;

import dev.shared.do_gamer.config.SpaceballConfig;
import dev.shared.do_gamer.utils.PetGearHelper;
import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.BoxInfo;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.InstructionProvider;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;

@Feature(name = "SpaceBall", description = "Attack SpaceBall without fleeing away and collect cargo boxes around the gate")
public class Spaceball implements Module, Task, Configurable<SpaceballConfig>, InstructionProvider {

    private final CustomLootModule loot;
    private final CustomCollectorModule collector;
    private final ConfigAPI configApi;
    private final EntitiesAPI entities;
    private final BotAPI bot;
    private final HeroAPI hero;
    private final MovementAPI movement;
    private final StarSystemAPI starSystem;
    private final BackpageAPI backpage;
    private final MapTraveler traveler;
    private final PetGearHelper petGearHelper;
    private boolean isSpaceball;
    private long lastTargetLostTime;
    private SpaceballConfig config;
    private int nullTargetCounter; // Counter for consecutive null targets
    private int reloadCounter; // Counter for consecutive reloads
    private static final String TERGET_MAP = "4-4";
    private static final String NPC_NAME = "SpaceBall";
    private static final String BOX_NAME = "FROM_SHIP";
    private static final int MAX_TARGET_DISTANCE = 1000; // max distance to keep target considered in range
    private static final int MAX_NULL_TARGETS_BEFORE_REFRESH = 3;
    private static final int MAX_RELOAD_ATTEMPTS = 3;
    private boolean autoStart; // Flag to auto start
    private boolean start; // Flag to start the bot
    private boolean stop; // Flag to stop the bot
    private double previousDistanceToExitGate = -1; // Initialize with an invalid value
    private long lastDirectionCheckTime = 0; // Timestamp for the last direction check
    private long lastUnderAttackTime = 0; // Timestamp for the last under attack check

    public static final String DIRECTION_OWN = "Own";
    public static final String DIRECTION_ENEMY = "Enemy";
    public static final String DIRECTION_NEUTRAL = "Neutral";

    private String direction = DIRECTION_NEUTRAL;

    public Spaceball(PluginAPI api, MapTraveler traveler) {
        this.loot = new CustomLootModule(api);
        this.collector = new CustomCollectorModule(api);
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.backpage = api.requireAPI(BackpageAPI.class);
        this.traveler = traveler;
        this.petGearHelper = new PetGearHelper(api);
        this.isSpaceball = false;
        this.lastTargetLostTime = 0;
        this.nullTargetCounter = 0;
        this.reloadCounter = 0;
        this.autoStart = false;
        this.start = false;
        this.stop = false;

        this.initConfig();
    }

    @Override
    public String instructions() {
        return "Recommended settings:\n" +
                "- In-game -> Settings -> Show cargo boxes\n" +
                "- Bot -> Collect -> Set " + BOX_NAME + " to collect with a wait 50-100ms\n" +
                "- Bot -> Npc killer -> " + NPC_NAME + ":\n" +
                "      Set to kill with a radius 400-500\n" +
                "      Enable Ignore Ownership and Ignore Attacked (in the Extra Column)\n" +
                "- Bot -> Safety places -> Set Portals to jump: Never.";
    }

    private void initConfig() {
        // Configure working map
        int mapId = this.starSystem.getOrCreateMap(TERGET_MAP).getId();
        if (!this.configApi.requireConfig("general.working_map").getValue().equals(mapId)) {
            this.configApi.requireConfig("general.working_map").setValue(mapId);
        }

        // Configure cargo box settings
        BoxInfo box;
        String[] boxes = { BOX_NAME, "CANDY_CARGO", "TURKEY_CARGO" };
        for (String boxName : boxes) {
            box = this.configApi.getLegacy().getOrCreateBoxInfo(boxName);
            if (!box.shouldCollect()) {
                box.setShouldCollect(true);
                box.setWaitTime(100);
            }
        }

        // Configure SpaceBall NPC settings
        NpcInfo npc = this.configApi.getLegacy().getOrCreateNpcInfo(NPC_NAME);
        if (!npc.getShouldKill()) {
            npc.addMapId(mapId);
            npc.setShouldKill(true);
            npc.setRadius(500);
            npc.setExtraFlag(NpcFlag.IGNORE_OWNERSHIP, true);
            npc.setExtraFlag(NpcFlag.IGNORE_ATTACKED, true);
        }
    }

    private String getExitMap() {
        EntityInfo.Faction faction = this.hero.getEntityInfo().getFaction();
        String exitMap;

        switch (faction) {
            case MMO:
                exitMap = "1-5"; // MMO exit map
                break;
            case EIC:
                exitMap = "2-5"; // EIC exit map
                break;
            case VRU:
                exitMap = "3-5"; // VRU exit map
                break;
            default:
                exitMap = "";
        }

        return exitMap;
    }

    @Override
    public boolean canRefresh() {
        // Check if the bot can refresh
        return this.targetOutOfRange() && this.targetDelay() < 0;
    }

    @Override
    public String getStatus() {
        StringBuilder status = new StringBuilder("SpaceBall: ");

        if (this.stop) {
            this.buildStoppingStatus(status);
            return status.toString();
        }

        this.buildRunningStatus(status);
        this.appendCountersAndDelay(status);
        this.appendAdditionalStatus(status);
        this.appendTimeStatus(status);

        return status.toString();
    }

    private void buildStoppingStatus(StringBuilder status) {
        status.append("Stopping bot due to ");
        if (!this.isRunningTime()) {
            status.append("stop time...");
        } else {
            status.append("excessive reloads...");
        }

        this.appendTimeStatus(status);
    }

    private void buildRunningStatus(StringBuilder status) {
        if (this.direction.equals(DIRECTION_NEUTRAL) && !this.isTargetSpaceBall()) {
            status.append("Roaming");
        } else {
            String modeInfo = getModeInfo();
            status.append(String.format("%s - %s", this.direction, modeInfo));
        }
    }

    private String getModeInfo() {
        switch (this.config.mode) {
            case SpaceballConfig.ModeOptions.ATTACK:
                return "Attacking";
            case SpaceballConfig.ModeOptions.FOLLOW:
                return "Following";
            default:
                return this.direction.equals(DIRECTION_ENEMY) ? "Following" : "Attacking";
        }
    }

    private void appendCountersAndDelay(StringBuilder status) {
        int delay = Math.max(0, (int) Math.ceil(this.targetDelay() / 1000.0));
        if (this.reloadCounter > 0 || this.nullTargetCounter > 0 || delay > 0) {
            status.append(" [");
            String space = "";
            if (this.reloadCounter > 0) {
                status.append(String.format("R%d", this.reloadCounter));
                space = " ";
            }
            if (this.nullTargetCounter > 0) {
                status.append(String.format("%sT%d", space, this.nullTargetCounter));
                space = " ";
            }
            if (delay > 0) {
                status.append(String.format("%sD%d", space, delay));
            }
            status.append("]");
        }
    }

    private void appendAdditionalStatus(StringBuilder status) {
        if (this.targetDelay() >= 0) {
            status.append(String.format("  |  Collect: %s", this.collector.getStatus()));
        } else if (this.loot.getAttacker().hasTarget() && !this.isTargetSpaceBall()) {
            status.append(String.format("  |  Kill: %s", this.loot.getStatus()));
        }
    }

    private void appendTimeStatus(StringBuilder status) {
        if (ServerTimeHelper.offsetUpdated() && !this.disabledTimeRestriction()) {
            LocalDateTime currentTime = ServerTimeHelper.currentDateTime();
            status.append(this.getTimeStatus(currentTime));
        }
    }

    @Override
    public String getStoppedStatus() {
        if (this.autoStart && !this.start) {
            StringBuilder status = new StringBuilder("SpaceBall: ");

            if (!ServerTimeHelper.offsetUpdated()) {
                status.append("Waiting for server time sync...");
            } else {
                this.buildAutoStartStatus(status);
            }

            return status.toString();
        }
        return null;
    }

    private void buildAutoStartStatus(StringBuilder status) {
        LocalDateTime currentTime = ServerTimeHelper.currentDateTime();
        LocalDateTime startTime = this.getTime(currentTime, this.config.time.startHour);
        if (startTime.isBefore(currentTime)) {
            startTime = startTime.plusDays(1); // Adjust start time to the next day if already passed
        }
        long secondsUntilStart = Duration.between(currentTime, startTime).getSeconds();
        if (secondsUntilStart > 0) {
            status.append(buildWaitingTimeString(secondsUntilStart));
        } else {
            status.append("Starting bot...");
        }
        // Append time
        status.append(this.getTimeStatus(currentTime));
    }

    private String buildWaitingTimeString(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder("Auto Start  |  Waiting time: ");
        if (hours > 0) {
            sb.append(String.format("%dh ", hours));
        }
        if (minutes > 0 || hours > 0) {
            sb.append(String.format("%dm ", minutes));
        }
        sb.append(String.format("%ds", secs));
        return sb.toString();
    }

    private String getTimeStatus(LocalDateTime currentTime) {
        String current = ServerTimeHelper.timeFormat(currentTime);
        String startTime = String.format("%02d:00", this.config.time.startHour);
        String stopTime = String.format("%02d:00", this.config.time.stopHour);
        String next = (this.config.time.stopHour < this.config.time.startHour) ? " (next day)" : "";

        return String.format("%n%nServer time: %s  |  Event time: %s - %s%s", current, startTime, stopTime, next);
    }

    public void onTickModule() {
        // Handle stop and exit first
        if (this.handleStopAndExit()) {
            return;
        }

        // Handle start or pause
        if (this.handleStartOrPause()) {
            return;
        }

        // Handle collector delay
        if (this.handleCollectorDelay()) {
            return;
        }

        // Main SpaceBall handling flow
        if (!this.isSpaceball) {
            this.handleNoSpaceballFlow();
        } else {
            this.handleSpaceballTargetFlow();
        }
    }

    private boolean handleStopAndExit() {
        if (!this.stop) {
            return false;
        }

        if (!this.getExitMap().isEmpty() && this.isOnTargetMap()) {
            this.moveToExit(); // Move to exit map
        } else {
            this.stopBot(); // Stop the bot
        }
        return true;
    }

    private boolean handleStartOrPause() {
        if (this.start) {
            return false;
        }

        if (this.isRunningTime()) {
            // Starting bot
            this.start = true; // Set start flag
            this.autoStart = false; // Reset auto start flag
            return false;
        }
        // Pausing bot
        this.bot.setRunning(false); // Stop the bot
        this.autoStart = true; // Set auto start flag
        return true;
    }

    private boolean handleCollectorDelay() {
        if (this.targetDelay() < 0) {
            return false;
        }

        this.collectorOnTick();
        return true;
    }

    private void handleNoSpaceballFlow() {
        if (this.loot.customFindTarget()) {
            if (this.isTargetSpaceBall()) {
                // Target is a SpaceBall, handle accordingly
                this.lootOnTick();
                if (!this.targetOutOfRange()) {
                    this.isSpaceball = true;
                    this.resetCounters();
                }
            } else {
                // Not a SpaceBall target, handle as normal
                this.petHandler(true);
                this.loot.onTickModule();
            }
        } else {
            this.collectorOnTick();
            if (!this.isLowHP()) { // Check if not been destroyed
                this.handleTargetLost();
            }
        }
    }

    private void handleSpaceballTargetFlow() {
        if (this.loot.getAttacker().hasTarget()) {
            if (this.isTargetSpaceBall()) {
                // Target is a SpaceBall, handle accordingly
                this.lootOnTick();
                if (this.targetOutOfRange()) {
                    this.isSpaceball = false;
                    this.loot.getAttacker().setTarget(null); // Clear target
                    this.direction = DIRECTION_NEUTRAL; // Reset direction
                    this.handleTargetLost();
                }
            } else {
                // Not a SpaceBall target, handle as normal and reset SpaceBall flag
                this.petHandler(true);
                this.loot.onTickModule();
                this.isSpaceball = false;
            }
        } else {
            this.collectorOnTick();
            this.isSpaceball = false;
        }
    }

    @Override
    public void onTickStopped() {
        if (this.autoStart && !this.start && this.isRunningTime()) {
            // Auto start bot
            this.bot.setRunning(true); // Start the bot
            this.start = true; // Set start flag
            this.autoStart = false; // Reset auto start flag
            this.bot.handleRefresh(); // Refresh the game
        }
    }

    @Override
    public void onTickTask() {
        // Empty method, required by Task interface
    }

    @Override
    public void onBackgroundTick() {
        ServerTimeHelper.fetchServerOffset(this.backpage);
    }

    private void collectorOnTick() {
        this.petHandler();
        this.collector.onTickModule();

        if (this.collector.currentBox != null && this.targetDelay() < 10000L) {
            // Adjust the lastTargetLostTime if left 10 seconds
            this.lastTargetLostTime = (System.currentTimeMillis() - this.configTargetDelay()) + 10000L;
            // Reset the collector's move counter
            this.collector.resetMoveCounter();
        }
    }

    private void lootOnTick() {
        this.checkDirection();
        this.petHandler();
        if (this.targetOutOfRange()) {
            // Target out of range, following
            this.loot.onTickFollowModule();
            return;
        }

        switch (this.config.mode) {
            case SpaceballConfig.ModeOptions.ATTACK: {
                this.loot.onTickAttackModule();
                break;
            }
            case SpaceballConfig.ModeOptions.FOLLOW: {
                this.loot.onTickFollowModule();
                break;
            }
            default: {
                if (this.direction.equals(DIRECTION_ENEMY)) {
                    this.loot.onTickFollowModule();
                } else {
                    this.loot.onTickAttackModule();
                }
            }
        }
    }

    private boolean isOnTargetMap() {
        return this.hero.getMap().getName().equals(TERGET_MAP);
    }

    private boolean isTargetSpaceBall() {
        if (!this.loot.getAttacker().hasTarget()) {
            return false;
        }
        return this.loot.getAttacker().getTarget().getEntityInfo().getUsername().equals(NPC_NAME);
    }

    private boolean targetOutOfRange() {
        if (!this.loot.getAttacker().hasTarget()) {
            return true;
        }
        return this.hero.distanceTo(this.loot.getAttacker().getTarget()) > MAX_TARGET_DISTANCE;
    }

    private double calculateDistanceToExitGate() {
        if (this.getExitMap().isEmpty() || !this.isOnTargetMap() || !this.loot.getAttacker().hasTarget()) {
            // Exit map is not set, not on target map, or no target
            return -1;
        }

        Portal exitGate = this.entities.getPortals().stream()
                .filter(portal -> portal.getTargetMap()
                        .map(map -> map.getName().equals(this.getExitMap()))
                        .orElse(false))
                .findFirst()
                .orElse(null);
        if (exitGate == null) {
            // Exit gate not found
            return -1;
        }

        return this.loot.getAttacker().getTarget().distanceTo(exitGate.getX(), exitGate.getY());
    }

    private void checkDirection() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastDirectionCheckTime < 1000) {
            return; // Skip if less than 1 second has passed
        }

        double currentDistance = this.calculateDistanceToExitGate();
        if (this.previousDistanceToExitGate == -1 || currentDistance == -1) {
            // Initialize on first call or if no valid distance
            this.previousDistanceToExitGate = currentDistance;
            this.lastDirectionCheckTime = currentTime;
            return;
        }

        if (currentDistance < this.previousDistanceToExitGate) {
            this.direction = DIRECTION_OWN; // Moving to gate
        } else if (currentDistance > this.previousDistanceToExitGate) {
            if (this.isTargetUnderAttack()) {
                this.direction = DIRECTION_ENEMY; // Moving away from gate and under attack
            } else {
                this.direction = DIRECTION_NEUTRAL; // Moving away from gate but not under attack
            }
        } else {
            this.direction = DIRECTION_NEUTRAL;
        }

        this.previousDistanceToExitGate = currentDistance; // Update previous distance
        this.lastDirectionCheckTime = currentTime; // Update the last check time
    }

    private void resetCounters() {
        this.nullTargetCounter = 0;
        this.reloadCounter = 0;
        this.collector.resetMoveCounter(); // Reset the collector's move counter
    }

    private long configTargetDelay() {
        // Convert the target delay from seconds to milliseconds (plus 1 extra second)
        return (this.config.other.targetDelay * 1000L) + 1000L;
    }

    private long targetDelay() {
        return this.targetDelay(0);
    }

    private long targetDelay(int extra) {
        long delay = this.configTargetDelay();
        if (extra > 0) {
            delay += (extra * 1000L);
        }
        return delay - (System.currentTimeMillis() - this.lastTargetLostTime);
    }

    private void handleTargetLost() {
        this.lastTargetLostTime = System.currentTimeMillis();

        if (this.isOnTargetMap()) {
            this.nullTargetCounter++; // Increment the null target counter
        }
        if (this.nullTargetCounter > MAX_NULL_TARGETS_BEFORE_REFRESH) {
            this.doRefresh(); // Refresh the game if counter exceeds threshold
        }
    }

    // Check if the hero is under attack
    private boolean isHeroUnderAttack() {
        return this.entities.getShips().stream().anyMatch(ship -> ship.isAttacking(this.hero));
    }

    private boolean isTargetUnderAttack() {
        if (!this.loot.getAttacker().hasTarget()) {
            return false; // No target, cannot be under attack
        }

        boolean isUnderAttack = this.entities.getShips().stream()
                .anyMatch(ship -> ship.getEntityInfo().getFaction() != this.hero.getEntityInfo().getFaction()
                        && ship.isAttacking(this.loot.getAttacker().getTarget()));

        long currentTime = System.currentTimeMillis();
        if (isUnderAttack) {
            this.lastUnderAttackTime = currentTime; // Update the last under attack time
        }

        // Keep the "under attack" state for 3 seconds
        return isUnderAttack || (currentTime - this.lastUnderAttackTime <= 3000);
    }

    // Check if the hero's HP is below 10%
    private boolean isLowHP() {
        return (this.hero.getHealth().hpPercent() < 0.1);
    }

    private void doRefresh() {
        if (this.isHeroUnderAttack() || this.isLowHP()) {
            // Cannot reload, hero is under attack or low HP
            this.lastTargetLostTime = System.currentTimeMillis();
        } else {
            if (this.reloadCounter > MAX_RELOAD_ATTEMPTS || !this.isRunningTime()) {
                this.resetCounters(); // Reset the counters
                this.stop = true; // Stop the bot if reloads exceed threshold or after end hour
            } else {
                // Refreshing game due to consecutive null targets
                this.nullTargetCounter = 0; // Reset the null target counter
                this.reloadCounter++; // Increment the reload counter
                if (this.movement.isMoving()) {
                    // Stopping hero movement before refresh
                    this.movement.stop(true);
                }
                this.bot.handleRefresh(); // Refresh the game
            }
        }
    }

    private void moveToExit() {
        // Moving to exit map
        this.hero.setRoamMode();
        this.petGearHelper.setEnabled(false);
        if (!this.traveler.isDone()) {
            this.traveler.setTarget(this.starSystem.getOrCreateMap(this.getExitMap()));
            this.traveler.tick();
        }
    }

    private void stopBot() {
        // Stopping bot
        this.resetCounters(); // Reset the counters
        this.stop = false; // Reset the stop flag
        this.start = false; // Reset the start flag

        if (this.config.other.botProfile != null && !this.config.other.botProfile.isEmpty()) {
            // Switch to the specified profile
            this.configApi.setConfigProfile(this.config.other.botProfile);
        } else {
            // Stop the bot
            this.bot.setRunning(false);
        }

    }

    private LocalDateTime getTime(LocalDateTime currentTime, int hour, int minute) {
        return currentTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    private LocalDateTime getTime(LocalDateTime currentTime, int hour) {
        return this.getTime(currentTime, hour, 0);
    }

    private boolean disabledTimeRestriction() {
        return (this.config.time.startHour == 0 && this.config.time.stopHour == 0);
    }

    private boolean isRunningTime() {
        if (this.disabledTimeRestriction()) {
            return true; // No specific time set, always running
        }

        if (!ServerTimeHelper.offsetUpdated()) {
            return false; // Wait for server time sync
        }

        LocalDateTime currentTime = ServerTimeHelper.currentDateTime();
        LocalDateTime startTime = this.getTime(currentTime, this.config.time.startHour);
        LocalDateTime stopTime = this.getTime(currentTime, this.config.time.stopHour, 1);

        if (stopTime.isBefore(startTime)) {
            if (currentTime.isBefore(LocalDateTime.of(currentTime.toLocalDate(), LocalDateTime.MAX.toLocalTime()))) {
                stopTime = stopTime.plusDays(1); // Add one day to stopTime
            } else {
                startTime = startTime.minusDays(1); // Deduct one day from startTime
            }
        }

        return currentTime.isAfter(startTime) && currentTime.isBefore(stopTime);
    }

    private boolean isPetEnabled() {
        return this.configApi.requireConfig("pet.enabled").getValue().equals(true);
    }

    private void petHandler() {
        // Target SpaceBall or no target, handle PET normally
        this.petHandler(false);
    }

    private void petHandler(boolean targetNpc) {
        if (!this.config.petAssist || !this.isPetEnabled()) {
            return;
        }

        // Set PET config to passive mode, if PET assist is enabled
        if (!this.configApi.requireConfig("pet.module_id").getValue().equals(PetGear.PASSIVE)) {
            this.configApi.requireConfig("pet.module_id").setValue(PetGear.PASSIVE);
        }

        if (targetNpc) {
            // Attacking some NPC, PET should attack too
            this.petGuard();
            return;
        }

        if (this.targetDelay(3) >= 0) {
            // Collecting cargo boxes around the gate (extra 3 seconds)
            this.petCollect();
        } else {
            // Attacking or following SpaceBall
            switch (this.config.mode) {
                case SpaceballConfig.ModeOptions.ATTACK: {
                    this.petGuard();
                    break;
                }
                case SpaceballConfig.ModeOptions.FOLLOW: {
                    this.petPassive();
                    break;
                }
                default: {
                    if (this.direction.equals(DIRECTION_ENEMY)) {
                        this.petPassive();
                    } else {
                        this.petGuard();
                    }
                }
            }
        }
    }

    private void petGuard() {
        this.petGearHelper.setGuard();
    }

    private void petPassive() {
        this.petGearHelper.setPassive();
    }

    private void petCollect() {
        if (!this.petGearHelper.tryUse(PetGear.LOOTER)) {
            // If Auto-looter gear is not equipped, set to passive
            this.petGearHelper.setPassive();
        }
    }

    @Override
    public void setConfig(ConfigSetting<SpaceballConfig> config) {
        this.config = config.getValue();
    }
}
