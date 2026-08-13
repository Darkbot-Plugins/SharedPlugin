package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;
import eu.darkbot.api.managers.StarSystemAPI;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Standalone daily-quest module.
 *
 * <p>This class deliberately does not extend LootCollectorModule and never
 * reads or writes the user's working-map, NPC-selection, box-selection or
 * ore-selling settings. Selecting another DarkBot module therefore leaves
 * that module's saved settings untouched.</p>
 */
@Feature(name = "DailyTaskAPI", description = "Completes only verified 24-hour daily quests")
public final class DailyTaskAPI implements Module, Configurable<DailyTaskConfig> {
    private static final String QUEST_BASE_MAP = "1-8";
    private static final String PARK_AFTER_DAILY = "__PARK_AFTER_DAILY__";
    private static final double QUEST_STATION_DISTANCE = 300d;
    private static final Set<Integer> SPECIAL_DAILY_QUEST_IDS = Set.of(318001);

    private enum State {
        STARTING, DISCOVERING, WAITING_FOR_DISCOVERY,
        SELECTING, WAITING_FOR_SELECTION, RUNNING,
        OPENING_GIVER, WAITING_GIVER_OPEN,
        SELECTING_DAILY_TAB, WAITING_DAILY_TAB,
        SELECTING_GIVER_ROW, WAITING_GIVER_ROW,
        ACCEPTING_GIVER_QUEST, WAITING_GIVER_ACCEPT,
        CLOSING_GIVER, PAUSED, DONE
    }

    private static final class QuestChoice {
        private final int id;
        private final String title;
        private final QuestMenuSource.Selector selector;
        private final int priority;

        private QuestChoice(int id, String title, QuestMenuSource.Selector selector, int priority) {
            this.id = id;
            this.title = title;
            this.selector = selector;
            this.priority = priority;
        }

        private int id() {
            return id;
        }

        private String title() {
            return title;
        }

        private QuestMenuSource.Selector selector() {
            return selector;
        }

        private int priority() {
            return priority;
        }
    }

    private final QuestAPI quests;
    private final BotAPI bot;
    private final GameScreenAPI gameScreen;
    private final StarSystemAPI starSystem;
    private final OreAPI ores;
    private final EntitiesAPI entities;
    private final MovementAPI movement;
    private final AttackAPI attack;
    private final HeroAPI hero;
    private final Gui questGui;
    private final QuestMenuSource questMenuSource;
    private final QuestGiverSource questGiverSource;
    private final DailyNpcCombat npcCombat;
    private final DailyPlayerCombat playerCombat;

    private DailyTaskConfig settings = new DailyTaskConfig();
    private State state = State.STARTING;
    private int selectionRetries;
    private int menuScanPass;
    private int selectorIndex;
    private int questIdBeforeSelection = -1;
    private int currentQuestId = -1;
    private String currentQuestTitle = "";
    private long nextActionAt;
    private long nextRoamAt;
    private String status = "Starting";
    private String targetNpcDescription;
    private String targetPlayerDescription;
    private String targetMapName;
    private OreAPI.Ore oreToSell;
    private boolean collectBonus;
    private boolean collectCargo;
    private Locatable targetCoordinates;
    private double lastTrackedProgress = -1d;
    private long lastProgressAt;
    private final Set<Integer> completedQuestIds = new HashSet<>();
    private final Set<Integer> knownDailyQuestIds = new HashSet<>();
    private final Set<Integer> scannedQuestIds = new LinkedHashSet<>();
    private final Map<Integer, QuestChoice> dailyChoices = new LinkedHashMap<>();
    private List<QuestMenuSource.Selector> selectors = List.of();
    private QuestChoice pendingChoice;
    private boolean offerScanNeeded = true;
    private int giverOpenAttempts;
    private int dailyTabRetries;
    private int giverScrollPage;
    private boolean giverPageHadNewId;
    private int giverRowIndex = 1;
    private int giverRowRetries;
    private int selectedOfferId = -1;
    private String selectedOfferTitle = "";
    private int acceptRetries;
    private final Set<Integer> reviewedOfferIds = new LinkedHashSet<>();
    private final Set<Integer> seenGiverOfferIds = new LinkedHashSet<>();
    private final Set<Integer> acceptedOfferIds = new LinkedHashSet<>();
    private final Map<DailyQuestPolicy.Decision, Integer> skippedOfferCounts =
            new EnumMap<>(DailyQuestPolicy.Decision.class);

    public DailyTaskAPI(PluginAPI api) {
        this.quests = api.requireAPI(QuestAPI.class);
        this.bot = api.requireAPI(BotAPI.class);
        this.gameScreen = api.requireAPI(GameScreenAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.ores = api.requireAPI(OreAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.attack = api.requireAPI(AttackAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.questGui = gameScreen.getGui("quests");
        this.questMenuSource = new QuestMenuSource(questGui);
        this.questGiverSource = new QuestGiverSource(gameScreen);
        this.npcCombat = new DailyNpcCombat(api);
        this.playerCombat = new DailyPlayerCombat(api);
    }

    @Override
    public void setConfig(ConfigSetting<DailyTaskConfig> setting) {
        DailyTaskConfig configured = setting.getValue();
        if (configured != null) settings = configured;
        setting.addListener(updated -> {
            if (updated != null) settings = updated;
        });
    }

    @Override
    public void onTickModule() {
        long now = System.currentTimeMillis();
        if (handleCompletedState(now)) return;
        initializeSession(now);
        if (tickStateMachine(now)) return;
        runDisplayedQuest(now);
    }

    private boolean handleCompletedState(long now) {
        if (state != State.DONE) return false;
        if (nextActionAt <= now) {
            state = State.STARTING;
            return false;
        }
        npcCombat.shutdownPet();
        if (npcCombat.isPetActive() || now < nextRoamAt) {
            status = "ALL DAILY QUESTS COMPLETED | PET is shutting down";
            return true;
        }
        status = "ALL DAILY QUESTS COMPLETED | Completed: " + completedQuestIds.size() +
                " | PET off | Bot stopped";
        if (bot.isRunning()) bot.setRunning(false);
        return true;
    }

    private void initializeSession(long now) {
        if (state != State.STARTING) return;
        resetSession();
        if (quests.isQuestGiverOpen()) {
            questGiverSource.close();
            nextActionAt = now + settings.questSwitchDelayMs;
        }
        state = State.DISCOVERING;
        if (nextActionAt < now) nextActionAt = now;
    }

    private boolean tickStateMachine(long now) {
        if (isGiverState(state)) {
            if (ensureQuestStation() && now >= nextActionAt) tickQuestGiver();
            return true;
        }
        Runnable action = stateAction();
        if (action == null) return state == State.PAUSED;
        if (now >= nextActionAt) action.run();
        return true;
    }

    private Runnable stateAction() {
        if (state == State.DISCOVERING) return this::discoverOrScanNext;
        if (state == State.WAITING_FOR_DISCOVERY) return this::inspectDiscoveredSelector;
        if (state == State.SELECTING) return this::selectPendingDaily;
        if (state == State.WAITING_FOR_SELECTION) return this::inspectPendingSelection;
        return null;
    }

    private void runDisplayedQuest(long now) {
        QuestAPI.Quest quest = quests.getDisplayedQuest();
        if (quest == null) {
            waitForQuestSnapshot();
            return;
        }
        if (handleQuestChange(quest, now) || rejectNonDailyQuest(quest, now) ||
                rejectDisallowedQuest(quest, now)) return;
        double progress = DailyTaskPlanner.progress(quest);
        if (quest.isCompleted() || progress >= 0.9999d) {
            completeQuest(quest, now);
            return;
        }
        buildPlanFromSource(quest);
        if (targetPlayerDescription != null) playerCombat.observeHeroState();
        if (recoverStagnantPlan(quest, progress, now)) return;
        boolean standardNpcCombatOwnsSafety = targetNpcDescription != null &&
                (targetMapName == null || isOnMap(targetMapName));
        if (!standardNpcCombatOwnsSafety && runLocalSafety()) return;
        executePlan(quest, progress);
    }

    private void waitForQuestSnapshot() {
        attack.stopAttack();
        movement.stop(false);
        hero.setRoamMode();
        status = "Waiting for refreshed quest data; current plan retained";
    }

    private boolean handleQuestChange(QuestAPI.Quest quest, long now) {
        if (currentQuestId < 0 || quest.getId() == currentQuestId) return false;
        scheduleQuestMenuScan(now, false);
        status = "Displayed quest changed before completion; safely rescanning the native quest menu";
        return true;
    }

    private boolean rejectNonDailyQuest(QuestAPI.Quest quest, long now) {
        if (isVerifiedDaily(quest)) return false;
        scheduleQuestMenuScan(now, false);
        status = "Displayed quest is not daily; rescanning the native quest menu";
        return true;
    }

    private void completeQuest(QuestAPI.Quest quest, long now) {
        completedQuestIds.add(quest.getId());
        status = "Completed: " + safeTitle(quest);
        scheduleQuestMenuScan(now, true);
    }

    private void scheduleQuestMenuScan(long now, boolean parkFirst) {
        stopCurrentAction();
        currentQuestId = -1;
        currentQuestTitle = parkFirst ? PARK_AFTER_DAILY : "";
        prepareNewScan();
        nextActionAt = now + settings.questSwitchDelayMs;
    }

    private void resetSession() {
        selectionRetries = 0;
        menuScanPass = 0;
        selectorIndex = 0;
        questIdBeforeSelection = -1;
        currentQuestId = -1;
        currentQuestTitle = "";
        pendingChoice = null;
        completedQuestIds.clear();
        knownDailyQuestIds.clear();
        scannedQuestIds.clear();
        dailyChoices.clear();
        selectors = List.of();
        offerScanNeeded = true;
        giverOpenAttempts = 0;
        dailyTabRetries = 0;
        giverScrollPage = 0;
        giverPageHadNewId = false;
        giverRowIndex = 1;
        giverRowRetries = 0;
        selectedOfferId = -1;
        selectedOfferTitle = "";
        acceptRetries = 0;
        reviewedOfferIds.clear();
        seenGiverOfferIds.clear();
        acceptedOfferIds.clear();
        skippedOfferCounts.clear();
        resetProgressTracking();
        clearPlan();
        status = "Scanning the native quest menu";
    }

    private void discoverOrScanNext() {
        if (parkAfterCompletedQuest()) return;
        if (!ensureQuestMenuVisible()) return;
        if (!ensureQuestSelectors()) return;
        scanNextQuestSelector();
    }

    private boolean parkAfterCompletedQuest() {
        if (!PARK_AFTER_DAILY.equals(currentQuestTitle)) return false;
        Optional<? extends Portal> portal = nearestPortal();
        if (portal.isEmpty()) {
            attack.stopAttack();
            status = "Waiting for the nearest portal after quest completion";
            nextActionAt = System.currentTimeMillis() + 1_000L;
            return true;
        }
        double distance = hero.distanceTo(portal.get());
        if (distance > 300d) {
            moveToCompletionPortal(portal.get(), distance);
            return true;
        }
        movement.stop(false);
        hero.setRoamMode();
        currentQuestTitle = "";
        status = "Quest completed; parked safely at the nearest portal";
        return false;
    }

    private Optional<? extends Portal> nearestPortal() {
        return entities.getPortals().stream()
                .filter(Portal::isValid)
                .min(Comparator.comparingDouble(hero::distanceTo));
    }

    private void moveToCompletionPortal(Portal portal, double distance) {
        attack.stopAttack();
        hero.setRunMode();
        movement.moveTo(portal);
        status = "Quest completed; moving to the nearest portal: " + (int) Math.round(distance);
        nextActionAt = System.currentTimeMillis() + 1_000L;
    }

    private boolean ensureQuestMenuVisible() {
        if (questGui == null) {
            pauseSafely("DarkBot quest window is unavailable");
            return false;
        }
        if (questGui.isVisible()) return true;
        if (!settings.autoOpenQuestWindow) {
            status = "Open the quest window";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return false;
        }
        questGui.setVisible(true);
        status = "Opening the quest window";
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
        return false;
    }

    private boolean ensureQuestSelectors() {
        if (!selectors.isEmpty()) return true;
        refreshDailyCatalog();
        selectors = questMenuSource.discoverSelectors();
        if (selectors.isEmpty()) {
            retryMenuScan(questMenuSource.getLastError());
            return false;
        }
        menuScanPass++;
        selectorIndex = 0;
        selectionRetries = 0;
        scannedQuestIds.clear();
        dailyChoices.clear();
        QuestAPI.Quest displayed = quests.getDisplayedQuest();
        recordQuest(displayed, null);
        if (!isRunnableDaily(displayed)) return true;
        startDaily(displayed);
        return false;
    }

    private boolean isRunnableDaily(QuestAPI.Quest quest) {
        return quest != null && dailyChoices.containsKey(quest.getId()) && !isFinished(quest);
    }

    private void scanNextQuestSelector() {
        if (selectorIndex >= selectors.size()) {
            finishMenuScan();
            return;
        }
        QuestMenuSource.Selector selector = selectors.get(selectorIndex);
        QuestAPI.Quest before = quests.getDisplayedQuest();
        questIdBeforeSelection = before == null ? -1 : before.getId();
        questGui.click(selector.x(), selector.y());
        state = State.WAITING_FOR_DISCOVERY;
        status = "Scanning quest menu: " + (selectorIndex + 1) + "/" + selectors.size();
        nextActionAt = System.currentTimeMillis() + 500L;
    }

    private void inspectDiscoveredSelector() {
        QuestAPI.Quest quest = quests.getDisplayedQuest();
        if (quest == null) {
            retryDiscoveredSelector("Waiting for quest data from the server");
            return;
        }

        QuestMenuSource.Selector selector = selectors.get(selectorIndex);
        if (quest.getId() == questIdBeforeSelection) {
            selectorIndex++;
            selectionRetries = 0;
            state = State.DISCOVERING;
            status = "Quest row did not respond; continuing the single scan";
            nextActionAt = System.currentTimeMillis() + 250L;
            return;
        }

        recordQuest(quest, selector);
        if (dailyChoices.containsKey(quest.getId()) && !isFinished(quest)) {
            startDaily(quest);
            return;
        }
        selectorIndex++;
        selectionRetries = 0;
        state = State.DISCOVERING;
        nextActionAt = System.currentTimeMillis() + 250L;
    }

    private void finishMenuScan() {
        QuestAPI.Quest displayed = quests.getDisplayedQuest();
        recordQuest(displayed, selectorIndex > 0 && selectorIndex <= selectors.size()
                ? selectors.get(selectorIndex - 1) : null);

        QuestChoice current = displayed == null ? null : dailyChoices.get(displayed.getId());
        if (current != null && !isFinished(displayed)) {
            startDaily(displayed);
            return;
        }

        pendingChoice = dailyChoices.values().stream()
                .filter(choice -> !completedQuestIds.contains(choice.id()))
                .filter(choice -> choice.selector() != null)
                .min(Comparator.comparingInt(QuestChoice::priority))
                .orElse(null);
        if (pendingChoice != null) {
            selectionRetries = 0;
            state = State.SELECTING;
            nextActionAt = System.currentTimeMillis() + 250L;
            status = scanSummary() + " | Selecting daily: " + pendingChoice.title();
            return;
        }

        if (offerScanNeeded) {
            beginQuestOfferScan();
            return;
        }
        finishAll();
    }

    private boolean isGiverState(State value) {
        return value == State.OPENING_GIVER || value == State.WAITING_GIVER_OPEN ||
                value == State.SELECTING_DAILY_TAB || value == State.WAITING_DAILY_TAB ||
                value == State.SELECTING_GIVER_ROW || value == State.WAITING_GIVER_ROW ||
                value == State.ACCEPTING_GIVER_QUEST || value == State.WAITING_GIVER_ACCEPT ||
                value == State.CLOSING_GIVER;
    }

    private void beginQuestOfferScan() {
        stopCurrentAction();
        if (questGui != null && questGui.isVisible()) questGui.setVisible(false);
        // This set represents offers accepted by the current station scan.
        // Keeping ids from an earlier scan made CLOSING_GIVER believe that new
        // quests had been accepted forever, causing an active-menu scan loop.
        acceptedOfferIds.clear();
        giverOpenAttempts = 0;
        dailyTabRetries = 0;
        giverScrollPage = 0;
        giverPageHadNewId = false;
        giverRowIndex = 1;
        giverRowRetries = 0;
        selectedOfferId = -1;
        selectedOfferTitle = "";
        acceptRetries = 0;
        seenGiverOfferIds.clear();
        state = State.OPENING_GIVER;
        nextActionAt = System.currentTimeMillis() + 500L;
        status = "Scanning station daily offers from source data";
    }

    private void tickQuestGiver() {
        switch (state) {
            case CLOSING_GIVER:
                tickClosingGiver();
                break;
            case OPENING_GIVER:
            case WAITING_GIVER_OPEN:
                tickOpeningGiver();
                break;
            default:
                tickOpenGiverState();
                break;
        }
    }

    private void tickClosingGiver() {
        if (quests.isQuestGiverOpen()) {
            questGiverSource.close();
            status = "Closing quest giver";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        if (acceptedOfferIds.isEmpty()) {
            finishAll();
            return;
        }
        prepareNewScan();
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
        status = "Verifying accepted daily quests in the active quest menu";
    }

    private void tickOpeningGiver() {
        if (quests.isQuestGiverOpen()) {
            giverOpenAttempts = 0;
            state = State.SELECTING_DAILY_TAB;
            nextActionAt = System.currentTimeMillis() + 300L;
            status = "Opening daily quest tab";
            return;
        }
        findQuestStation().filter(station -> hero.distanceTo(station) <= QUEST_STATION_DISTANCE)
                .ifPresent(station -> station.trySelect(false));
        giverOpenAttempts++;
        state = State.WAITING_GIVER_OPEN;
        status = "Waiting for quest giver response (" + giverOpenAttempts + ")";
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void tickOpenGiverState() {
        if (!quests.isQuestGiverOpen()) {
            state = State.OPENING_GIVER;
            nextActionAt = System.currentTimeMillis() + 500L;
            status = "Quest giver closed; reopening";
            return;
        }
        switch (state) {
            case SELECTING_DAILY_TAB:
                selectDailyTab();
                break;
            case WAITING_DAILY_TAB:
                beginGiverRows();
                break;
            case SELECTING_GIVER_ROW:
                selectGiverRow();
                break;
            case WAITING_GIVER_ROW:
                inspectGiverRow();
                break;
            case ACCEPTING_GIVER_QUEST:
                acceptSelectedOffer();
                break;
            case WAITING_GIVER_ACCEPT:
                verifyAcceptedOffer();
                break;
            default:
                break;
        }
    }

    private void selectDailyTab() {
        questGiverSource.selectDailyTab();
        state = State.WAITING_DAILY_TAB;
        status = "Waiting for daily tab source data";
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void beginGiverRows() {
        giverRowIndex = 1;
        giverRowRetries = 0;
        state = State.SELECTING_GIVER_ROW;
        nextActionAt = System.currentTimeMillis() + 250L;
    }

    private void selectGiverRow() {
        if (giverRowIndex > QuestGiverSource.MAX_ROWS) {
            advanceGiverPage();
            return;
        }
        questGiverSource.selectRow(giverRowIndex);
        state = State.WAITING_GIVER_ROW;
        status = "Reading daily offer row " + giverRowIndex;
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void advanceGiverPage() {
        boolean exhausted = giverScrollPage > 0 && !giverPageHadNewId;
        if (exhausted || giverScrollPage >= QuestGiverSource.MAX_SCROLL_PAGES) {
            finishQuestOfferScan();
            return;
        }
        giverScrollPage++;
        giverPageHadNewId = false;
        giverRowIndex = 1;
        giverRowRetries = 0;
        questGiverSource.scrollDailyListDown();
        state = State.WAITING_DAILY_TAB;
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void inspectGiverRow() {
        QuestAPI.QuestListItem info = quests.getSelectedQuestInfo();
        QuestAPI.Quest selected = quests.getSelectedQuest();
        boolean matchingSource = info != null && selected != null && info.getId() == selected.getId();
        if (!matchingSource) {
            retryOrAdvanceGiverRow("Waiting for offer details from the server");
            return;
        }
        if (seenGiverOfferIds.add(info.getId())) giverPageHadNewId = true;
        boolean dailyOffer = DailyTaskPlanner.isDailyType(info.getType()) || DailyTaskPlanner.isDaily(selected);
        if (!dailyOffer) {
            advanceGiverRow();
            return;
        }

        int id = info.getId();
        if (reviewedOfferIds.contains(id) || info.isCompleted() || !info.isActivable()) {
            reviewedOfferIds.add(id);
            advanceGiverRow();
            return;
        }
        DailyQuestPolicy.Decision decision = DailyQuestPolicy.evaluateOffer(selected, settings);
        if (decision == DailyQuestPolicy.Decision.WAIT_FOR_REWARDS) {
            retryOrAdvanceGiverRow("Waiting for offer rewards from the source");
            return;
        }
        if (!decision.accepted()) {
            reviewedOfferIds.add(id);
            skippedOfferCounts.merge(decision, 1, Integer::sum);
            status = "Skipped daily quest: " + decision.message() + " | " + safeTitle(selected);
            advanceGiverRow();
            return;
        }

        selectedOfferId = id;
        selectedOfferTitle = safeTitle(selected);
        acceptRetries = 0;
        state = State.ACCEPTING_GIVER_QUEST;
        nextActionAt = System.currentTimeMillis() + 250L;
        status = "Source data verified; accepting daily: " + selectedOfferTitle;
    }

    private void retryOrAdvanceGiverRow(String reason) {
        giverRowRetries++;
        if (giverRowRetries < Math.max(2, settings.maxSelectionRetries)) {
            state = State.SELECTING_GIVER_ROW;
            status = reason + " (" + giverRowRetries + ")";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        advanceGiverRow();
    }

    private void advanceGiverRow() {
        giverRowIndex++;
        giverRowRetries = 0;
        state = State.SELECTING_GIVER_ROW;
        nextActionAt = System.currentTimeMillis() + 300L;
    }

    private void acceptSelectedOffer() {
        QuestAPI.QuestListItem info = quests.getSelectedQuestInfo();
        QuestAPI.Quest selected = quests.getSelectedQuest();
        if (!isSafeOfferSelection(info, selected)) {
            skipSelectedOffer(info);
            return;
        }
        questGiverSource.acceptSelected();
        state = State.WAITING_GIVER_ACCEPT;
        status = "Waiting for server confirmation: " + selectedOfferTitle;
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private boolean isSafeOfferSelection(QuestAPI.QuestListItem info, QuestAPI.Quest selected) {
        if (info == null || selected == null) return false;
        if (info.getId() != selectedOfferId || selected.getId() != selectedOfferId) return false;
        if (!DailyTaskPlanner.isDailyType(info.getType()) && !DailyTaskPlanner.isDaily(selected)) return false;
        if (!info.isActivable() || info.isCompleted()) return false;
        return DailyQuestPolicy.evaluateOffer(selected, settings).accepted();
    }

    private void skipSelectedOffer(QuestAPI.QuestListItem info) {
        int skippedId = selectedOfferId;
        String skippedTitle = selectedOfferTitle;
        if (skippedId > 0) reviewedOfferIds.add(skippedId);
        selectedOfferId = -1;
        selectedOfferTitle = "";
        if (info != null && info.getId() == skippedId && !info.isActivable()) {
            status = "Offer is no longer activable; skipped: " + skippedTitle;
        } else {
            status = "Offer source changed during verification; skipped safely: " + skippedTitle;
        }
        advanceGiverRow();
    }

    private void verifyAcceptedOffer() {
        QuestAPI.QuestListItem catalog = findQuestItem(selectedOfferId).orElse(null);
        boolean accepted = catalog != null && !catalog.isActivable();
        if (accepted) {
            acceptedOfferIds.add(selectedOfferId);
            reviewedOfferIds.add(selectedOfferId);
            status = "Daily quest accepted: " + selectedOfferTitle;
            selectedOfferId = -1;
            selectedOfferTitle = "";
            giverRowIndex = 1;
            giverRowRetries = 0;
            dailyTabRetries = 0;
            giverPageHadNewId = false;
            state = quests.isQuestGiverOpen() ? State.SELECTING_GIVER_ROW : State.OPENING_GIVER;
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        if (acceptRetries++ < Math.max(2, settings.maxSelectionRetries)) {
            state = State.ACCEPTING_GIVER_QUEST;
            status = "Daily acceptance response delayed; reverifying source";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        pauseSafely("Could not verify daily acceptance: " + selectedOfferTitle);
    }

    private Optional<QuestAPI.QuestListItem> findQuestItem(int id) {
        try {
            return quests.getCurrestQuests().stream()
                    .filter(item -> item != null && item.getId() == id)
                    .findFirst()
                    .map(item -> (QuestAPI.QuestListItem) item);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private void finishQuestOfferScan() {
        offerScanNeeded = false;
        state = State.CLOSING_GIVER;
        status = "Station scan completed | Accepted: " + acceptedOfferIds.size() +
                " | Skipped by policy: " + skippedOfferCounts.values().stream()
                .mapToInt(Integer::intValue).sum();
        nextActionAt = System.currentTimeMillis() + 300L;
    }

    private void selectPendingDaily() {
        if (pendingChoice == null || pendingChoice.selector() == null) {
            prepareNewScan();
            return;
        }
        QuestAPI.Quest displayed = quests.getDisplayedQuest();
        if (displayed != null && displayed.getId() == pendingChoice.id() && isVerifiedDaily(displayed)) {
            startDaily(displayed);
            return;
        }
        questIdBeforeSelection = displayed == null ? -1 : displayed.getId();
        questGui.click(pendingChoice.selector().x(), pendingChoice.selector().y());
        state = State.WAITING_FOR_SELECTION;
        status = "Selecting daily quest from source: " + pendingChoice.title();
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void inspectPendingSelection() {
        QuestAPI.Quest quest = quests.getDisplayedQuest();
        if (quest != null && pendingChoice != null && quest.getId() == pendingChoice.id() && isVerifiedDaily(quest)) {
            startDaily(quest);
            return;
        }

        selectionRetries++;
        if (selectionRetries >= Math.max(1, settings.maxSelectionRetries)) {
            prepareNewScan();
            status = "Daily selection not verified; rescanning quest menu";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        state = State.SELECTING;
        status = "Waiting for daily quest selection (" + selectionRetries + "/" +
                settings.maxSelectionRetries + ")";
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void startDaily(QuestAPI.Quest quest) {
        currentQuestId = quest.getId();
        currentQuestTitle = safeTitle(quest);
        selectionRetries = 0;
        resetProgressTracking();
        playerCombat.startQuest();
        state = State.RUNNING;
        buildPlanFromSource(quest);
        status = "Daily quest verified: " + currentQuestTitle;
    }

    private void retryDiscoveredSelector(String reason) {
        selectionRetries++;
        if (selectionRetries >= Math.max(1, settings.maxSelectionRetries)) {
            selectorIndex++;
            selectionRetries = 0;
            state = State.DISCOVERING;
            status = reason + "; trying the next source selector";
            nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
            return;
        }
        state = State.DISCOVERING;
        status = reason + " (" + selectionRetries + "/" + settings.maxSelectionRetries + ")";
        nextActionAt = System.currentTimeMillis() + settings.questSwitchDelayMs;
    }

    private void retryMenuScan(String reason) {
        status = reason + "; single scan completed";
        if (offerScanNeeded) beginQuestOfferScan();
        else finishAll();
    }

    private void prepareNewScan() {
        state = State.DISCOVERING;
        menuScanPass = 0;
        selectorIndex = 0;
        selectionRetries = 0;
        questIdBeforeSelection = -1;
        pendingChoice = null;
        selectors = List.of();
        scannedQuestIds.clear();
        dailyChoices.clear();
    }

    private void refreshDailyCatalog() {
        try {
            for (QuestAPI.QuestListItem item : quests.getCurrestQuests()) {
                if (item != null && DailyTaskPlanner.isDailyType(item.getType())) {
                    knownDailyQuestIds.add(item.getId());
                }
            }
        } catch (RuntimeException ignored) {
            // The quest-giver catalog can be unavailable when its window has
            // never been opened. The 24-hour requirement remains authoritative.
        }
    }

    private void recordQuest(QuestAPI.Quest quest, QuestMenuSource.Selector selector) {
        if (quest == null || quest.getId() <= 0) return;
        scannedQuestIds.add(quest.getId());
        if (!isVerifiedDaily(quest)) return;
        if (isFinished(quest)) {
            completedQuestIds.add(quest.getId());
            return;
        }
        if (!DailyQuestPolicy.evaluateActive(quest, settings).accepted()) return;
        QuestChoice choice = new QuestChoice(quest.getId(), safeTitle(quest), selector,
                DailyQuestPolicy.priority(quest, settings));
        QuestChoice existing = dailyChoices.get(quest.getId());
        if (existing == null || (existing.selector() == null && selector != null)) {
            dailyChoices.put(quest.getId(), choice);
        }
    }

    private boolean isVerifiedDaily(QuestAPI.Quest quest) {
        return quest != null && (SPECIAL_DAILY_QUEST_IDS.contains(quest.getId()) ||
                acceptedOfferIds.contains(quest.getId()) || knownDailyQuestIds.contains(quest.getId()) ||
                DailyTaskPlanner.isDaily(quest));
    }

    private boolean isFinished(QuestAPI.Quest quest) {
        return quest == null || quest.isCompleted() || DailyTaskPlanner.progress(quest) >= 0.9999d;
    }

    private String scanSummary() {
        return "Menu: " + scannedQuestIds.size() + " quests, " + dailyChoices.size() + " daily";
    }

    private void buildPlanFromSource(QuestAPI.Quest quest) {
        clearPlan();
        DailyQuestConditionEngine.Plan plan = DailyQuestConditionEngine.build(quest, companyPrefix());
        targetMapName = plan.targetMapName();
        targetNpcDescription = plan.targetNpcDescription();
        targetPlayerDescription = plan.targetPlayerDescription();
        oreToSell = plan.oreToSell();
        collectBonus = plan.collectBonus();
        collectCargo = plan.collectCargo();
        targetCoordinates = plan.targetCoordinates();

        if (targetPlayerDescription != null && targetMapName == null &&
                settings.playerCombatMap != null && !settings.playerCombatMap.isBlank()) {
            targetMapName = DailyTaskPlanner.findMap(settings.playerCombatMap).orElse(null);
        }

        if (oreToSell != null && !ores.canSellOres() && targetMapName == null) {
            targetMapName = homeBaseMapName();
        }
    }

    private void executePlan(QuestAPI.Quest quest, double progress) {
        if (travelToTargetMap(quest, progress)) return;
        if (handleTargetCoordinates(quest, progress)) return;
        if (handleOreSale(quest, progress)) return;
        if (collectRequiredBox(quest, progress)) return;
        if (huntRequiredNpc(quest, progress)) return;
        if (fightRequiredPlayer(quest, progress)) return;
        if (searchForRequiredBox(quest, progress)) return;
        if (reportMapArrival(quest, progress)) return;
        pauseSafely("Unsupported daily objective: " + firstRequirement(quest));
    }

    private boolean travelToTargetMap(QuestAPI.Quest quest, double progress) {
        if (targetMapName == null || isOnMap(targetMapName)) return false;
        navigateTo(targetMapName);
        updateProgressStatus(quest, progress, "Travelling to map " + targetMapName);
        return true;
    }

    private boolean handleTargetCoordinates(QuestAPI.Quest quest, double progress) {
        if (targetCoordinates == null) return false;
        if (movement.getClosestDistance(targetCoordinates) > 120d) {
            npcCombat.stopCombat();
            playerCombat.stopCombat();
            attack.stopAttack();
            hero.setRunMode();
            movement.moveTo(targetCoordinates);
            updateProgressStatus(quest, progress, "Travelling to coordinates " +
                    targetCoordinates.x() + "/" + targetCoordinates.y());
            return true;
        }
        if (!isCoordinateOnlyPlan()) return false;
        movement.stop(false);
        hero.setRoamMode();
        updateProgressStatus(quest, progress, "Reached target coordinates");
        return true;
    }

    private boolean isCoordinateOnlyPlan() {
        return targetNpcDescription == null && targetPlayerDescription == null && oreToSell == null &&
                !collectBonus && !collectCargo;
    }

    private boolean handleOreSale(QuestAPI.Quest quest, double progress) {
        if (oreToSell == null) return false;
        if (ores.canSellOres() && ores.getAmount(oreToSell) > 0) {
            ores.sellOre(oreToSell);
            updateProgressStatus(quest, progress, "Selling ore: " + oreToSell.getName());
            return true;
        }
        if (!ores.canSellOres()) {
            String base = homeBaseMapName();
            if (!isOnMap(base)) navigateTo(base);
            updateProgressStatus(quest, progress, "Travelling to base to sell ore");
            return true;
        }
        if (targetNpcDescription == null) {
            targetNpcDescription = "Streuner";
            collectCargo = true;
        }
        return false;
    }

    private boolean collectRequiredBox(QuestAPI.Quest quest, double progress) {
        if ((!collectBonus && !collectCargo) || !tryCollectRequiredBox()) return false;
        String action = collectBonus ? "Collecting bonus box" : "Collecting NPC cargo";
        updateProgressStatus(quest, progress, action);
        return true;
    }

    private boolean huntRequiredNpc(QuestAPI.Quest quest, double progress) {
        if (targetNpcDescription == null) return false;
        npcCombat.tick(targetNpcDescription, settings.attackRadius);
        if (attack.getTarget() == null && !movement.isMoving()) movement.moveRandom();
        updateProgressStatus(quest, progress, "NPC Kill and Collect: " + npcCombat.getStatus() +
                " | Target: " + cleanedRequirement(targetNpcDescription));
        return true;
    }

    private boolean rejectDisallowedQuest(QuestAPI.Quest quest, long now) {
        DailyQuestPolicy.Decision decision = DailyQuestPolicy.evaluateActive(quest, settings);
        if (decision.accepted()) return false;
        scheduleQuestMenuScan(now, false);
        status = "Daily quest skipped by policy: " + decision.message();
        return true;
    }

    private boolean fightRequiredPlayer(QuestAPI.Quest quest, double progress) {
        if (targetPlayerDescription == null) return false;
        if (playerCombat.exceededSafetyLimits(settings)) {
            pauseSafely("Player combat stopped: " + playerCombat.failureReason(settings));
            return true;
        }
        playerCombat.tick(targetPlayerDescription, settings);
        updateProgressStatus(quest, progress, "Player combat: " + playerCombat.getStatus());
        return true;
    }

    private boolean searchForRequiredBox(QuestAPI.Quest quest, double progress) {
        if (!collectBonus && !collectCargo) return false;
        roamIfNeeded();
        updateProgressStatus(quest, progress, "Searching for required box");
        return true;
    }

    private boolean reportMapArrival(QuestAPI.Quest quest, double progress) {
        if (targetMapName == null || !isOnMap(targetMapName)) return false;
        updateProgressStatus(quest, progress, "Reached target map");
        return true;
    }

    private boolean runLocalSafety() {
        double hp = hero.getHealth().hpPercent();
        boolean lowShieldInPlayerCombat = targetPlayerDescription != null &&
                hero.getHealth().shieldPercent() < settings.playerMinimumShieldPercent;
        if (hp >= settings.minimumHpPercent && !lowShieldInPlayerCombat) return false;
        attack.stopAttack();
        hero.setRunMode();
        entities.getPortals().stream()
                .filter(Portal::isValid)
                .min(Comparator.comparingDouble(hero::distanceTo))
                .ifPresent(portal -> movement.moveTo(portal));
        status = "Local safety: HP " + (int) Math.round(hp * 100d) + "% / shield " +
                (int) Math.round(hero.getHealth().shieldPercent() * 100d) + "% ; waiting near portal";
        return true;
    }

    private boolean ensureQuestStation() {
        if (!isOnMap(QUEST_BASE_MAP)) {
            if (!selectors.isEmpty() || state != State.DISCOVERING) prepareNewScan();
            if (questGui != null && questGui.isVisible()) questGui.setVisible(false);
            navigateToQuestBase();
            return false;
        }

        Optional<? extends Station> station = findQuestStation();
        if (station.isEmpty()) {
            attack.stopAttack();
            hero.setRoamMode();
            status = QUEST_BASE_MAP + " ana istasyon verisi bekleniyor";
            return false;
        }

        double distance = hero.distanceTo(station.get());
        if (distance > QUEST_STATION_DISTANCE) {
            attack.stopAttack();
            hero.setRoamMode();
            movement.moveTo(station.get());
            status = "Approaching main quest station on " + QUEST_BASE_MAP + ": " + (int) Math.round(distance);
            return false;
        }

        if (movement.isMoving()) movement.stop(false);
        return true;
    }

    private Optional<? extends Station> findQuestStation() {
        Optional<? extends Station> questGiver = entities.getStations().stream()
                .filter(Station::isValid)
                .filter(station -> station instanceof Station.QuestGiver)
                .min(Comparator.comparingDouble(hero::distanceTo));
        if (questGiver.isPresent()) return questGiver;

        Optional<? extends Station> homeBase = entities.getStations().stream()
                .filter(Station::isValid)
                .filter(station -> station instanceof Station.HomeBase)
                .min(Comparator.comparingDouble(hero::distanceTo));
        if (homeBase.isPresent()) return homeBase;

        return entities.getStations().stream()
                .filter(Station::isValid)
                .min(Comparator.comparingDouble(hero::distanceTo));
    }

    private void navigateToQuestBase() {
        Optional<GameMap> destination = starSystem.findMap(QUEST_BASE_MAP);
        if (destination.isEmpty() || !starSystem.isAccessible(destination.get())) {
            attack.stopAttack();
            status = "Waiting for quest-center map data: " + QUEST_BASE_MAP;
            return;
        }

        Portal portal = starSystem.findNext(destination.get());
        if (portal == null || !portal.isValid()) {
            // Map changes briefly clear the portal collection. This is a
            // normal loading state, so wait for the next tick instead of
            // entering a permanent safe pause.
            attack.stopAttack();
            status = "Loading quest-center route: " + QUEST_BASE_MAP;
            return;
        }

        attack.stopAttack();
        hero.setRoamMode();
        if (hero.distanceTo(portal) > 220d) movement.moveTo(portal);
        else movement.jumpPortal(portal);
        status = "Travelling to " + QUEST_BASE_MAP + " for the quest center";
    }

    private void navigateTo(String mapName) {
        Optional<GameMap> destination = starSystem.findMap(mapName);
        if (destination.isEmpty() || !starSystem.isAccessible(destination.get())) {
            // Map/route data can be temporarily unavailable while a portal jump is
            // loading. Keep the daily plan intact and retry on the next tick.
            attack.stopAttack();
            status = "Loading map route: " + mapName;
            return;
        }
        Portal portal = starSystem.findNext(destination.get());
        if (portal == null || !portal.isValid()) {
            // The portal collection is briefly empty during map transitions. This
            // is not a permanent navigation failure and must not clear the plan.
            attack.stopAttack();
            status = "Sonraki portal bekleniyor: " + mapName;
            return;
        }
        attack.stopAttack();
        hero.setRoamMode();
        if (hero.distanceTo(portal) > 220d) movement.moveTo(portal);
        else movement.jumpPortal(portal);
    }

    private boolean tryCollectRequiredBox() {
        Optional<? extends Box> nearest = entities.getBoxes().stream()
                .filter(Box::isValid)
                .filter(box -> !box.isCollected())
                .filter(this::matchesRequiredBox)
                .min(Comparator.comparingDouble(hero::distanceTo));
        if (nearest.isEmpty()) return false;
        Box box = nearest.get();
        attack.stopAttack();
        hero.setRoamMode();
        if (hero.distanceTo(box) > 180d) movement.moveTo(box);
        else box.tryCollect();
        return true;
    }

    private boolean matchesRequiredBox(Box box) {
        String type = ((box.getTypeName() == null ? "" : box.getTypeName()) + " " +
                (box.getHash() == null ? "" : box.getHash())).toUpperCase(Locale.ROOT);
        if (collectBonus && type.contains("BONUS")) return true;
        return collectCargo && (type.contains("FROM_SHIP") || type.contains("CARGO"));
    }

    private void roamIfNeeded() {
        long now = System.currentTimeMillis();
        if (movement.isMoving() && now < nextRoamAt) return;
        movement.moveRandom();
        nextRoamAt = now + 2500L;
    }

    private boolean isNpcRequirement(QuestAPI.Requirement requirement) {
        return DailyQuestConditionEngine.isNpcCondition(requirement);
    }

    private boolean isOnMap(String mapName) {
        GameMap current = starSystem.getCurrentMap();
        return current != null && current.getName().equalsIgnoreCase(mapName);
    }

    private String homeBaseMapName() {
        GameMap current = starSystem.getCurrentMap();
        String prefix = current == null ? "1" : current.getName().split("-")[0];
        if (!prefix.matches("[1-3]")) prefix = "1";
        return prefix + "-1";
    }

    private String companyPrefix() {
        GameMap current = starSystem.getCurrentMap();
        String prefix = current == null ? "1" : current.getName().split("-")[0];
        return prefix.matches("[1-3]") ? prefix : "1";
    }

    private boolean recoverStagnantPlan(QuestAPI.Quest quest, double progress, long now) {
        if (lastProgressAt == 0L || lastTrackedProgress < 0d || progress > lastTrackedProgress + 0.000001d) {
            lastTrackedProgress = progress;
            lastProgressAt = now;
            return false;
        }
        if (now - lastProgressAt < settings.stagnationTimeoutSeconds * 1_000L) return false;

        lastProgressAt = now;
        npcCombat.stopCombat();
        playerCombat.stopCombat();
        attack.stopAttack();
        attack.setTarget(null);
        if (targetMapName != null && !isOnMap(targetMapName)) {
            navigateTo(targetMapName);
        } else if (!movement.isMoving()) {
            movement.moveRandom();
        }
        updateProgressStatus(quest, progress,
                "Quest Engine recovery: target and route reevaluated");
        return true;
    }

    private void resetProgressTracking() {
        lastTrackedProgress = -1d;
        lastProgressAt = 0L;
    }

    private void updateProgressStatus(QuestAPI.Quest quest, double progress, String action) {
        int percent = (int) Math.round(progress * 100d);
        status = "Daily quest | Completed: " + completedQuestIds.size() +
                " | " + safeTitle(quest) + " | %" + percent + " | " + action;
    }

    private void pauseSafely(String reason) {
        stopCurrentAction();
        state = State.PAUSED;
        status = "Safe pause: " + reason;
    }

    private void finishAll() {
        stopCurrentAction();
        npcCombat.shutdownPet();
        state = State.DONE;
        ZoneId localZone = ZoneId.systemDefault();
        nextActionAt = LocalDate.now(localZone).plusDays(1)
                .atStartOfDay(localZone)
                .toInstant().toEpochMilli();
        // Keep DarkBot ticking briefly so PetManager can send and observe the
        // actual PET deactivation before the bot's running flag is cleared.
        nextRoamAt = System.currentTimeMillis() + 5_000L;
        status = "ALL DAILY QUESTS COMPLETED | Completed: " + completedQuestIds.size() +
                " | PET is shutting down | Bot will stop afterwards";
    }

    private void stopCurrentAction() {
        npcCombat.stopCombat();
        playerCombat.stopCombat();
        attack.stopAttack();
        movement.stop(false);
        hero.setRoamMode();
        clearPlan();
    }

    private void clearPlan() {
        targetNpcDescription = null;
        targetPlayerDescription = null;
        targetMapName = null;
        oreToSell = null;
        collectBonus = false;
        collectCargo = false;
        targetCoordinates = null;
    }

    private String getNpcLocatorTargetDescription() {
        if (state != State.RUNNING || targetNpcDescription == null) return null;
        if (targetMapName != null && !isOnMap(targetMapName)) return null;
        return targetNpcDescription;
    }

    public boolean hasNpcLocatorTarget() {
        return getNpcLocatorTargetDescription() != null;
    }

    public boolean matchesNpcLocatorTarget(String npcName) {
        String description = getNpcLocatorTargetDescription();
        return DailyTaskPlanner.matchesNpcName(description, npcName);
    }

    private String safeTitle(QuestAPI.Quest quest) {
        String title = quest == null ? null : quest.getTitle();
        if (title == null || title.isBlank() || title.equalsIgnoreCase("ERROR")) {
            return quest == null ? "Quest" : "Quest #" + quest.getId();
        }
        return title;
    }

    private String firstRequirement(QuestAPI.Quest quest) {
        return DailyTaskPlanner.actionable(quest).stream()
                .map(QuestAPI.Requirement::getDescription)
                .findFirst()
                .orElse("etkin hedef yok");
    }

    private String cleanedRequirement(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @Override
    public void onTickStopped() {
        // DarkBot also invokes this callback during short disconnects and map-load
        // gaps. Stop active movement/attack, but retain the selected quest and its
        // plan so a slow server does not force another trip to the quest station.
        npcCombat.stopCombat();
        playerCombat.stopCombat();
        attack.stopAttack();
        movement.stop(false);
        hero.setRoamMode();
        if (state != State.PAUSED && state != State.DONE) {
            status = "DarkBot idle; daily quest plan retained";
        }
    }

    @Override
    public String getStatus() {
        return "DailyTaskAPI | " + status;
    }

    @Override
    public String getStoppedStatus() {
        return "DailyTaskAPI stopped | " + status;
    }

    @Override
    public boolean canRefresh() {
        return true;
    }
}
