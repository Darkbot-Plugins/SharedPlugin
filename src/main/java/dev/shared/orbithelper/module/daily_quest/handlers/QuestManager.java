package dev.shared.orbithelper.module.daily_quest.handlers;

import static dev.shared.orbithelper.module.daily_quest.Constants.BASE_NPCS;
import static dev.shared.orbithelper.module.daily_quest.Constants.FROM_SHIP_MAPS;
import static dev.shared.orbithelper.module.daily_quest.Constants.MAP_CHANGE_GRACE_PERIOD_MS;
import static dev.shared.orbithelper.module.daily_quest.Constants.MAP_NAME_PATTERN;
import static dev.shared.orbithelper.module.daily_quest.Constants.MAX_SCAN_RETRIES;
import static dev.shared.orbithelper.module.daily_quest.Constants.MAX_SEQUENTIAL_WAIT_RETRIES;
import static dev.shared.orbithelper.module.daily_quest.Constants.NPC_PREFIXES;
import static dev.shared.orbithelper.module.daily_quest.Constants.NPC_SPAWNS;
import static dev.shared.orbithelper.module.daily_quest.Constants.NPC_SUFFIXES;
import static dev.shared.orbithelper.module.daily_quest.Constants.ORE_SELL_NAMES;
import static dev.shared.orbithelper.module.daily_quest.Constants.ORE_SPAWNS;
import static dev.shared.orbithelper.module.daily_quest.Constants.PVP_MAP;
import static dev.shared.orbithelper.module.daily_quest.Constants.REWARD_TYPE;
import static dev.shared.orbithelper.module.daily_quest.Constants.SCAN_MIN_X;
import static dev.shared.orbithelper.module.daily_quest.Constants.SCAN_START_X;
import static dev.shared.orbithelper.module.daily_quest.Constants.SCAN_STEP_X;
import static dev.shared.orbithelper.module.daily_quest.Constants.SCAN_Y;
import static dev.shared.orbithelper.module.daily_quest.Constants.WINDOW_SETTLE_DELAY_MS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;

import dev.shared.orbithelper.config.DailyQuestConfig;
import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.model.AcceptedQuestData;
import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;
import dev.shared.orbithelper.module.daily_quest.model.QuestRequirement;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.managers.QuestAPI;

public class QuestManager {

    // -------------------------------------------------------------------------
    // Quest Type Constants
    // -------------------------------------------------------------------------

    private static final String TYPE_KILL_NPC = "KILL_NPC";
    private static final String TYPE_COLLECT = "COLLECT";
    private static final String TYPE_SALVAGE = "SALVAGE";
    private static final String TYPE_SELL_ORE = "SELL_ORE";
    private static final String TYPE_KILL_PLAYERS = "KILL_PLAYERS";
    private static final String KEY_QUESTS = "quests";

    // -------------------------------------------------------------------------
    // Module Reference
    // -------------------------------------------------------------------------

    private final DailyQuestModule module;

    // -------------------------------------------------------------------------
    // Quest Window State
    // -------------------------------------------------------------------------

    private long windowOpenedAt = 0;

    // -------------------------------------------------------------------------
    // Scan State
    // -------------------------------------------------------------------------

    private int scanIndex = 0;
    private int lastQuestId = -1;
    private int sameIdCount = 0;
    private long lastScanClickTime = 0;

    private final Set<Integer> activeQuestIds = new HashSet<>();

    // -------------------------------------------------------------------------
    // Quest Queue
    // -------------------------------------------------------------------------

    private final Queue<ExecutionQuest> executionQueue = new LinkedList<>();
    private ExecutionQuest currentQuest;

    // -------------------------------------------------------------------------
    // Map Change Tracking
    // -------------------------------------------------------------------------

    private int currentMapId = -1;
    private long lastMapChangeTime = 0;

    // -------------------------------------------------------------------------
    // Quest Selection State
    // -------------------------------------------------------------------------

    private long lastQuestSelectClickTime = 0;
    private int questSelectIndex = 0;
    private int fullSweepCount = 0;

    private static final long SELECT_CLICK_INTERVAL_MS = 300;
    private boolean selectClickPending = false;

    // -------------------------------------------------------------------------
    // Completion Verification
    // -------------------------------------------------------------------------

    private int verificationScans = 0;
    private boolean validatingActiveQuests = false;

    // -------------------------------------------------------------------------
    // Null Display Timeout (all quests completed detection)
    // -------------------------------------------------------------------------

    private long nullDisplayStartTime = 0;
    private static final long NULL_DISPLAY_TIMEOUT_MS = 5000;

    // -------------------------------------------------------------------------
    // Sequential Step Wait
    // -------------------------------------------------------------------------

    private int sequentialWaitRetries = 0;

    // -------------------------------------------------------------------------
    // Keeps a sequential quest alive while waiting for the server to enable the
    // next
    // step
    // -------------------------------------------------------------------------

    private static final double SEQUENTIAL_STEP_KEEP_ALIVE = 0.001;

    // =========================================================================
    // Constructor & Public Accessors
    // =========================================================================

    public QuestManager(DailyQuestModule module) {
        this.module = module;
    }

    public ExecutionQuest getCurrentQuest() {
        return currentQuest;
    }

    public Queue<ExecutionQuest> getExecutionQueue() {
        return executionQueue;
    }

    public ExecutionQuest peekNextQuest() {
        return executionQueue.peek();
    }

    public Set<Integer> getActiveQuestIds() {
        return activeQuestIds;
    }

    // =========================================================================
    // Reset
    // =========================================================================

    public void reset() {
        activeQuestIds.clear();
        executionQueue.clear();
        currentQuest = null;
        scanIndex = 0;
        lastQuestId = -1;
        sameIdCount = 0;
        windowOpenedAt = 0;
        lastScanClickTime = 0;
        questSelectIndex = 0;
        fullSweepCount = 0;
        nullDisplayStartTime = 0;
        validatingActiveQuests = false;
        verificationScans = 0;
    }

    // =========================================================================
    // STATE: OPENING_QUEST_WINDOW
    // =========================================================================

    public void handleOpeningQuestWindow() {
        Gui questGui = module.guiApi.getGui(KEY_QUESTS);

        if (questGui == null || !questGui.isVisible()) {
            tryOpenQuestWindow();
            windowOpenedAt = 0;
            return;
        }

        if (windowOpenedAt == 0) {
            windowOpenedAt = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - windowOpenedAt < WINDOW_SETTLE_DELAY_MS) {
            return;
        }

        QuestAPI.Quest displayedQuest = module.questApi.getDisplayedQuest();
        if (displayedQuest == null) {
            if (module.config.acceptQuest) {
                module.state = DailyQuestModule.State.OPENING_QUEST_GIVER_WINDOW;
            } else {
                module.state = DailyQuestModule.State.COMPLETE;
            }
            return;
        }

        module.state = DailyQuestModule.State.SCANNING_ACTIVE_QUESTS;
        scanIndex = 0;
        lastQuestId = -1;
        sameIdCount = 0;
        lastScanClickTime = 0;
        activeQuestIds.clear();
        validatingActiveQuests = false;
    }

    // =========================================================================
    // STATE: SCANNING_ACTIVE_QUESTS
    // =========================================================================

    public void handleScanningActiveQuests() {
        updateMapStatus();
        if (isMapChanging())
            return;
        if (module.questApi.isQuestGiverOpen()) {
            module.questGiverHandler.moveAwayFromQuestGiver();
            return;
        }

        Gui questGui = module.guiApi.getGui(KEY_QUESTS);
        if (questGui == null || !questGui.isVisible()) {
            tryOpenQuestWindow();
            windowOpenedAt = 0;
            return;
        }
        if (windowOpenedAt == 0) {
            windowOpenedAt = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - windowOpenedAt < WINDOW_SETTLE_DELAY_MS)
            return;
        if (System.currentTimeMillis() - lastScanClickTime < SELECT_CLICK_INTERVAL_MS)
            return;

        if (scanIndex == 0) {
            lastScanClickTime = System.currentTimeMillis();
            questGui.click(SCAN_START_X, SCAN_Y);
            scanIndex++;
            return;
        }

        tickScanQuestId();
        if (module.state != DailyQuestModule.State.SCANNING_ACTIVE_QUESTS)
            return;

        int clickX = SCAN_START_X - (scanIndex * SCAN_STEP_X);
        if (clickX < SCAN_MIN_X) {
            finishScanning();
            return;
        }

        lastScanClickTime = System.currentTimeMillis();
        questGui.click(clickX, SCAN_Y);
        scanIndex++;
    }

    private boolean tickScanQuestId() {
        QuestAPI.Quest displayedQuest = module.questApi.getDisplayedQuest();
        if (displayedQuest == null) {
            sameIdCount++;
            if (sameIdCount >= MAX_SCAN_RETRIES)
                finishScanning();
            return false;
        }

        int displayedQuestId = displayedQuest.getId();
        if (displayedQuestId == lastQuestId) {
            sameIdCount++;
            if (sameIdCount >= MAX_SCAN_RETRIES)
                finishScanning();
            return false;
        }

        sameIdCount = 0;
        if (!activeQuestIds.contains(displayedQuestId)) {
            activeQuestIds.add(displayedQuestId);
            if (hasArmoryTokenReward(displayedQuest))
                captureDisplayedQuestData(displayedQuest);
        }
        lastQuestId = displayedQuestId;
        return true;
    }

    // =========================================================================
    // STATE: ANALYZING_QUESTS
    // =========================================================================

    public void handleAnalyzingQuests() {
        if (module.acceptedQuestsData.isEmpty()) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        generateExecutionPlan();

        module.state = DailyQuestModule.State.EXECUTING_QUEST;
    }

    // =========================================================================
    // Quest Selection & Status Update (called by ExecutionHandler)
    // =========================================================================

    /**
     * Ensures the correct quest is selected in the quest window.
     * Returns true during a map transition to prevent the quest from being wiped.
     */
    public boolean ensureQuestSelected() {
        updateMapStatus();
        if (currentQuest == null)
            return false;
        if (isMapChanging()) {
            resetQuestSelectState();
            return true;
        }

        Gui questGui = module.guiApi.getGui(KEY_QUESTS);
        QuestAPI.Quest displayedQuest = module.questApi.getDisplayedQuest();
        int displayedId = displayedQuest != null ? displayedQuest.getId() : -1;

        if (displayedId == currentQuest.questId) {
            resetQuestSelectState();
            if (questGui != null && questGui.isVisible())
                questGui.setVisible(false);
            return true;
        }

        if (fullSweepCount >= 1 && displayedId != -1) {
            ExecutionQuest nextQuest = peekNextQuest();
            if (nextQuest != null && displayedId == nextQuest.questId) {
                return false;
            }
        }

        if (!isQuestWindowReady(questGui))
            return true;

        if (displayedId == -1)
            return handleNullDisplay();

        nullDisplayStartTime = 0;

        if (questSelectIndex == 0 && !selectClickPending) {
            doSelectClick(questGui, SCAN_START_X);
            return true;
        }

        if (selectClickPending && !processSelectClickResult())
            return false;

        return advanceSelectScan(questGui);
    }

    private boolean isQuestWindowReady(Gui questGui) {
        if (questGui == null || !questGui.isVisible()) {
            if (questGui != null)
                questGui.setVisible(true);
            windowOpenedAt = 0;
            return false;
        }

        if (windowOpenedAt == 0) {
            windowOpenedAt = System.currentTimeMillis();
            return false;
        }
        return System.currentTimeMillis() - windowOpenedAt >= WINDOW_SETTLE_DELAY_MS &&
                System.currentTimeMillis() - lastQuestSelectClickTime >= SELECT_CLICK_INTERVAL_MS;
    }

    private boolean handleNullDisplay() {
        if (nullDisplayStartTime == 0)
            nullDisplayStartTime = System.currentTimeMillis();
        boolean isTimeout = System.currentTimeMillis() - nullDisplayStartTime >= NULL_DISPLAY_TIMEOUT_MS;
        if (isTimeout) {
            nullDisplayStartTime = 0;
        }
        return !isTimeout;
    }

    private boolean processSelectClickResult() {
        selectClickPending = false;
        return true;
    }

    private boolean advanceSelectScan(Gui questGui) {
        int clickX = SCAN_START_X - (questSelectIndex * SCAN_STEP_X);
        if (clickX < SCAN_MIN_X) {
            questSelectIndex = 0;
            fullSweepCount++;
            if (fullSweepCount >= 2) {
                return false;
            }
            clickX = SCAN_START_X;
        }
        doSelectClick(questGui, clickX);
        return true;
    }

    private void doSelectClick(Gui questGui, int clickX) {
        lastQuestSelectClickTime = System.currentTimeMillis();
        selectClickPending = true;
        questGui.click(clickX, SCAN_Y);
        questSelectIndex++;
    }

    private void resetQuestSelectState() {
        questSelectIndex = 0;
        fullSweepCount = 0;
        selectClickPending = false;
        nullDisplayStartTime = 0;
    }

    /**
     * Updates the current quest's progress from live quest data.
     * Triggers the next sequential step or marks the quest complete when finished.
     */
    public void updateCurrentQuestStatus() {
        QuestAPI.Quest displayedQuest = module.questApi.getDisplayedQuest();
        if (displayedQuest == null || displayedQuest.getId() != currentQuest.questId)
            return;

        AcceptedQuestData cachedQuestData = module.acceptedQuestsData.get(currentQuest.questId);

        if (!processQuestRequirements(displayedQuest, cachedQuestData)) {
            handleRequirementNotFound(displayedQuest);
        }
    }

    private boolean processQuestRequirements(QuestAPI.Quest displayedQuest, AcceptedQuestData cachedQuestData) {
        int parsedRequirementIndex = 0;
        for (QuestAPI.Requirement req : displayedQuest.getRequirements()) {
            String reqType = req.getType();
            if (isParsableRequirementType(reqType)) {
                int index = parsedRequirementIndex++;
                if (reqType.equals(currentQuest.type)
                        && resolveRequirementMatch(req, reqType, index, cachedQuestData)) {
                    updateQuestProgress(req, displayedQuest);
                    return true;
                }
            }
        }
        return false;
    }

    private void updateQuestProgress(QuestAPI.Requirement req, QuestAPI.Quest displayedQuest) {
        currentQuest.active = req.isEnabled();
        double newRemaining = Math.max(0, req.getGoal() - req.getProgress());
        if (currentQuest.remaining != newRemaining)
            currentQuest.remaining = newRemaining;
        if (req.isCompleted() || newRemaining == 0)
            handleQuestCompletion(displayedQuest);
    }

    /**
     * Advances to the next quest in the queue.
     */
    public void setupNextQuest() {
        if (module.isShipSwitched && module.restoreShipId != null) {
            ExecutionQuest nextQuest = executionQueue.peek();
            if (nextQuest != null && nextQuestNeedsSwitchedShip(nextQuest)) {
                // switched ship is still needed — keep it, continue to next quest
            } else if (executionQueue.isEmpty()) {
                module.state = DailyQuestModule.State.COMPLETE;
                return;
            } else {
                module.state = DailyQuestModule.State.RESTORING_SHIP;
                return;
            }
        }

        if (executionQueue.isEmpty()) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        boolean wasNotExecuting = module.state != DailyQuestModule.State.EXECUTING_QUEST;
        currentQuest = executionQueue.poll();
        module.state = DailyQuestModule.State.EXECUTING_QUEST;

        // Reset death counter when execution first begins so pre-existing deaths are
        if (wasNotExecuting && hasActiveAvoidDeathQuest()) {
            module.repairApi.resetDeaths();
        }
    }

    /**
     * Returns true if the given quest needs the same ship that is currently
     * switched to.
     * Used to avoid unnecessary restore+switch cycles between consecutive quests
     * that share the same alternate ship.
     */
    private boolean nextQuestNeedsSwitchedShip(ExecutionQuest next) {
        String currentActiveHangar = module.shipSwitchHandler.resolveActiveHangarId();
        if (currentActiveHangar == null)
            return false;

        String shipForCollect = module.config.questTypes.collectOresSettings.shipForCollect;
        String shipForSell = module.config.questTypes.sellOresSettings.shipForSell;

        boolean nextIsCollect = TYPE_COLLECT.equals(next.type) || TYPE_SALVAGE.equals(next.type);
        boolean nextIsSell = TYPE_SELL_ORE.equals(next.type);

        return (nextIsCollect && shipForCollect != null && shipForCollect.equals(currentActiveHangar))
                || (nextIsSell && shipForSell != null && shipForSell.equals(currentActiveHangar));
    }

    /**
     * Called by CollectHandler (via ExecutionHandler) when ship restore is done.
     */
    public void markComplete() {
        module.state = DailyQuestModule.State.COMPLETE;
    }

    // =========================================================================
    // AVOID_DEATH Violation Handling
    // =========================================================================

    /**
     * Called every tick from DailyQuestModule.
     * Only active when there is at least one incomplete AVOID_DEATH quest in the
     * plan.
     * Resets the death counter when execution starts, and reacts if a death occurs.
     */
    public void checkAvoidDeathViolation() {
        if (module.state != DailyQuestModule.State.EXECUTING_QUEST)
            return;
        if (!hasActiveAvoidDeathQuest())
            return;

        if (module.repairApi.getDeathAmount() > 0) {
            handlePlayerDeath();
        }
    }

    /**
     * Moves the current quest to the end of the execution queue and advances
     * to the next quest. Used when cargo is full but more quests remain.
     */
    public void deferCurrentQuestToEnd() {
        if (currentQuest == null)
            return;
        executionQueue.add(currentQuest);
        setupNextQuest();
    }

    private boolean hasActiveAvoidDeathQuest() {
        // Check current quest
        if (currentQuest != null) {
            AcceptedQuestData data = module.acceptedQuestsData.get(currentQuest.questId);
            if (data != null && data.hasAvoidDeath && !data.completed)
                return true;
        }
        // Check queue
        for (ExecutionQuest quest : executionQueue) {
            AcceptedQuestData data = module.acceptedQuestsData.get(quest.questId);
            if (data != null && data.hasAvoidDeath && !data.completed)
                return true;
        }
        return false;
    }

    /**
     * Called when the player dies while an AVOID_DEATH quest is active.
     * Removes all AVOID_DEATH quests from the execution plan, clears their cached
     * data so they will be re-scanned, and returns to the quest giver to re-accept
     * them.
     */
    private void handlePlayerDeath() {
        // Remove AVOID_DEATH quests from the queue
        executionQueue.removeIf(quest -> {
            AcceptedQuestData data = module.acceptedQuestsData.get(quest.questId);
            return data != null && data.hasAvoidDeath && !data.completed;
        });

        // Remove current quest if it belongs to an AVOID_DEATH quest
        if (currentQuest != null) {
            AcceptedQuestData data = module.acceptedQuestsData.get(currentQuest.questId);
            if (data != null && data.hasAvoidDeath && !data.completed) {
                currentQuest = null;
            }
        }

        // Clear cached data so they get re-accepted and re-analyzed
        module.acceptedQuestsData.entrySet()
                .removeIf(e -> e.getValue().hasAvoidDeath && !e.getValue().completed);
        module.acceptedQuestIds.removeIf(id -> !module.acceptedQuestsData.containsKey(id));

        module.repairApi.resetDeaths();

        validatingActiveQuests = false;
        verificationScans = 0;
        resetQuestSelectState();

        // Return to initializing so the quest giver flow restarts from the beginning
        module.state = DailyQuestModule.State.INITIALIZING;
    }

    /**
     * Extracts the NPC name from a quest description.
     * Supports Prefix + Base + Suffix structure (e.g. "Boss Saimon",
     * "UberLordakia").
     */
    public String parseNpcName(String description) {
        if (description == null || description.isEmpty())
            return null;

        // Strip digits and special characters; keep only letters and spaces
        String sanitized = description.replaceAll("[^a-zA-Z ]", "");

        String foundBase = findMatch(sanitized, BASE_NPCS);
        if (foundBase == null)
            return null;

        String foundPrefix = findMatch(sanitized, NPC_PREFIXES);
        String foundSuffix = findMatch(sanitized, NPC_SUFFIXES);

        StringBuilder fullName = new StringBuilder();
        if (foundPrefix != null) {
            fullName.append(foundPrefix);
            // "Uber" is written without a space (UberLordakia), others use a space (Boss
            // Lordakia)
            if (!"Uber".equals(foundPrefix))
                fullName.append(" ");
            fullName.append(foundBase);
        } else {
            fullName.append(foundBase);
        }

        if (foundSuffix != null)
            fullName.append(" ").append(foundSuffix);

        return fullName.toString();
    }

    private String findMatch(String text, java.util.List<String> list) {
        for (String item : list) {
            if (text.contains(item))
                return item;
        }
        return null;
    }

    /**
     * Extracts the ore name from a quest description.
     */
    public String parseOreName(String description) {
        if (description == null || description.isEmpty())
            return null;

        // Check all sellable ore names first (superset of ORE_SPAWNS keys)
        for (String oreName : ORE_SELL_NAMES) {
            if (description.contains(oreName))
                return oreName;
        }
        return null;
    }

    /**
     * Extracts map names in "x-y" format from a quest description.
     */
    public List<String> parseMapNames(String description) {
        List<String> maps = new ArrayList<>();
        Matcher matcher = MAP_NAME_PATTERN.matcher(description);
        while (matcher.find())
            maps.add(matcher.group(1));
        return maps;
    }

    // =========================================================================
    // Execution Plan Generation
    // =========================================================================

    private void generateExecutionPlan() {
        executionQueue.clear();

        List<SimulationQuest> readyQuests = buildSimulationQuests();

        if (readyQuests.isEmpty()) {
            setupNextQuest();
            return;
        }

        simulateOptimalMapVisits(readyQuests);

        reorderKillPlayersOnRoute(new ArrayList<>(executionQueue));
        consolidateCollectQuests();

        setupNextQuest();
    }

    private List<SimulationQuest> buildSimulationQuests() {
        List<SimulationQuest> readyQuests = new ArrayList<>();

        for (AcceptedQuestData questData : module.acceptedQuestsData.values()) {
            if (questData.parsedRequirements != null) {
                SimulationQuest[] previousSimQuestBox = new SimulationQuest[1];

                for (QuestRequirement req : questData.parsedRequirements) {
                    processSimulationRequirement(req, questData, readyQuests, previousSimQuestBox);
                }
            }
        }
        return readyQuests;
    }

    private void processSimulationRequirement(QuestRequirement req, AcceptedQuestData questData,
            List<SimulationQuest> readyQuests, SimulationQuest[] previousSimQuestBox) {
        if (req.completed || !isQuestTypeEnabled(req.type))
            return;

        ExecutionQuest execQuest = buildExecutionQuest(req, questData);
        if (execQuest == null)
            return;

        execQuest.validMaps = normalizeMapsToFaction(execQuest.validMaps);
        if (execQuest.validMaps.isEmpty())
            return;

        SimulationQuest simQuest = new SimulationQuest(execQuest, questData.isSequential);

        if (questData.isSequential) {
            if (previousSimQuestBox[0] != null) {
                previousSimQuestBox[0].nextStep = simQuest;
            } else {
                readyQuests.add(simQuest);
            }
            previousSimQuestBox[0] = simQuest;
        } else {
            readyQuests.add(simQuest);
        }
    }

    private void simulateOptimalMapVisits(List<SimulationQuest> readyQuests) {
        String currentSimMap = module.travelHandler.getCurrentMap();
        List<ExecutionQuest> finalPlan = new ArrayList<>();

        while (!readyQuests.isEmpty()) {
            String targetMap = selectBestMap(readyQuests, currentSimMap);
            if (targetMap == null)
                break;

            String lastTarget = null;
            while (true) {
                SimulationQuest selected = processNextQuestInMap(readyQuests, targetMap, lastTarget);
                if (selected == null)
                    break;

                selected.quest.map = targetMap;
                finalPlan.add(selected.quest);
                readyQuests.remove(selected);

                lastTarget = selected.quest.targetNpc != null
                        ? selected.quest.targetNpc
                        : selected.quest.targetOre;

                if (selected.nextStep != null) {
                    readyQuests.add(selected.nextStep);
                }
            }
            currentSimMap = targetMap;
        }

        executionQueue.addAll(finalPlan);
    }

    private SimulationQuest processNextQuestInMap(List<SimulationQuest> readyQuests, String targetMap,
            String lastTarget) {
        List<SimulationQuest> candidatesInMap = getCandidatesForMap(readyQuests, targetMap);
        if (candidatesInMap.isEmpty())
            return null;

        List<SimulationQuest> nonSellCandidates = new ArrayList<>();
        List<SimulationQuest> sellCandidates = new ArrayList<>();

        for (SimulationQuest c : candidatesInMap) {
            if (TYPE_SELL_ORE.equals(c.quest.type)) {
                sellCandidates.add(c);
            } else {
                nonSellCandidates.add(c);
            }
        }

        if (!nonSellCandidates.isEmpty()) {
            SimulationQuest selected = findQuestWithSameTarget(nonSellCandidates, lastTarget);
            return selected != null ? selected : nonSellCandidates.get(0);
        }
        return sellCandidates.get(0);
    }

    private void consolidateCollectQuests() {
        String shipForCollect = module.config.questTypes.collectOresSettings.shipForCollect;
        if (shipForCollect != null && !shipForCollect.isEmpty()) {
            List<ExecutionQuest> collectQuests = new ArrayList<>();
            List<ExecutionQuest> otherQuests = new ArrayList<>();
            for (ExecutionQuest q : executionQueue) {
                if (TYPE_COLLECT.equals(q.type) || TYPE_SALVAGE.equals(q.type)) {
                    collectQuests.add(q);
                } else {
                    otherQuests.add(q);
                }
            }

            if (!collectQuests.isEmpty()) {
                int insertIdx = findBestCollectInsertionIndex(otherQuests, collectQuests);
                executionQueue.clear();
                executionQueue.addAll(otherQuests.subList(0, insertIdx));
                executionQueue.addAll(collectQuests);
                executionQueue.addAll(otherQuests.subList(insertIdx, otherQuests.size()));
            }
        }
    }

    /**
     * Reorders KILL_PLAYERS quests in the execution queue so they are executed when
     * the route passes through or directly adjacent to their map.
     *
     * Logic: for each KILL_PLAYERS quest, find the earliest position in the plan
     * where the transition (prevMap → nextMap) makes the KILL_PLAYERS map a natural
     * detour (i.e., KILL_PLAYERS map is adjacent to prevMap AND adjacent to
     * nextMap,
     * or it lies on the shortest path between the two map groups).
     */

    /**
     * Finds the best index in otherQuests to insert collect quests.
     *
     * Strategy: Insert BEFORE the first non-collect step on the collect quest's
     * map,
     * so the ship switch happens once and all collect + subsequent steps on that
     * map
     * use the switched ship (or it's restored before the kill quests start).
     *
     * If no matching map is found, appends at end.
     */

    // =========================================================================
    // Simulation — Map Selection
    // =========================================================================

    /**
     * Selects the most efficient target map from the ready quest list.
     * Score = (quest count × 100) - (distance × 250) + lookahead bonus - skip
     * penalty
     */

    /**
     * Calculates a lookahead bonus for chain steps that remain on the same map.
     */
    private int calculateLookaheadScore(SimulationQuest nextStep, String targetMap) {
        int score = 0;
        int depth = 0;
        SimulationQuest current = nextStep;

        while (current != null && depth < 5) {
            boolean nextInMap = current.quest.validMaps.stream()
                    .anyMatch(vm -> getFactionMap(vm).equals(targetMap));
            score += nextInMap ? 50 : 10;
            current = current.nextStep;
            depth++;
        }
        return score;
    }

    /**
     * Applies a heavy penalty when skipping intermediate maps that contain quests.
     * Penalty: 5000 points per skipped map with at least one pending quest.
     */
    private int calculateSkipPenalty(List<SimulationQuest> readyQuests, String from, String to) {
        String pathMap = from;
        int safety = 0;

        while (!pathMap.equals(to) && safety < 20) {
            pathMap = module.travelHandler.getNextMapOnPath(pathMap, to);
            if (pathMap == null || pathMap.equals(to))
                break;

            for (SimulationQuest quest : readyQuests) {
                for (String vm : quest.quest.validMaps) {
                    if (getFactionMap(vm).equals(pathMap))
                        return 5000;
                }
            }
            safety++;
        }
        return 0;
    }

    // =========================================================================
    // Scan & Validation Helpers
    // =========================================================================

    private void finishScanning() {
        // ---- Validation scan (mid-execution) ----
        // Always return from this block — never fall through to ANALYZING_QUESTS
        if (validatingActiveQuests) {
            if (currentQuest == null) {
                // Shouldn't happen, but handle gracefully
                validatingActiveQuests = false;
                verificationScans = 0;
                setupNextQuest();
                return;
            }

            boolean questStillActive = activeQuestIds.contains(currentQuest.questId);

            if (!questStillActive) {
                if (isMapChanging())
                    return;

                if (verificationScans < 2) {
                    verificationScans++;
                    resetScanState();
                    return;
                }

                // Quest is confirmed gone after double scan
                executionQueue.removeIf(quest -> quest.questId == currentQuest.questId);
                currentQuest.remaining = 0;
                verificationScans = 0;
                validatingActiveQuests = false;
                module.state = DailyQuestModule.State.EXECUTING_QUEST;
                return;
            }

            // Quest is still active — confirm with double scan
            verificationScans++;
            if (verificationScans >= 2) {
                validatingActiveQuests = false;
                verificationScans = 0;
                module.state = DailyQuestModule.State.EXECUTING_QUEST;
                return;
            }
            resetScanState();
            return;
        }

        // ---- Normal (initial) scan flow ----
        // Sync cache with actually active quests to purge any completed/aborted ones
        module.acceptedQuestsData.keySet().retainAll(activeQuestIds);
        module.acceptedQuestIds.retainAll(activeQuestIds);

        if (module.config.acceptQuest) {
            module.state = DailyQuestModule.State.OPENING_QUEST_GIVER_WINDOW;
            return;
        }

        module.state = DailyQuestModule.State.ANALYZING_QUESTS;
    }

    private void resetScanState() {
        scanIndex = 0;
        lastQuestId = -1;
        sameIdCount = 0;
        activeQuestIds.clear();
    }

    // =========================================================================
    // Quest Data Capture
    // =========================================================================

    /**
     * Captures the displayed quest's data and stores it in acceptedQuestsData.
     * The same quest is never captured twice.
     */
    private void captureDisplayedQuestData(QuestAPI.Quest quest) {
        if (quest == null)
            return;
        int questId = quest.getId();
        if (module.acceptedQuestsData.containsKey(questId))
            return;

        AcceptedQuestData data = new AcceptedQuestData();
        data.id = questId;
        data.title = quest.getTitle();
        data.completed = quest.isCompleted();

        if (quest.getRewards() != null) {
            data.rewards = new ArrayList<>();
            for (QuestAPI.Reward reward : quest.getRewards()) {
                data.rewards.add(reward.getType() + " x" + reward.getAmount());
            }
        }

        if (quest.getRequirements() != null) {
            data.requirements = new ArrayList<>();
            data.parsedRequirements = new ArrayList<>();
            parseRequirementsInto(quest, data);
            if (!data.hasAvoidDeath) {
                data.hasAvoidDeath = quest.getRequirements().stream()
                        .anyMatch(req -> "AVOID_DEATH".equals(req.getType()));
            }
            data.isSequential = quest.getRequirements().stream()
                    .anyMatch(req -> !"REAL_TIME_HASTE".equals(req.getType()) && !req.isEnabled());
        }

        module.acceptedQuestsData.put(data.id, data);
    }

    private void parseRequirementsInto(QuestAPI.Quest quest, AcceptedQuestData data) {
        int requirementIndex = 0;
        for (QuestAPI.Requirement req : quest.getRequirements()) {
            String reqType = req.getType();
            data.requirements.add("Req: " + req.getDescription()
                    + " | Type: " + reqType
                    + " | Progress: " + req.getProgress() + "/" + req.getGoal()
                    + " | Completed: " + req.isCompleted()
                    + " | Enabled: " + req.isEnabled());

            if (isParsableRequirementType(reqType)) {
                QuestRequirement parsed = parseRequirement(quest, req, reqType, requirementIndex);
                if (parsed != null) {
                    applySubRequirements(req, parsed, data);
                    data.parsedRequirements.add(parsed);
                }
            }
            requirementIndex++;
        }
    }

    private void applySubRequirements(QuestAPI.Requirement req, QuestRequirement parsed, AcceptedQuestData data) {
        if (req.getRequirements() == null)
            return;
        for (QuestAPI.Requirement subReq : req.getRequirements()) {
            data.requirements.add("  L2: " + subReq.getDescription()
                    + " | Type: " + subReq.getType()
                    + " | Enabled: " + subReq.isEnabled());
            if ("MAP".equals(subReq.getType()))
                parsed.allowedMaps = parseMapNames(subReq.getDescription());
            if ("AVOID_DEATH".equals(subReq.getType()))
                data.hasAvoidDeath = true;
        }
    }

    /**
     * Parses a single requirement. Returns null if the requirement cannot be
     * parsed.
     */
    private QuestRequirement parseRequirement(QuestAPI.Quest quest, QuestAPI.Requirement req,
            String reqType, int index) {
        QuestRequirement parsed = new QuestRequirement();
        parsed.description = req.getDescription();
        parsed.type = reqType;
        parsed.progress = req.getProgress();
        parsed.goal = req.getGoal();
        parsed.completed = req.isCompleted();
        parsed.enabled = req.isEnabled();

        String targetNpc = null;
        String targetOre = null;

        if (TYPE_KILL_NPC.equals(reqType)) {
            targetNpc = parseNpcName(req.getDescription());
            if (targetNpc == null)
                return null; // Skip unparsable NPC
            parsed.targetNpc = targetNpc;
        } else if (TYPE_COLLECT.equals(reqType)) {
            targetOre = parseOreName(req.getDescription());
            if (targetOre == null)
                return null; // Skip unparsable ore
            parsed.targetOre = targetOre;
        } else if (TYPE_SALVAGE.equals(reqType)) {
            targetOre = parseOreName(req.getDescription());
            if (targetOre == null)
                return null; // Skip unparsable ore
            parsed.targetOre = targetOre;
        } else if (TYPE_SELL_ORE.equals(reqType)) {
            targetOre = parseOreName(req.getDescription());
            // targetOre may be null for some sell quests — still keep the requirement
            parsed.targetOre = targetOre;
        }

        parsed.uuid = generateRequirementUuid(
                quest.getId(), reqType, req.getDescription(), targetNpc, targetOre,
                req.getProgress(), index);

        return parsed;
    }

    // =========================================================================
    // Quest Completion & Sequential Step Management
    // =========================================================================

    private void handleQuestCompletion(QuestAPI.Quest displayedQuest) {
        updateAcceptedQuestDataProgress(currentQuest);

        // Only try to inject the next sequential step for sequential quests.
        // For non-sequential quests, all steps are already in the execution queue
        // in the correct planned order — injecting addFirst() here would bypass
        // the optimized plan and cause map backtracking.
        AcceptedQuestData questData = module.acceptedQuestsData.get(currentQuest.questId);
        boolean isSequential = questData != null && questData.isSequential;

        if (isSequential) {
            boolean foundNextSequentialStep = checkForNextSequentialStep(displayedQuest);
            if (!foundNextSequentialStep && hasMoreIncompleteRequirements(displayedQuest)) {
                sequentialWaitRetries++;
                if (sequentialWaitRetries >= MAX_SEQUENTIAL_WAIT_RETRIES) {
                    currentQuest.remaining = 0;
                    sequentialWaitRetries = 0;
                } else {
                    currentQuest.remaining = SEQUENTIAL_STEP_KEEP_ALIVE;
                }
            } else {
                currentQuest.remaining = 0;
                sequentialWaitRetries = 0;
            }
        } else {
            // Non-sequential: all steps already planned, just mark done
            currentQuest.remaining = 0;
            sequentialWaitRetries = 0;
        }
    }

    private void handleRequirementNotFound(QuestAPI.Quest displayedQuest) {

        // In keep-alive mode: check whether the server has now enabled the next step
        if (currentQuest.remaining == SEQUENTIAL_STEP_KEEP_ALIVE) {
            boolean foundNext = checkForNextSequentialStep(displayedQuest);
            if (foundNext) {
                currentQuest.remaining = 0;
            } else if (!hasMoreIncompleteRequirements(displayedQuest)) {
                currentQuest.remaining = 0;
            }
            // Else: still waiting
        }
        // Note: do not immediately complete the quest if no match is found; it may be a
        // UI lag.
    }

    /**
     * After a quest completes, looks for the next enabled sequential step and
     * pushes
     * it to the front of the queue.
     * Skips the injection if the step is already planned.
     *
     * @return true if the next step was found and added
     */
    private boolean checkForNextSequentialStep(QuestAPI.Quest displayedQuest) {
        for (QuestAPI.Requirement req : displayedQuest.getRequirements()) {
            if (!req.isCompleted() && req.isEnabled() && req.getProgress() < req.getGoal()) {
                ExecutionQuest nextQuest = buildNextStepQuest(displayedQuest, req);
                if (nextQuest != null) {
                    if (isQuestAlreadyPlanned(nextQuest))
                        return true;
                    ((LinkedList<ExecutionQuest>) executionQueue).addFirst(nextQuest);
                    return true;
                }
            }
        }
        return false;
    }

    private ExecutionQuest buildNextStepQuest(QuestAPI.Quest displayedQuest, QuestAPI.Requirement req) {
        String reqType = req.getType();
        if (TYPE_KILL_NPC.equals(reqType)) {
            String npc = parseNpcName(req.getDescription());
            return npc != null ? createKillQuest(displayedQuest, req, npc) : null;
        }
        if (TYPE_COLLECT.equals(reqType)) {
            String ore = parseOreName(req.getDescription());
            return ore != null ? createCollectQuest(displayedQuest, req, ore) : null;
        }
        return null;
    }

    /**
     * Checks whether the given quest is already present in the execution queue,
     * matched by UUID.
     */
    private boolean isQuestAlreadyPlanned(ExecutionQuest candidate) {
        for (ExecutionQuest planned : executionQueue) {
            if (planned.questId != candidate.questId)
                continue;

            boolean uuidMatch = candidate.requirementUuid != null && planned.requirementUuid != null
                    && candidate.requirementUuid.equals(planned.requirementUuid);

            boolean legacyMatch = !uuidMatch
                    && ((TYPE_KILL_NPC.equals(candidate.type) && Objects.equals(planned.targetNpc, candidate.targetNpc))
                            || (TYPE_COLLECT.equals(candidate.type)
                                    && Objects.equals(planned.targetOre, candidate.targetOre)));

            if (uuidMatch || legacyMatch) {
                planned.active = true;
                return true;
            }
        }
        return false;
    }

    private boolean hasMoreIncompleteRequirements(QuestAPI.Quest displayedQuest) {
        return displayedQuest.getRequirements().stream()
                .anyMatch(req -> !req.isCompleted() && req.getProgress() < req.getGoal());
    }

    // =========================================================================
    // ExecutionQuest Creation
    // =========================================================================

    private ExecutionQuest createKillQuest(QuestAPI.Quest quest, QuestAPI.Requirement req, String targetNpc) {
        List<String> spawnMaps = NPC_SPAWNS.get(targetNpc);
        List<String> allowedMaps = parseMapRestrictionsFromSubRequirements(req);

        List<String> validMaps = null;
        if (!allowedMaps.isEmpty()) {
            validMaps = allowedMaps;
        } else if (spawnMaps != null && !spawnMaps.isEmpty()) {
            validMaps = spawnMaps;
        }

        if (validMaps == null)
            return null;

        String targetMap = resolvePreferredMap(validMaps);
        if (targetMap == null)
            return null;

        ExecutionQuest questData = new ExecutionQuest();
        questData.type = TYPE_KILL_NPC;
        questData.map = targetMap;
        questData.targetNpc = targetNpc;
        questData.questId = quest.getId();
        questData.questTitle = quest.getTitle();
        questData.remaining = req.getGoal() - req.getProgress();
        questData.active = req.isEnabled();
        questData.requirementUuid = findRequirementUuidForKill(quest.getId(), targetNpc, req.getDescription());

        return questData;
    }

    private ExecutionQuest createCollectQuest(QuestAPI.Quest quest, QuestAPI.Requirement req, String targetOre) {
        List<String> spawnMaps = ORE_SPAWNS.get(targetOre);
        if (spawnMaps == null || spawnMaps.isEmpty())
            return null;

        String targetMap = getBestMap(spawnMaps);
        if (targetMap == null)
            return null;

        targetMap = getFactionMap(targetMap);

        ExecutionQuest questData = new ExecutionQuest();
        questData.type = TYPE_COLLECT;
        questData.map = targetMap;
        questData.targetOre = targetOre;
        questData.questId = quest.getId();
        questData.questTitle = quest.getTitle();
        questData.remaining = req.getGoal() - req.getProgress();
        questData.active = true;
        questData.requirementUuid = findRequirementUuidForCollect(quest.getId(), targetOre);

        return questData;
    }

    // =========================================================================
    // Valid Map Calculation
    // =========================================================================

    /**
     * Called by ExecutionHandler to sync the target map if the bot's current map is
     * already valid.
     * This prevents unnecessary travel after a ship switch puts the bot on a valid
     * map.
     */
    public void tryUpdateTargetMapToCurrent(ExecutionQuest quest) {
        if (quest == null)
            return;
        String currentMap = module.travelHandler.getCurrentMap();
        if (currentMap == null || currentMap.equals(quest.map))
            return;

        AcceptedQuestData questData = module.acceptedQuestsData.get(quest.questId);
        if (questData == null || questData.parsedRequirements == null)
            return;

        QuestRequirement matchedReq = findMatchingRequirement(quest, questData);
        if (matchedReq == null)
            return;

        List<String> validMaps = getValidMapsForType(quest.type, matchedReq);
        if (validMaps != null && validMaps.contains(currentMap)) {
            quest.map = currentMap;
            module.travelHandler.setWorkingMap(currentMap);
        }
    }

    private QuestRequirement findMatchingRequirement(ExecutionQuest quest, AcceptedQuestData questData) {
        for (QuestRequirement req : questData.parsedRequirements) {
            if (isRequirementMatch(quest, req))
                return req;
        }
        return null;
    }

    private boolean isRequirementMatch(ExecutionQuest quest, QuestRequirement req) {
        if (quest.requirementUuid != null) {
            return quest.requirementUuid.equals(req.uuid);
        }
        if (req.type == null || !req.type.equals(quest.type))
            return false;

        if (TYPE_KILL_NPC.equals(quest.type) && quest.targetNpc != null) {
            return quest.targetNpc.equals(req.targetNpc);
        }
        if (TYPE_COLLECT.equals(quest.type) || TYPE_SALVAGE.equals(quest.type)) {
            return quest.targetOre != null && quest.targetOre.equals(req.targetOre);
        }
        return false;
    }

    private List<String> getValidMapsForType(String type, QuestRequirement req) {
        switch (type) {
            case TYPE_KILL_NPC:
                return getValidMapsForNpc(req);
            case TYPE_COLLECT:
                return getValidMapsForOre(req);
            case TYPE_SALVAGE:
                return FROM_SHIP_MAPS.stream().map(this::getFactionMap).collect(java.util.stream.Collectors.toList());
            case TYPE_SELL_ORE:
                return getValidMapsForSellOre();
            case TYPE_KILL_PLAYERS:
                return getValidMapsForKillPlayers(req);
            default:
                return java.util.Collections.emptyList();
        }
    }

    private List<String> getValidMapsForNpc(QuestRequirement req) {
        List<String> validMaps = new ArrayList<>();

        if (req.allowedMaps != null && !req.allowedMaps.isEmpty()) {
            validMaps.addAll(req.allowedMaps);
        } else {
            List<String> spawnMaps = NPC_SPAWNS.get(req.targetNpc);
            if (spawnMaps != null)
                validMaps.addAll(spawnMaps);
        }

        // Filter the PvP map (excluding Uber and open restriction exceptions)
        if (module.getConfig().questTypes.killNpcSettings.ignorePvpMaps) {
            boolean isUber = req.targetNpc != null && req.targetNpc.startsWith("Uber");
            boolean requiresPvpMapExplicitly = req.allowedMaps != null && req.allowedMaps.contains(PVP_MAP);

            if (!isUber && !requiresPvpMapExplicitly) {
                validMaps.remove(PVP_MAP);
            }
        }

        return validMaps;
    }

    private List<String> getValidMapsForOre(QuestRequirement req) {
        List<String> validMaps = new ArrayList<>();

        if (req.allowedMaps != null && !req.allowedMaps.isEmpty()) {
            validMaps.addAll(req.allowedMaps);
        } else {
            List<String> spawnMaps = ORE_SPAWNS.get(req.targetOre);
            if (spawnMaps != null)
                validMaps.addAll(spawnMaps);
        }

        return validMaps;
    }

    private List<String> getValidMapsForSellOre() {
        DailyQuestConfig.SellingMethod method = module.config.questTypes.sellOresSettings.sellingMethod;
        DailyQuestConfig.CollectionMethod collect = module.config.questTypes.sellOresSettings.collectionMethod;

        int factionId = module.travelHandler.getFactionCode();
        String faction = String.valueOf(factionId);
        String current = module.travelHandler.getCurrentMap();

        if (collect == DailyQuestConfig.CollectionMethod.CARGO) {
            return getSellMapsForCargo(method, faction, current);
        }
        if (collect == DailyQuestConfig.CollectionMethod.SKYLAB) {
            return getSellMapsForSkylab(method, faction, current);
        }
        return java.util.Arrays.asList(faction + "-1");
    }

    private List<String> getSellMapsForCargo(DailyQuestConfig.SellingMethod method, String faction,
            String current) {
        if (method == DailyQuestConfig.SellingMethod.BASE) {
            return java.util.Arrays.asList("5-2");
        }
        if (current.equals(faction + "-6") || current.equals(faction + "-7"))
            return java.util.Arrays.asList(current);
        return java.util.Arrays.asList(faction + "-6", faction + "-7");
    }

    private List<String> getSellMapsForSkylab(DailyQuestConfig.SellingMethod method, String faction,
            String current) {
        List<String> factionMaps = java.util.Arrays.asList(
                faction + "-1", faction + "-2", faction + "-3", faction + "-4",
                faction + "-5", faction + "-6", faction + "-7", faction + "-8", "5-2");

        if (method == DailyQuestConfig.SellingMethod.BASE) {
            if (!factionMaps.contains(current))
                return factionMaps;
            List<String> ordered = new ArrayList<>();
            ordered.add(current);
            for (String m : factionMaps) {
                if (!m.equals(current))
                    ordered.add(m);
            }
            return ordered;
        }

        // PET_TRADING or HM7
        if ("5-2".equals(current) || current.equals(faction + "-1") || current.equals(faction + "-8"))
            return java.util.Arrays.asList(current);
        if (current.startsWith(faction + "-") && !current.startsWith("4-"))
            return java.util.Arrays.asList(current);
        return java.util.Arrays.asList(faction + "-1", faction + "-2", faction + "-3", faction + "-4",
                faction + "-5", faction + "-6", faction + "-7", faction + "-8");
    }

    /**
     * Returns the single highest-priority PvP map for KILL_PLAYERS quests.
     * Priority: 4-5 > 4-4 > enemy faction 4-x > own faction 4-x
     * Returns only one map to force the planner to always use this map,
     * regardless of distance scoring.
     */
    private List<String> getValidMapsForKillPlayers(QuestRequirement req) {
        List<String> allowedMaps = req.allowedMaps;
        if (allowedMaps == null || allowedMaps.isEmpty()) {
            allowedMaps = Arrays.asList("4-1", "4-2", "4-3", "4-4", "4-5");
        }

        int factionId = module.travelHandler.getFactionCode();
        String ownMap = "4-" + factionId;

        // Priority 1: 4-5 (cross-faction PvP map — highest player density)
        if (allowedMaps.contains("4-5"))
            return Arrays.asList("4-5");

        // Priority 2: 4-4 (upper corner map)
        if (allowedMaps.contains("4-4"))
            return Arrays.asList("4-4");

        // Priority 3: Enemy faction 4-x maps
        for (String map : allowedMaps) {
            if (map.startsWith("4-") && !map.equals(ownMap)) {
                return Arrays.asList(map);
            }
        }

        // Priority 4: Own faction map (last resort)
        if (allowedMaps.contains(ownMap)) {
            return Arrays.asList(ownMap);
        }

        // Fallback
        return allowedMaps.isEmpty() ? Arrays.asList("4-5") : Arrays.asList(allowedMaps.get(0));
    }

    // =========================================================================
    // UUID Operations
    // =========================================================================

    /**
     * Generates a unique requirement UUID based on content hash and queue position.
     * Different requirements of the same type (e.g. Streuner twice) receive
     * different UUIDs.
     */
    private String generateRequirementUuid(int questId, String type, String description,
            String targetNpc, String targetOre, double progress, int index) {
        String content = String.format("%d|%s|%s|%s|%s|%.0f|%d",
                questId,
                type != null ? type : "",
                description != null ? description : "",
                targetNpc != null ? targetNpc : "",
                targetOre != null ? targetOre : "",
                progress, index);

        int hash = content.hashCode();
        return String.format("%d-%08X-%d", questId, hash & 0xFFFFFFFFL, index);
    }

    private String findRequirementUuidForKill(int questId, String targetNpc, String description) {
        AcceptedQuestData qd = module.acceptedQuestsData.get(questId);
        if (qd == null || qd.parsedRequirements == null)
            return null;

        for (QuestRequirement qr : qd.parsedRequirements) {
            if (TYPE_KILL_NPC.equals(qr.type) && targetNpc.equals(qr.targetNpc)
                    && !qr.completed && description.equals(qr.description)) {
                return qr.uuid;
            }
        }
        return null;
    }

    private String findRequirementUuidForCollect(int questId, String targetOre) {
        AcceptedQuestData qd = module.acceptedQuestsData.get(questId);
        if (qd == null || qd.parsedRequirements == null)
            return null;

        for (QuestRequirement qr : qd.parsedRequirements) {
            if (TYPE_COLLECT.equals(qr.type) && targetOre.equals(qr.targetOre) && !qr.completed) {
                return qr.uuid;
            }
        }
        return null;
    }

    // =========================================================================
    // Quest Progress Update
    // =========================================================================

    /**
     * Marks the completed quest's cached requirement as done.
     * Skips the update if the quest has no UUID.
     */
    public void updateAcceptedQuestDataProgress(ExecutionQuest quest) {
        AcceptedQuestData questData = module.acceptedQuestsData.get(quest.questId);
        if (questData == null || questData.parsedRequirements == null)
            return;

        if (quest.requirementUuid == null) {
            return;
        }

        for (QuestRequirement req : questData.parsedRequirements) {
            if (quest.requirementUuid.equals(req.uuid)) {
                req.completed = true;
                req.progress = req.goal;
                return;
            }
        }
    }

    // =========================================================================
    // Map & Requirement Matching — Helpers
    // =========================================================================

    /** Returns true if the requirement type can be parsed and tracked. */
    private boolean isParsableRequirementType(String type) {
        return TYPE_KILL_NPC.equals(type) ||
                TYPE_KILL_PLAYERS.equals(type) ||
                TYPE_COLLECT.equals(type) ||
                TYPE_SALVAGE.equals(type) ||
                TYPE_SELL_ORE.equals(type);
    }

    /**
     * Determines whether a live requirement matches the current quest.
     * UUID match takes priority; falls back to name matching when UUID is absent.
     */
    private boolean resolveRequirementMatch(QuestAPI.Requirement req, String reqType,
            int parsedIndex, AcceptedQuestData cachedData) {
        if (currentQuest.requirementUuid != null && cachedData != null
                && cachedData.parsedRequirements != null
                && parsedIndex < cachedData.parsedRequirements.size()) {
            QuestRequirement cached = cachedData.parsedRequirements.get(parsedIndex);
            if (currentQuest.requirementUuid.equals(cached.uuid))
                return true;
        }

        if (currentQuest.requirementUuid == null) {
            return fallbackMatchByName(req, reqType);
        }
        return false;
    }

    private boolean fallbackMatchByName(QuestAPI.Requirement req, String reqType) {
        if (TYPE_KILL_NPC.equals(reqType)) {
            String npc = parseNpcName(req.getDescription());
            return npc != null && npc.equals(currentQuest.targetNpc);
        }
        if (TYPE_COLLECT.equals(reqType)) {
            String ore = parseOreName(req.getDescription());
            return ore != null && ore.equals(currentQuest.targetOre);
        }
        if (TYPE_SALVAGE.equals(reqType)) {
            if (currentQuest.targetOre != null) {
                String ore = parseOreName(req.getDescription());
                return ore != null && ore.equals(currentQuest.targetOre);
            }
            return true;
        }
        if (TYPE_SELL_ORE.equals(reqType)) {
            if (currentQuest.targetOre != null) {
                String ore = parseOreName(req.getDescription());
                return ore != null && ore.equals(currentQuest.targetOre);
            }
            return true;
        }
        return false;
    }

    /** Extracts MAP restrictions from sub-requirements. */
    private List<String> parseMapRestrictionsFromSubRequirements(QuestAPI.Requirement req) {
        if (req.getRequirements() == null)
            return Collections.emptyList();
        for (QuestAPI.Requirement subReq : req.getRequirements()) {
            if ("MAP".equals(subReq.getType()))
                return parseMapNames(subReq.getDescription());
        }
        return Collections.emptyList();
    }

    /**
     * Converts a raw map name to its faction-specific equivalent via travelHandler.
     */
    private String getFactionMap(String rawMap) {
        return module.travelHandler.getFactionSpecificMapName(rawMap);
    }

    /**
     * Normalizes a list of map names to faction-specific equivalents, removing
     * duplicates.
     */

    /** Returns the current map if it is valid, otherwise the first allowed map. */
    private String getBestMap(List<String> allowedMaps) {
        if (allowedMaps == null || allowedMaps.isEmpty())
            return null;
        String currentMap = module.travelHandler.getCurrentMap();
        for (String m : allowedMaps) {
            if (getFactionMap(m).equals(currentMap))
                return currentMap;
        }
        return allowedMaps.get(0);
    }

    /** Resolves the preferred map, favouring the current map if valid. */
    private String resolvePreferredMap(List<String> validMaps) {
        String currentMap = module.travelHandler.getCurrentMap();
        for (String m : validMaps) {
            String factionMap = getFactionMap(m);
            if (factionMap.equals(currentMap))
                return factionMap;
        }
        String best = getBestMap(validMaps);
        return best != null ? getFactionMap(best) : null;
    }

    /** Returns all SimulationQuests whose validMaps include the given map. */

    /**
     * Returns the first candidate whose target matches lastTarget, or null if none.
     */

    private String getQuestTarget(ExecutionQuest quest) {
        return quest.targetNpc != null ? quest.targetNpc : quest.targetOre;
    }

    /** Returns true if the quest has an Armory Token reward. */
    private boolean hasArmoryTokenReward(QuestAPI.Quest quest) {
        if (quest == null || quest.getRewards() == null)
            return false;
        for (QuestAPI.Reward reward : quest.getRewards()) {
            if (reward.getType() != null && reward.getType().toString().contains(REWARD_TYPE))
                return true;
        }
        return false;
    }

    // =========================================================================
    // Map Change Tracking
    // =========================================================================

    private void updateMapStatus() {
        int mapId = module.hero.getMap().getId();
        if (mapId != currentMapId) {
            if (currentMapId != -1)
                lastMapChangeTime = System.currentTimeMillis();
            currentMapId = mapId;
        }
    }

    private boolean isMapChanging() {
        long now = System.currentTimeMillis();
        return (now - lastMapChangeTime < MAP_CHANGE_GRACE_PERIOD_MS)
                || (now - module.lastJumpTime < MAP_CHANGE_GRACE_PERIOD_MS);
    }

    public boolean isMapTransitioning() {
        return isMapChanging();
    }

    // =========================================================================
    // Quest Window Helper
    // =========================================================================

    /*
     * Logic: for each KILL_PLAYERS quest, find the earliest position in the plan
     * where the transition (prevMap → nextMap) makes the KILL_PLAYERS map a natural
     * so the ship switch happens once and all collect + subsequent steps on that
     * map
     */

    private String selectBestMap(List<SimulationQuest> readyQuests, String currentSimMap) {
        // Stay on the current map if there are quests here
        for (SimulationQuest quest : readyQuests) {
            for (String validMap : quest.quest.validMaps) {
                if (getFactionMap(validMap).equals(currentSimMap))
                    return currentSimMap;
            }
        }

        // Collect candidate maps
        Set<String> candidateMaps = new HashSet<>();
        for (SimulationQuest quest : readyQuests) {
            for (String validMap : quest.quest.validMaps) {
                candidateMaps.add(getFactionMap(validMap));
            }
        }

        return findBestCandidateMap(candidateMaps, readyQuests, currentSimMap);
    }

    private String findBestCandidateMap(Set<String> candidateMaps, List<SimulationQuest> readyQuests,
            String currentSimMap) {
        String bestMap = null;
        int maxScore = Integer.MIN_VALUE;

        for (String candidate : candidateMaps) {
            int distance = module.travelHandler.getShortestPath(currentSimMap, candidate);
            if (distance < 0)
                continue;

            int score = calculateMapScore(candidate, readyQuests, currentSimMap, distance);

            if (score > maxScore || (score == maxScore && (bestMap == null || candidate.compareTo(bestMap) < 0))) {
                maxScore = score;
                bestMap = candidate;
            }
        }

        return bestMap;
    }

    private int calculateMapScore(String candidate, List<SimulationQuest> readyQuests, String currentSimMap,
            int distance) {
        int questCount = 0;
        int lookaheadScore = 0;

        for (SimulationQuest quest : readyQuests) {
            boolean validForCandidate = quest.quest.validMaps.stream()
                    .anyMatch(vm -> getFactionMap(vm).equals(candidate));

            if (validForCandidate) {
                questCount++;
                lookaheadScore += calculateLookaheadScore(quest.nextStep, candidate);
            }
        }

        int skipPenalty = calculateSkipPenalty(readyQuests, currentSimMap, candidate);
        return (questCount * 100) - (distance * 250) + lookaheadScore - skipPenalty;
    }

    private ExecutionQuest buildExecutionQuest(QuestRequirement req, AcceptedQuestData questData) {
        ExecutionQuest quest = new ExecutionQuest();

        if (TYPE_KILL_NPC.equals(req.type) && req.targetNpc != null) {
            quest.type = TYPE_KILL_NPC;
            quest.targetNpc = req.targetNpc;
            quest.validMaps = getValidMapsForNpc(req);
        } else if (TYPE_COLLECT.equals(req.type) && req.targetOre != null) {
            quest.type = TYPE_COLLECT;
            quest.targetOre = req.targetOre;
            quest.validMaps = getValidMapsForOre(req);
        } else if (TYPE_SALVAGE.equals(req.type)) {
            quest.type = TYPE_SALVAGE;
            quest.targetOre = req.targetOre;
            quest.validMaps = new ArrayList<>(FROM_SHIP_MAPS.stream()
                    .map(this::getFactionMap)
                    .collect(java.util.stream.Collectors.toList()));
        } else if (TYPE_SELL_ORE.equals(req.type)) {
            quest.type = TYPE_SELL_ORE;
            quest.targetOre = parseOreName(req.description);
            quest.validMaps = getValidMapsForSellOre();
        } else if (TYPE_KILL_PLAYERS.equals(req.type)) {
            quest.type = TYPE_KILL_PLAYERS;
            quest.validMaps = getValidMapsForKillPlayers(req);
        } else {
            return null;
        }

        quest.questId = questData.id;
        quest.questTitle = questData.title;
        quest.remaining = req.goal - req.progress;
        quest.active = req.enabled;
        quest.requirementUuid = req.uuid;

        return quest;
    }

    private boolean isQuestTypeEnabled(String type) {
        DailyQuestConfig.QuestTypesSettings qt = module.config.questTypes;
        switch (type) {
            case TYPE_KILL_NPC:
                return qt.killNpcSettings.killNpc;
            case TYPE_COLLECT:
            case TYPE_SALVAGE:
                return qt.collectOresSettings.collectOres;
            case TYPE_SELL_ORE:
                return qt.sellOresSettings.sellOres;
            case TYPE_KILL_PLAYERS:
                return qt.killPlayersSettings.killPlayers;
            default:
                return false;
        }
    }

    private List<String> normalizeMapsToFaction(List<String> maps) {
        List<String> normalized = new ArrayList<>();
        for (String m : maps) {
            String factionMap = getFactionMap(m);
            if (!normalized.contains(factionMap))
                normalized.add(factionMap);
        }
        return normalized;
    }

    private List<SimulationQuest> getCandidatesForMap(List<SimulationQuest> quests, String map) {
        List<SimulationQuest> result = new ArrayList<>();
        for (SimulationQuest t : quests) {
            if (t.quest.validMaps.contains(map))
                result.add(t);
        }
        return result;
    }

    private SimulationQuest findQuestWithSameTarget(List<SimulationQuest> candidates, String lastTarget) {
        if (lastTarget == null)
            return null;
        for (SimulationQuest t : candidates) {
            if (lastTarget.equals(getQuestTarget(t.quest)))
                return t;
        }
        return null;
    }

    /**
     * Logic: for each KILL_PLAYERS quest, find the earliest position in the plan
     * where the transition (prevMap → nextMap) makes the KILL_PLAYERS map a natural
     * detour (i.e., KILL_PLAYERS map is adjacent to prevMap AND adjacent to
     * nextMap,
     * or it lies on the shortest path between the two map groups).
     */
    private void reorderKillPlayersOnRoute(List<ExecutionQuest> plan) {
        // Separate KILL_PLAYERS from the rest
        List<ExecutionQuest> kpQuests = new ArrayList<>();
        List<ExecutionQuest> others = new ArrayList<>();
        for (ExecutionQuest q : plan) {
            if (TYPE_KILL_PLAYERS.equals(q.type)) {
                kpQuests.add(q);
            } else {
                others.add(q);
            }
        }
        if (kpQuests.isEmpty())
            return;

        executionQueue.clear();

        for (ExecutionQuest kp : kpQuests) {
            insertKillPlayerQuestOptimally(kp, others);
        }

        executionQueue.addAll(others);
    }

    private void insertKillPlayerQuestOptimally(ExecutionQuest kp, List<ExecutionQuest> others) {
        String kpMap = kp.map;
        if (kpMap == null) {
            others.add(kp);
            return;
        }

        List<String> mapSequence = new ArrayList<>();
        for (ExecutionQuest q : others) {
            if (q.map != null && (mapSequence.isEmpty() || !mapSequence.get(mapSequence.size() - 1).equals(q.map))) {
                mapSequence.add(q.map);
            }
        }

        int bestScore = Integer.MAX_VALUE;
        int bestIdx = others.size();

        for (int i = 0; i < mapSequence.size(); i++) {
            String prevMap = mapSequence.get(i);
            String nextMap = (i + 1 < mapSequence.size()) ? mapSequence.get(i + 1) : null;

            int extraCost = calculateDetourCost(prevMap, kpMap, nextMap);
            if (extraCost >= 0 && extraCost <= bestScore) {
                bestScore = extraCost;
                bestIdx = findInsertionIndexAfterSegment(others, prevMap);
            }
        }

        others.add(bestIdx, kp);
    }

    private int calculateDetourCost(String prevMap, String kpMap, String nextMap) {
        int distPrevKp = module.travelHandler.getShortestPath(prevMap, kpMap);
        if (distPrevKp < 0)
            return -1;

        if (nextMap == null)
            return distPrevKp;

        int distKpNext = module.travelHandler.getShortestPath(kpMap, nextMap);
        int distPrevNext = module.travelHandler.getShortestPath(prevMap, nextMap);

        if (distKpNext < 0 || distPrevNext < 0)
            return -1;

        return distPrevKp + distKpNext - distPrevNext;
    }

    private int findInsertionIndexAfterSegment(List<ExecutionQuest> others, String segmentMap) {
        int idx = 0;
        boolean inSegment = false;
        for (int j = 0; j < others.size(); j++) {
            if (segmentMap.equals(others.get(j).map)) {
                inSegment = true;
                idx = j + 1;
            } else if (inSegment) {
                break;
            }
        }
        return idx;
    }

    /*
     * so the ship switch happens once and all collect + subsequent steps on that
     * map
     * use the switched ship (or it's restored before the kill quests start).
     *
     * If no matching map is found, appends at end.
     */
    private int findBestCollectInsertionIndex(List<ExecutionQuest> otherQuests,
            List<ExecutionQuest> collectQuests) {
        // Gather all valid maps for the collect quests
        Set<String> collectMaps = new java.util.LinkedHashSet<>();
        for (ExecutionQuest cq : collectQuests) {
            collectMaps.addAll(cq.validMaps);
        }

        // Insert BEFORE the first step whose map matches a collect map.
        // This ensures: arrive at map → SALVAGE (with collect ship) → restore →
        // KILL_NPC
        for (int i = 0; i < otherQuests.size(); i++) {
            if (collectMaps.contains(otherQuests.get(i).map)) {
                return i; // insert before this step
            }
        }
        return otherQuests.size(); // fallback: append at end
    }

    private void tryOpenQuestWindow() {
        try {
            if (module.main.guiManager.quests != null) {
                module.main.guiManager.quests.show(true);
            }
        } catch (Exception ignored) { // quest window may not be available
        }
    }

    // =========================================================================
    // Inner Class: SimulationQuest
    // =========================================================================

    /** Wrapper used during execution plan simulation. */
    private static class SimulationQuest {
        final ExecutionQuest quest;
        SimulationQuest nextStep; // Next step in a sequential chain

        SimulationQuest(ExecutionQuest quest, boolean sequential) {
            this.quest = quest;
        }
    }
}