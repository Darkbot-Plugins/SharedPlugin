package dev.shared.orbithelper.module.daily_quest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.entities.ShipInfo;
import com.github.manolo8.darkbot.config.types.suppliers.BrowserApi;

import dev.shared.orbithelper.config.DailyQuestConfig;
import dev.shared.orbithelper.module.daily_quest.handlers.ExecutionHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.QuestGiverHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.QuestManager;
import dev.shared.orbithelper.module.daily_quest.handlers.SafetyHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.ShipSwitchHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.TravelHandler;
import dev.shared.orbithelper.module.daily_quest.model.AcceptedQuestData;
import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.config.types.PercentRange;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HangarAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.QuestAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.api.utils.Inject;

@Feature(name = "Daily Quest Module", description = "Automatically accepts and executes daily quests")
@SuppressWarnings("java:S1104")
public class DailyQuestModule implements Module, Task, Configurable<DailyQuestConfig> {

    // API Dependencies
    public final PluginAPI api;
    public final QuestAPI questApi;
    public final HeroAPI hero;
    public final RepairAPI repairApi;
    public final AttackAPI attackApi;
    public final MovementAPI movement;
    public final StarSystemAPI starSystem;
    public final EntitiesAPI entities;
    public final PetAPI pet;
    public final StatsAPI stats;
    public final ConfigAPI configApi;
    public final HeroItemsAPI heroItems;
    public final GameScreenAPI guiApi;
    public final BotAPI bot;
    public HangarAPI hangarApi;
    public final BackpageAPI backpageAPI;

    public final Main main;
    public final BackpageManager backpageManager;

    // ConfigSettings
    private ConfigSetting<Integer> workingMapSetting;
    private ConfigSetting<Map<String, NpcInfo>> npcInfos;

    // Safety Configs
    private ConfigSetting<PercentRange> repairHpRange;
    private ConfigSetting<ShipMode> repairMode;
    private ConfigSetting<ShipMode> configRun;
    private ConfigSetting<ShipMode> configRoam;
    private ConfigSetting<ShipMode> configOffensive;

    // Bot Browser API
    public ConfigSetting<BrowserApi> botBrowserApi;

    public DailyQuestConfig config;

    // State Management
    public State state = State.INITIALIZING;

    public enum State {
        INITIALIZING,
        TRAVELING_TO_QUEST_GIVER,
        NAVIGATING_TO_STATION,
        OPENING_QUEST_WINDOW,
        SCANNING_ACTIVE_QUESTS,
        OPENING_QUEST_GIVER_WINDOW,
        CHANGING_QUEST_GIVER_TAB,
        BROWSING_QUESTS,
        CLOSING_QUEST_GIVER,
        ANALYZING_QUESTS,
        EXECUTING_QUEST,
        RESTORING_SHIP,
        COMPLETE
    }

    // Handlers
    public TravelHandler travelHandler;
    public QuestGiverHandler questGiverHandler;
    public QuestManager questManager;
    public SafetyHandler safetyHandler;
    public ExecutionHandler executionHandler;
    public ShipSwitchHandler shipSwitchHandler;

    // Shared Data Fields (accessed by handlers)
    public String targetQuestGiverMap = null;
    public String lastMapBeforeJump = null;
    public long lastJumpTime = 0;
    public static final long MAP_CHANGE_WAIT_MS = 3000;

    // Global Ship tracking
    public boolean isShipSwitched = false;
    public String restoreShipId = null;
    public String questShipOriginalId = null;

    public final Set<Integer> acceptedQuestIds = new HashSet<>();
    public final Map<Integer, AcceptedQuestData> acceptedQuestsData = new HashMap<>();

    private long lastTick = 0;
    private static final long TICK_DELAY_MS = 300;
    private boolean updateHangarData = true;

    @Inject
    public DailyQuestModule(Main main, PluginAPI api) {
        this.api = api;
        this.main = main;
        this.questApi = api.requireAPI(QuestAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.repairApi = api.requireAPI(RepairAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.guiApi = api.requireAPI(GameScreenAPI.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.backpageAPI = api.requireAPI(BackpageAPI.class);
        this.heroItems = api.getAPI(HeroItemsAPI.class);
        this.backpageManager = api.requireInstance(BackpageManager.class);
        this.attackApi = api.getAPI(AttackAPI.class);

        this.botBrowserApi = configApi.requireConfig("bot_settings.api_config.browser_api");

        this.workingMapSetting = configApi.requireConfig("general.working_map");
        this.npcInfos = configApi.requireConfig("loot.npc_infos");

        // Safety Configs Init
        this.repairHpRange = configApi.requireConfig("general.safety.repair_hp_range");
        this.repairMode = configApi.requireConfig("general.safety.repair");
        this.configRun = configApi.requireConfig("general.run");
        this.configRoam = configApi.requireConfig("general.roam");
        this.configOffensive = configApi.requireConfig("general.offensive");

        // Initialize Handlers
        this.travelHandler = new TravelHandler(this);
        this.questGiverHandler = new QuestGiverHandler(this);
        this.questManager = new QuestManager(this);
        this.safetyHandler = new SafetyHandler(this);
        this.shipSwitchHandler = new ShipSwitchHandler(this);
        this.executionHandler = new ExecutionHandler(this);
    }

    @Override
    public void setConfig(ConfigSetting<DailyQuestConfig> arg0) {
        this.config = arg0.getValue();
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public void onTickTask() {
        if (shipSwitchHandler != null && shipSwitchHandler.isSwitching()) {
            shipSwitchHandler.tick();
        }
    }

    @Override
    public void onBackgroundTick() {
        if (this.updateHangarData) {
            this.backpageManager.legacyHangarManager.updateHangarData(500);
            this.updateHangarData = false;
        }

        Map<String, String> ships = DailyQuestConfig.ShipList.ships;
        if (ships.isEmpty()) {
            ships.put("", "--Current Ship--");
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
    public String getStatus() {
        return "Daily Quest: " + getStateMessage();
    }

    private String getStateMessage() {
        switch (this.state) {
            case INITIALIZING:
                return "Initializing...";
            case TRAVELING_TO_QUEST_GIVER:
                return "Traveling to " + (targetQuestGiverMap != null ? targetQuestGiverMap : "quest giver") + "...";
            case NAVIGATING_TO_STATION:
                return "Navigating to Quest Giver station...";
            case OPENING_QUEST_WINDOW:
                return "Opening quest window...";
            case SCANNING_ACTIVE_QUESTS:
                return "Scanning quests (" + questManager.getActiveQuestIds().size() + " found)...";
            case OPENING_QUEST_GIVER_WINDOW:
                return "Opening Quest Giver window...";
            case ANALYZING_QUESTS:
                return "Analyzing quest details...";
            case EXECUTING_QUEST:
                return getExecutionStateMessage();
            case RESTORING_SHIP:
                return "Restoring Ship...";
            case COMPLETE:
                return "All Quests Completed";
            default:
                return this.state.name();
        }
    }

    private String getExecutionStateMessage() {
        ExecutionQuest current = questManager.getCurrentQuest();
        if (current != null) {
            String target = current.targetNpc != null ? current.targetNpc : current.targetOre;
            if (target == null)
                target = "Enemy Players";
            String phaseInfo = "";
            if ("SELL_ORE".equals(current.type) && executionHandler != null) {
                phaseInfo = " [" + executionHandler.getSellOrePhaseStatus() + "]";
            }
            return " (" + current.questTitle + ") " + current.type + " | " +
                    target + " " + (int) current.remaining + " left" + phaseInfo;
        }
        return "Executing...";
    }

    @Override
    public void onTickModule() {
        if (shipSwitchHandler != null && shipSwitchHandler.isSwitching()) {
            shipSwitchHandler.tick();
            return;
        }

        if (this.state != State.EXECUTING_QUEST && System.currentTimeMillis() - lastTick < TICK_DELAY_MS) {
            return;
        }

        lastTick = System.currentTimeMillis();

        if (state == State.INITIALIZING || state == State.ANALYZING_QUESTS) {
            String currentMap = travelHandler.getCurrentMap();
            if (currentMap != null && !currentMap.isEmpty()) {
                travelHandler.setWorkingMap(currentMap);
            }
        }

        questManager.checkAvoidDeathViolation();

        if (safetyHandler.handleSafety())
            return;

        dispatchState();
    }

    private void dispatchState() {
        switch (this.state) {
            case INITIALIZING:
                handleInitializing();
                break;
            case TRAVELING_TO_QUEST_GIVER:
                travelHandler.handleTravelingToQuestGiver();
                break;
            case NAVIGATING_TO_STATION:
                travelHandler.handleNavigatingToStation();
                break;
            case OPENING_QUEST_WINDOW:
                questManager.handleOpeningQuestWindow();
                break;
            case SCANNING_ACTIVE_QUESTS:
                questManager.handleScanningActiveQuests();
                break;
            case EXECUTING_QUEST:
                enforceWorkingMap();
                executionHandler.handleExecutingQuest();
                break;
            case OPENING_QUEST_GIVER_WINDOW:
                questGiverHandler.handleOpeningQuestGiverWindow();
                break;
            case CHANGING_QUEST_GIVER_TAB:
                questGiverHandler.handleChangingQuestGiverTab();
                break;
            case BROWSING_QUESTS:
                questGiverHandler.handleBrowsingQuests();
                break;
            case CLOSING_QUEST_GIVER:
                questGiverHandler.handleClosingQuestGiver();
                break;
            case ANALYZING_QUESTS:
                questManager.handleAnalyzingQuests();
                break;
            case RESTORING_SHIP:
                handleRestoringShip();
                break;
            case COMPLETE:
                handleComplete();
                break;
            default:
                resetState();
        }
    }

    private void handleInitializing() {
        String shipForQuest = config != null ? config.shipForQuest : null;
        if (shipForQuest != null && !shipForQuest.isEmpty()) {
            if (questShipOriginalId == null) {
                questShipOriginalId = shipSwitchHandler.resolveActiveHangarId();
                if (questShipOriginalId == null)
                    return;
            }
            if (!shipForQuest.equals(shipSwitchHandler.resolveActiveHangarId()) && !shipSwitchHandler.isSwitching()) {
                shipSwitchHandler.requestSwitch(shipForQuest, this::advanceFromInitializing);
                return;
            }
            if (shipSwitchHandler.isSwitching())
                return;
        } else {
            // Disabled: clear any stale questShipOriginalId so we don't restore a ship we
            // never switched to
            questShipOriginalId = null;
        }

        advanceFromInitializing();
    }

    private void advanceFromInitializing() {
        if (config.acceptQuest) {
            travelHandler.findNearestQuestGiverMap();
            if (targetQuestGiverMap != null) {
                String currentMap = travelHandler.getCurrentMap();
                if (currentMap.equals(targetQuestGiverMap)) {
                    this.state = State.NAVIGATING_TO_STATION;
                } else {
                    travelHandler.setWorkingMap(targetQuestGiverMap);
                    this.state = State.TRAVELING_TO_QUEST_GIVER;
                }
            } else {
                this.state = State.OPENING_QUEST_WINDOW;
            }
        } else {
            this.state = State.SCANNING_ACTIVE_QUESTS;
        }
    }

    private void handleComplete() {
        if (shipSwitchHandler.isSwitching()) {
            return;
        }

        String shipForQuest = config != null ? config.shipForQuest : null;
        boolean needsQuestShipRestore = shipForQuest != null && !shipForQuest.isEmpty()
                && questShipOriginalId != null
                && !questShipOriginalId.equals(shipSwitchHandler.resolveActiveHangarId());

        if (needsQuestShipRestore) {
            final String targetId = questShipOriginalId;
            final boolean hasMoreQuests = !questManager.getExecutionQueue().isEmpty();
            shipSwitchHandler.requestSwitch(targetId, () -> {
                questShipOriginalId = null;
                isShipSwitched = false;
                restoreShipId = null;
                if (hasMoreQuests) {
                    state = State.INITIALIZING;
                } else {
                    finalizeComplete();
                }
            });
            return;
        }

        if (isShipSwitched && restoreShipId != null) {
            this.state = State.RESTORING_SHIP;
            return;
        }

        finalizeComplete();
    }

    private void finalizeComplete() {
        String currentMap = travelHandler.getCurrentMap();
        if (currentMap != null && !currentMap.isEmpty()) {
            travelHandler.setWorkingMap(currentMap);
        }

        // Fallback config action
        String fallback = config.fallbackConfig;
        String currentProfile = configApi.getCurrentProfile();

        if (fallback == null || DailyQuestConfig.FallbackConfigOptions.STOP_BOT.equals(fallback)) {
            // Safe spot + logout, no reconnect
            shipSwitchHandler.requestStopBot();

        } else if (DailyQuestConfig.FallbackConfigOptions.LAST_USED.equals(fallback)) {
            // Switch to most recently modified config (excluding current)
            String target = DailyQuestConfig.FallbackConfigOptions.resolve(fallback, currentProfile);
            if (target == null) {
                shipSwitchHandler.requestStopBot();
            } else {
                configApi.setConfigProfile(target);
            }

        } else {
            // Specific config selected
            if (fallback.equals(currentProfile)) {
                shipSwitchHandler.requestStopBot();
            } else {
                configApi.setConfigProfile(fallback);
            }
        }
    }

    private void handleRestoringShip() {
        if (!shipSwitchHandler.isSwitching()) {
            shipSwitchHandler.requestSwitch(restoreShipId, () -> {
                isShipSwitched = false;
                restoreShipId = null;
                questManager.setupNextQuest();
            });
        }
    }

    private void enforceWorkingMap() {
        ExecutionQuest current = questManager.getCurrentQuest();
        if (current == null)
            return;
        if ("SELL_ORE".equals(current.type))
            return;

        String expectedMap = current.map;
        if (expectedMap == null)
            return;

        String workingMap = travelHandler.getWorkingMap();
        if (!expectedMap.equals(workingMap)) {
            travelHandler.setWorkingMap(expectedMap);
        }
    }

    private void resetState() {
        this.state = State.OPENING_QUEST_WINDOW;
        questManager.reset();
        questGiverHandler.reset();
        this.targetQuestGiverMap = null;
    }

    // Accessors for Configs (used by handlers)
    public ConfigSetting<Integer> getWorkingMapSetting() {
        return workingMapSetting;
    }

    public ConfigSetting<Map<String, NpcInfo>> getNpcInfos() {
        return npcInfos;
    }

    public ConfigSetting<PercentRange> getRepairHpRange() {
        return repairHpRange;
    }

    public ConfigSetting<ShipMode> getRepairMode() {
        return repairMode;
    }

    public ConfigSetting<ShipMode> getConfigRun() {
        return configRun;
    }

    public ConfigSetting<ShipMode> getConfigRoam() {
        return configRoam;
    }

    public ConfigSetting<ShipMode> getConfigOffensive() {
        return configOffensive;
    }

    public DailyQuestConfig getConfig() {
        return config;
    }

    // =========================================================================
    // Pet management — force enable via configApi
    // =========================================================================

    private Boolean petEnabledOriginal = null;

    public void forcePetEnabled(boolean force) {
        ConfigSetting<Boolean> setting = configApi.getConfig("pet.enabled");
        if (setting == null)
            return;

        if (force) {
            if (petEnabledOriginal == null) {
                petEnabledOriginal = setting.getValue();
            }
            if (!Boolean.TRUE.equals(setting.getValue())) {
                setting.setValue(true);
            }
        } else {
            if (petEnabledOriginal != null) {
                setting.setValue(petEnabledOriginal);
                petEnabledOriginal = null;
            }
        }
    }
}