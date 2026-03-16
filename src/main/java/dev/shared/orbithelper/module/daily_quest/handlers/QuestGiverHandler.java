package dev.shared.orbithelper.module.daily_quest.handlers;

import dev.shared.orbithelper.utils.VirtualWindow;
import eu.darkbot.api.utils.NativeAction;
import dev.shared.orbithelper.module.daily_quest.Constants;
import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.model.AcceptedQuestData;
import dev.shared.orbithelper.module.daily_quest.model.QuestRequirement;

import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.managers.QuestAPI;

import java.util.ArrayList;
import java.util.Comparator;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.manager.MapManager;

public class QuestGiverHandler {
    // -------------------------------------------------------------------------
    // Quest Giver window — 840x550, centered on screen
    // -------------------------------------------------------------------------
    private static final double WIN_W = 840.0D;
    private static final double WIN_H = 550.0D;
    private static final double WIN_HALF_W = WIN_W / 2.0D; // 420
    private static final double WIN_HALF_H = WIN_H / 2.0D; // 275

    // -------------------------------------------------------------------------
    // Tabs region — window left+8, top+35, size 813x21, 5 tabs total
    // Target: tab id=2 → 3rd slot → 1-based index 3
    // -------------------------------------------------------------------------
    private static final double TABS_OFF_X = 20.0D;
    private static final double TABS_OFF_Y = 45.0D;
    private static final double TABS_W = 813.0D;
    private static final double TABS_H = 21.0D;
    private static final int TABS_COUNT = 5;
    private static final int TARGET_TAB = 3;

    // -------------------------------------------------------------------------
    // Quest list region — window left+8, top+305, size 238x206, 6 visible rows
    // -------------------------------------------------------------------------
    private static final double LIST_OFF_X = 13.0D;
    private static final double LIST_OFF_Y = 315.0D;
    private static final double LIST_W = 238.0D;
    private static final double LIST_H = 210.0D;
    private static final int LIST_ROWS = 6;

    // -------------------------------------------------------------------------
    // Accept button — measured inward from window's bottom-right corner
    // -------------------------------------------------------------------------
    private static final double ACCEPT_MARGIN_RIGHT = 10.0D;
    private static final double ACCEPT_MARGIN_BOTTOM = 35.0D;
    private static final double ACCEPT_W = 165.0D;
    private static final double ACCEPT_H = 28.0D;

    // -------------------------------------------------------------------------
    // Timing / retry constants
    // -------------------------------------------------------------------------
    private static final long SETTLE_DELAY_MS = 500;
    private static final int MAX_TAB_ATTEMPTS = 3;
    private static final long BROWSE_DELAY_MS = 400;
    private static final int MAX_ACCEPT_RETRIES = 6;

    // -------------------------------------------------------------------------
    // Module reference
    // -------------------------------------------------------------------------
    private final DailyQuestModule module;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private long windowOpenedAt = 0;
    private boolean windowProcessed = false;
    private int tabAttempts = 0;
    private int browseRow = 0;
    private int lastQuestId = -1;
    private int browsePhase = 0;
    private int afterAcceptPhase = 0;
    private long lastActionTime = 0;
    private int acceptRetries = 0;
    private boolean firstScrollDone = false;
    /**
     * ID of the bottom-row quest after the last scroll — used to detect end of
     * list.
     */
    private int lastBottomQuestId = -1;
    /** How many consecutive scrolls had the same bottom-row quest ID. */
    private int sameBottomIdCount = 0;
    private static final int MAX_SAME_BOTTOM = 3;
    private boolean hasClickedClose = false;

    public QuestGiverHandler(DailyQuestModule module) {
        this.module = module;
    }

    // =========================================================================
    // Region builders — each derives from the window anchor
    // =========================================================================

    /**
     * Full Quest Giver popup.
     * Screen bounds come from gameScreenAPI — no manual dimension calculation
     * needed.
     * Window size: 840x550, always centered on the game view.
     */
    private VirtualWindow buildWindow() {
        int screenWidth = MapManager.clientWidth;
        int screenHeight = MapManager.clientHeight;

        double centerX = screenWidth / 2.0D;
        double centerY = screenHeight / 2.0D;
        double startX = centerX - WIN_HALF_W;
        double startY = centerY - WIN_HALF_H;

        return VirtualWindow.fromBounds(startX, startY, WIN_W, WIN_H);
    }

    /** Tab bar region derived from window origin. */
    private VirtualWindow buildTabs() {
        VirtualWindow w = buildWindow();
        return VirtualWindow.fromBounds(w.x() + TABS_OFF_X, w.y() + TABS_OFF_Y, TABS_W, TABS_H);
    }

    /** Quest list region derived from window origin. */
    private VirtualWindow buildQuestList() {
        VirtualWindow w = buildWindow();
        return VirtualWindow.fromBounds(w.x() + LIST_OFF_X, w.y() + LIST_OFF_Y, LIST_W, LIST_H);
    }

    /** Accept button region derived from window bottom-right corner. */
    private VirtualWindow buildAcceptButton() {
        VirtualWindow w = buildWindow();
        double startX = (w.endX() - ACCEPT_MARGIN_RIGHT) - ACCEPT_W;
        double startY = (w.endY() - ACCEPT_MARGIN_BOTTOM) - ACCEPT_H;
        return VirtualWindow.fromBounds(startX, startY, ACCEPT_W, ACCEPT_H);
    }

    // =========================================================================
    // Public state handlers
    // =========================================================================

    public void reset() {
        windowOpenedAt = 0;
        windowProcessed = false;
        tabAttempts = 0;
        browseRow = 0;
        lastQuestId = -1;
        browsePhase = 0;
        acceptRetries = 0;
        firstScrollDone = false;
        lastBottomQuestId = -1;
        sameBottomIdCount = 0;
        hasClickedClose = false;
    }

    public void handleOpeningQuestGiverWindow() {
        if (closeQuestGuiIfVisible()) {
            return;
        }

        if (module.questApi.isQuestGiverOpen()) {
            handleOpenQuestGiverState();
            return;
        }

        if (!windowProcessed)
            windowProcessed = true;

        Station.QuestGiver qg = getQuestGiver();
        if (qg == null) {
            module.state = module.config.acceptQuest
                    ? DailyQuestModule.State.INITIALIZING
                    : DailyQuestModule.State.COMPLETE;
            return;
        }

        if (module.hero.distanceTo(qg) < 200)
            qg.trySelect(false);
        else
            module.movement.moveTo(qg);
    }

    private boolean closeQuestGuiIfVisible() {
        eu.darkbot.api.game.other.Gui questGui = module.guiApi.getGui("quests");
        if (questGui != null && questGui.isVisible()) {
            try {
                if (module.main.guiManager.quests != null)
                    module.main.guiManager.quests.show(false);
            } catch (Exception ignored) {
                // Quest GUI close might throw if guiManager isn't ready — safe to ignore
            }
            return true;
        }
        return false;
    }

    private void handleOpenQuestGiverState() {
        if (!windowProcessed) {
            moveAwayFromQuestGiver();
            return;
        }
        if (windowOpenedAt == 0) {
            windowOpenedAt = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - windowOpenedAt < SETTLE_DELAY_MS)
            return;
        module.state = DailyQuestModule.State.CHANGING_QUEST_GIVER_TAB;
    }

    private Station.QuestGiver getQuestGiver() {
        return module.entities.getStations().stream()
                .filter(s -> s instanceof Station.QuestGiver)
                .map(s -> (Station.QuestGiver) s)
                .findFirst().orElse(null);
    }

    public void moveAwayFromQuestGiver() {
        Station.QuestGiver qg = module.entities.getStations().stream()
                .filter(s -> s instanceof Station.QuestGiver)
                .map(s -> (Station.QuestGiver) s)
                .findFirst().orElse(null);

        if (qg == null || module.hero.distanceTo(qg) >= 500)
            return;

        Portal nearestPortal = module.entities.getPortals().stream()
                .filter(p -> !p.getTargetMap().map(m -> m.isGG()).orElse(true))
                .min(Comparator.comparingDouble(p -> module.hero.distanceTo(p))).orElse(null);

        Station nearestStation = module.entities.getStations().stream()
                .filter(s -> s instanceof Station.Refinery || s instanceof Station.Repair)
                .min(Comparator.comparingDouble(s -> module.hero.distanceTo(s))).orElse(null);

        if (nearestPortal != null && nearestStation != null) {
            if (module.hero.distanceTo(nearestPortal) < module.hero.distanceTo(nearestStation))
                module.movement.moveTo(nearestPortal);
            else
                module.movement.moveTo(nearestStation);
        } else if (nearestPortal != null)
            module.movement.moveTo(nearestPortal);
        else if (nearestStation != null)
            module.movement.moveTo(nearestStation);
    }

    public void handleChangingQuestGiverTab() {
        if (!module.questApi.isQuestGiverOpen()) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        // Previous click succeeded — tab is now correct
        if (tabAttempts > 0 && module.questApi.getSelectedTab() == 2) {
            tabAttempts = 0;
            browseRow = 0;
            lastQuestId = -1;
            browsePhase = 0;
            lastActionTime = 0;
            module.state = DailyQuestModule.State.BROWSING_QUESTS;
            return;
        }

        if (tabAttempts >= MAX_TAB_ATTEMPTS) {
            tabAttempts = 0;
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        // Click the center of the target tab slot.
        // Formula: tabsStartX + (tabWidth * tabIndex) - (tabWidth / 2)
        // This lands exactly on the horizontal center of the target slot.
        VirtualWindow tabs = buildTabs();
        double tabSlotW = tabs.width() / (double) TABS_COUNT;
        int clickX = (int) (tabs.x() + (tabSlotW * TARGET_TAB) - (tabSlotW / 2.0D));
        int clickY = (int) tabs.centerY();

        try {
            Main.API.mouseClick(clickX, clickY);
            tabAttempts++;
        } catch (Exception e) {
            tabAttempts = 0;
            module.state = DailyQuestModule.State.COMPLETE;
        }
    }

    public void handleBrowsingQuests() {
        if (!module.questApi.isQuestGiverOpen()) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }
        if (System.currentTimeMillis() - lastActionTime < BROWSE_DELAY_MS)
            return;

        switch (browsePhase) {
            case 0:
                clickQuestRow(browseRow);
                browsePhase = 1;
                break;
            case 1:
                handleReadQuestInfoPhase();
                break;
            case 2:
                handleScrollListPhase();
                break;
            case 3:
                clickQuestRow(browseRow);
                browsePhase = 4;
                break;
            case 4:
                checkScrollResult();
                break;
            case 5:
                clickAcceptButton();
                browsePhase = 6;
                break;
            case 6:
                handleAcceptResultPhase();
                break;
            default:
                module.state = DailyQuestModule.State.COMPLETE;
        }
    }

    private void handleReadQuestInfoPhase() {
        readQuestInfo();
        if (hasArmoryTokenReward()) {
            boolean alreadyActive = isQuestAlreadyActive();
            captureQuestData();
            if (!alreadyActive) {
                afterAcceptPhase = (browseRow < 5) ? 0 : 2;
                browsePhase = 5;
            } else {
                advanceRow();
            }
        } else {
            advanceRow();
        }
    }

    private void handleScrollListPhase() {
        scrollQuestList();
        // First scroll: click last 2 quests → start from row 4 (LIST_ROWS - 2)
        // Subsequent scrolls: click last 3 quests → start from row 3 (LIST_ROWS - 3)
        browseRow = firstScrollDone ? LIST_ROWS - 3 : LIST_ROWS - 2;
        firstScrollDone = true;
        browsePhase = 3;
    }

    private void handleAcceptResultPhase() {
        if (System.currentTimeMillis() - lastActionTime < 250)
            return;

        boolean stillActivable = false;
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        if (info != null && info.getId() == lastQuestId)
            stillActivable = info.isActivable();

        if (stillActivable) {
            if (System.currentTimeMillis() - lastActionTime < 500)
                return;
            acceptRetries++;
            if (acceptRetries >= MAX_ACCEPT_RETRIES) {
                acceptRetries = 0;
                lastQuestId = -1;
                advanceAfterAccept();
            } else {
                browsePhase = 5;
            }
        } else {
            acceptRetries = 0;
            lastQuestId = -1;
            advanceAfterAccept();
        }
    }

    public void handleClosingQuestGiver() {
        if (module.questApi.isQuestGiverOpen()) {
            moveAwayFromQuestGiver();
            return;
        }

        if (!hasClickedClose) {
            // Click screen center to deselect the quest giver window and re-enable NPC
            // combat
            int screenW = MapManager.clientWidth;
            int screenH = MapManager.clientHeight;
            try {
                Main.API.mouseClick(screenW / 2, screenH / 2 + 100);
            } catch (Exception ignored) {
                // Mouse click may fail if API isn't ready — safe to ignore here
            }
            hasClickedClose = true;
        }

        module.state = DailyQuestModule.State.ANALYZING_QUESTS;
    }

    // =========================================================================
    // Click helpers — all coordinates derived from VirtualWindow regions
    // =========================================================================

    /**
     * Clicks a row in the quest list (0-based, 0 = top, 5 = bottom).
     * Row height = list height / LIST_ROWS.
     * Click Y = list top + (rowHeight * row) + (rowHeight / 2)
     */
    private void clickQuestRow(int row) {
        VirtualWindow list = buildQuestList();
        double rowH = list.height() / (double) LIST_ROWS;
        int clickX = (int) list.centerX();
        int clickY = (int) (list.y() + (rowH * row) + (rowH / 2.0D));
        doClick(clickX, clickY);
    }

    private void scrollQuestList() {
        VirtualWindow list = buildQuestList();
        int scrollX = (int) list.centerX();
        int scrollY = (int) list.centerY();
        long action = NativeAction.MouseWheel.down(scrollX, scrollY);
        Main.API.postActions(action);
        lastActionTime = System.currentTimeMillis();
    }

    private void clickAcceptButton() {
        VirtualWindow btn = buildAcceptButton();
        doClick((int) btn.centerX(), (int) btn.centerY());
    }

    private void doClick(int x, int y) {
        try {
            Main.API.mouseClick(x, y);
            lastActionTime = System.currentTimeMillis();
        } catch (Exception e) {
            module.state = DailyQuestModule.State.COMPLETE;
        }
    }

    // =========================================================================
    // Browse flow helpers
    // =========================================================================

    private void advanceRow() {
        if (browseRow < LIST_ROWS - 1) {
            browseRow++;
            browsePhase = 0;
        } else {
            browsePhase = 2;
        }
    }

    private void advanceAfterAccept() {
        if (afterAcceptPhase == 0) {
            // Normal browsing section: advance one row
            browseRow++;
            browsePhase = 0;
        } else if (browseRow < LIST_ROWS - 1) {
            // Post-scroll section: still have rows to check
            browseRow++;
            browsePhase = 3;
        } else {
            browsePhase = 2;
        }
    }

    // =========================================================================
    // Quest reading / data capture — logic unchanged
    // =========================================================================

    private void readQuestInfo() {
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        if (info == null)
            return;
        int currentId = info.getId();
        if (currentId == lastQuestId) {
            module.state = DailyQuestModule.State.CLOSING_QUEST_GIVER;
            return;
        }
        lastQuestId = currentId;
    }

    private void checkScrollResult() {
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        if (info == null) {
            browsePhase = 2;
            return;
        }

        int currentId = info.getId();

        if (handleEndOfListDetection(currentId)) {
            return;
        }

        if (currentId == lastQuestId) {
            handleSameQuestClicked();
            return;
        }

        lastQuestId = currentId;
        processQuestAfterScroll();
    }

    private boolean handleEndOfListDetection(int currentId) {
        if (browseRow == LIST_ROWS - 1) {
            if (currentId == lastBottomQuestId) {
                sameBottomIdCount++;
                if (sameBottomIdCount >= MAX_SAME_BOTTOM) {
                    module.state = DailyQuestModule.State.CLOSING_QUEST_GIVER;
                    return true;
                }
            } else {
                lastBottomQuestId = currentId;
                sameBottomIdCount = 0;
            }
        }
        return false;
    }

    private void handleSameQuestClicked() {
        if (browseRow < LIST_ROWS - 1) {
            browseRow++;
            browsePhase = 3;
        } else {
            browsePhase = 2;
        }
    }

    private void processQuestAfterScroll() {
        if (hasArmoryTokenReward()) {
            boolean alreadyActive = isQuestAlreadyActive();
            captureQuestData();
            if (!alreadyActive) {
                afterAcceptPhase = 2;
                browsePhase = 5;
            } else {
                advanceRowPostScroll();
            }
        } else {
            advanceRowPostScroll();
        }
    }

    private void advanceRowPostScroll() {
        if (browseRow < LIST_ROWS - 1) {
            browseRow++;
            browsePhase = 3;
        } else {
            browsePhase = 2;
        }
    }

    private boolean hasArmoryTokenReward() {
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        QuestAPI.Quest quest = module.questApi.getSelectedQuest();
        if (info == null || quest == null || quest.getRewards() == null)
            return false;
        for (QuestAPI.Reward r : quest.getRewards())
            if (r.getType() != null && r.getType().toString().contains(Constants.REWARD_TYPE))
                return true;
        return false;
    }

    private boolean isQuestAlreadyActive() {
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        if (info == null)
            return false;

        // If already captured this session, skip
        if (module.acceptedQuestsData.containsKey(info.getId()))
            return true;

        return !info.isActivable();
    }

    private void captureQuestData() {
        QuestAPI.QuestListItem info = module.questApi.getSelectedQuestInfo();
        QuestAPI.Quest quest = module.questApi.getSelectedQuest();
        if (info == null || module.acceptedQuestsData.containsKey(info.getId()))
            return;

        AcceptedQuestData data = new AcceptedQuestData();
        data.id = info.getId();
        data.title = info.getTitle();
        data.type = info.getType();
        data.completed = info.isCompleted();

        populateRewardsData(data, quest);
        populateRequirementsData(data, quest, info);
        populateExtraFlags(data, quest);

        module.acceptedQuestIds.add(data.id);
        module.acceptedQuestsData.put(data.id, data);
    }

    private void populateRewardsData(AcceptedQuestData data, QuestAPI.Quest quest) {
        if (quest != null && quest.getRewards() != null) {
            data.rewards = new ArrayList<>();
            for (QuestAPI.Reward r : quest.getRewards())
                data.rewards.add(r.getType() + " x" + r.getAmount());
        }
    }

    private void populateExtraFlags(AcceptedQuestData data, QuestAPI.Quest quest) {
        if (quest == null || quest.getRequirements() == null) {
            return;
        }
        for (QuestAPI.Requirement req : quest.getRequirements()) {
            checkRequirementFlags(data, req);
            checkSubRequirementFlags(data, req);
        }
    }

    private void checkRequirementFlags(AcceptedQuestData data, QuestAPI.Requirement req) {
        if (!"REAL_TIME_HASTE".equals(req.getType()) && !req.isEnabled()) {
            data.isSequential = true;
        }
        if ("AVOID_DEATH".equals(req.getType())) {
            data.hasAvoidDeath = true;
        }
    }

    private void checkSubRequirementFlags(AcceptedQuestData data, QuestAPI.Requirement req) {
        if (req.getRequirements() == null) {
            return;
        }
        for (QuestAPI.Requirement sub : req.getRequirements()) {
            if ("AVOID_DEATH".equals(sub.getType())) {
                data.hasAvoidDeath = true;
            }
        }
    }

    private void populateRequirementsData(AcceptedQuestData data, QuestAPI.Quest quest, QuestAPI.QuestListItem info) {
        if (quest == null || quest.getRequirements() == null) {
            return;
        }
        data.requirements = new ArrayList<>();
        data.parsedRequirements = new ArrayList<>();
        int idx = 0;

        for (QuestAPI.Requirement req : quest.getRequirements()) {
            idx = processSingleRequirement(data, req, info, idx);
        }
    }

    private int processSingleRequirement(AcceptedQuestData data, QuestAPI.Requirement req, QuestAPI.QuestListItem info,
            int idx) {
        String reqType = req.getType();
        data.requirements.add("Req: " + req.getDescription()
                + " | Type: " + reqType
                + " | Progress: " + req.getProgress() + "/" + req.getGoal()
                + " | Completed: " + req.isCompleted()
                + " | Enabled: " + req.isEnabled());

        if (isParseableRequirementType(reqType)) {
            QuestRequirement parsed = parseRequirement(data, req, reqType, info, idx);
            data.parsedRequirements.add(parsed);
            return idx + 1;
        }

        if (!"MAP".equals(reqType) && !"REAL_TIME_HASTE".equals(reqType)) {
            data.requirements.add("  [UNKNOWN TYPE: " + reqType + "]");
        }
        return idx;
    }

    private boolean isParseableRequirementType(String reqType) {
        return "KILL_NPC".equals(reqType) || "KILL_PLAYERS".equals(reqType)
                || "COLLECT".equals(reqType) || "SALVAGE".equals(reqType)
                || "SELL_ORE".equals(reqType);
    }

    private QuestRequirement parseRequirement(AcceptedQuestData data, QuestAPI.Requirement req, String reqType,
            QuestAPI.QuestListItem info, int idx) {
        QuestRequirement parsed = new QuestRequirement();
        parsed.description = req.getDescription();
        parsed.type = reqType;
        if ("KILL_NPC".equals(reqType)) {
            parsed.targetNpc = module.questManager.parseNpcName(req.getDescription());
        } else if ("COLLECT".equals(reqType) || "SELL_ORE".equals(reqType) || "SALVAGE".equals(reqType)) {
            parsed.targetOre = module.questManager.parseOreName(req.getDescription());
        }
        parsed.progress = req.getProgress();
        parsed.goal = req.getGoal();
        parsed.completed = req.isCompleted();
        parsed.enabled = req.isEnabled();
        parsed.uuid = generateRequirementUuid(info.getId(), parsed, idx);

        if (req.getRequirements() != null) {
            for (QuestAPI.Requirement sub : req.getRequirements()) {
                data.requirements.add("  L2: " + sub.getDescription()
                        + " | Type: " + sub.getType()
                        + " | Enabled: " + sub.isEnabled());
                if ("MAP".equals(sub.getType()))
                    parsed.allowedMaps = module.questManager.parseMapNames(sub.getDescription());
            }
        }
        return parsed;
    }

    private String generateRequirementUuid(int questId, QuestRequirement parsed, int index) {
        String s = String.format("%d|%s|%s|%s|%s|%.0f|%.0f|%d",
                questId,
                parsed.type != null ? parsed.type : "",
                parsed.description != null ? parsed.description : "",
                parsed.targetNpc != null ? parsed.targetNpc : "",
                parsed.targetOre != null ? parsed.targetOre : "",
                parsed.progress, parsed.goal, index);
        return String.format("%d-%08X-%d", questId, s.hashCode() & 0xFFFFFFFFL, index);
    }
}