package dev.shared.alievs.tasks.auto_dispatch;

import com.github.manolo8.darkbot.core.manager.DispatchManager;
import com.github.manolo8.darkbot.core.objects.gui.DispatchPopupRewardGui;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Drives the in-game dispatch window: collects finished retrievers and hires new ones into free slots.
 * <p>
 * DispatchAPI only exposes read access, so the clicks go through DarkBot's own {@link DispatchManager}
 * (openAvailableTab, clickHire, clickCollect, ...). Each step waits for the game UI to react.
 */
@Feature(name = "Auto Dispatch", description = "Collects finished retrievers and hires new ones automatically",
        enabledByDefault = false)
public class AutoDispatch implements Task, Configurable<AutoDispatchConfig> {

    private static final long STEP_DELAY_MS = 1_200;
    private static final long SELECT_DELAY_MS = 500;
    private static final long OPEN_TIMEOUT_MS = 15_000;
    private static final long RETRY_DELAY_MS = 60_000;
    private static final long FINISH_MARGIN_MS = 2_000;
    private static final int MAX_ACTIONS_PER_CYCLE = 20;
    private static final int HIRE_VERIFY_RETRIES = 2;
    // Hire button position measured in a 785x424 dispatch window; scaled to the window's actual size.
    private static final double HIRE_REF_W = 785, HIRE_REF_H = 424, HIRE_BTN_X = 700, HIRE_BTN_Y = 375;

    private enum State { IDLE, OPENING, COLLECT, AFTER_COLLECT, HIRE_TAB, HIRE_SELECT, HIRE_CLICK, AFTER_HIRE, CLOSING }

    private final BotAPI bot;
    private final StarSystemAPI starSystem;
    private final Optional<DispatchManager> dispatch;
    private final DispatchPopupRewardGui rewardsGui;
    private final RetrieverSelector selector;

    private AutoDispatchConfig config;
    private State state = State.IDLE;
    private long stateStart;
    private long waitUntil;
    private long nextCheck;
    private int actions;

    private DispatchAPI.Retriever hiring;
    private int slotsBeforeHire;
    private boolean acceptClicked;
    private int hireChecks;

    public AutoDispatch(PluginAPI api, BotAPI bot, StatsAPI stats) {
        this.bot = bot;
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.selector = new RetrieverSelector(stats);
        this.rewardsGui = api.requireInstance(DispatchPopupRewardGui.class);

        DispatchAPI dispatchApi = api.requireAPI(DispatchAPI.class);
        this.dispatch = dispatchApi instanceof DispatchManager
                ? Optional.of((DispatchManager) dispatchApi) : Optional.empty();
        if (!this.dispatch.isPresent())
            log("Dispatch manager not available in this DarkBot version: " + dispatchApi.getClass().getName());
    }

    @Override
    public void setConfig(ConfigSetting<AutoDispatchConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickTask() {
        if (config == null || !dispatch.isPresent()) return;

        if (!bot.isRunning()) {
            if (state != State.IDLE) abort(dispatch.get());
            return;
        }
        if (System.currentTimeMillis() < waitUntil) return;

        tick(dispatch.get());
    }

    private void tick(DispatchManager dm) {
        switch (state) {
            case IDLE: onIdle(); break;
            case OPENING: onOpening(dm); break;
            case COLLECT: onCollect(dm); break;
            case AFTER_COLLECT: closeRewardPopups(dm); enter(State.COLLECT, STEP_DELAY_MS); break;
            case HIRE_TAB: onHireTab(dm); break;
            case HIRE_SELECT: onHireSelect(dm); break;
            case HIRE_CLICK: onHireClick(dm); break;
            case AFTER_HIRE: onAfterHire(dm); break;
            case CLOSING: onClosing(dm); break;
        }
    }

    private void onIdle() {
        if (System.currentTimeMillis() < nextCheck || (!config.collect && !config.hire)) return;
        if (config.skipInGg && isInGalaxyGate()) return; // don't disturb the UI while running a Galaxy Gate
        actions = 0;
        enter(State.OPENING, 0);
    }

    private void onOpening(DispatchManager dm) {
        if (dm.show(true)) {
            log("Dispatch window opened");
            enter(State.COLLECT, STEP_DELAY_MS);
        } else if (System.currentTimeMillis() - stateStart > OPEN_TIMEOUT_MS) {
            log("Dispatch window did not open, retrying later");
            finish(RETRY_DELAY_MS);
        }
    }

    private void onCollect(DispatchManager dm) {
        int row = config.collect && actions < MAX_ACTIONS_PER_CYCLE ? findFinishedRow(dm) : -1;
        if (row < 0) {
            enter(State.HIRE_TAB, 0);
            return;
        }
        actions++;
        log("Collecting finished retriever (row " + row + ")");
        dm.clickCollect(row);
        enter(State.AFTER_COLLECT, STEP_DELAY_MS * 2);
    }

    private void onHireTab(DispatchManager dm) {
        if (!config.hire || actions >= MAX_ACTIONS_PER_CYCLE || dm.getAvailableSlots() <= 0) {
            enter(State.CLOSING, 0);
            return;
        }
        dm.openAvailableTab();
        enter(State.HIRE_SELECT, STEP_DELAY_MS);
    }

    private void onHireSelect(DispatchManager dm) {
        Optional<DispatchAPI.Retriever> next = selector.select(dm.getAvailableRetrievers(), config);
        if (!next.isPresent()) {
            enter(State.CLOSING, 0);
            return;
        }
        actions++;
        hiring = next.get();
        slotsBeforeHire = dm.getAvailableSlots();
        acceptClicked = false;
        hireChecks = 0;
        dm.clickFirstItem();                  // activate the hire panel in the UI (a memory-only select is not enough)
        dm.overrideSelectedRetriever(hiring); // then point the selection at the retriever we actually want
        enter(State.HIRE_CLICK, SELECT_DELAY_MS);
    }

    private void onHireClick(DispatchManager dm) {
        log("Hiring " + hiring.getName() + " (" + RetrieverSelector.describeCost(hiring) + ")");
        // Re-assert the selection right before the click: the user or another task may have changed it while we waited.
        dm.overrideSelectedRetriever(hiring);
        clickHireButton(dm);
        enter(State.AFTER_HIRE, STEP_DELAY_MS * 2);
    }

    private void onAfterHire(DispatchManager dm) {
        // The hire shows two popups: (1) "really hire?" to accept, then (2) "expedition sent" to close with OK.
        if (!acceptClicked && dm.clickAcceptPopup()) {
            acceptClicked = true;
            waitUntil = System.currentTimeMillis() + STEP_DELAY_MS * 2;
            return;
        }
        if (dm.getAvailableSlots() < slotsBeforeHire) {
            log("Hired " + hiring.getName());
            dm.clickCloseOkPopup();   // close the "expedition sent" popup, otherwise it blocks the next hire
        } else if (hireChecks < HIRE_VERIFY_RETRIES) {
            // The game can be slow to update the slot count; re-check before concluding the hire failed.
            hireChecks++;
            enter(State.AFTER_HIRE, STEP_DELAY_MS * 2);
            return;
        } else {
            log("Hiring " + hiring.getName() + " failed, skipping it for 30 min");
            selector.markFailed(hiring);
            dm.clickCloseOkPopup();
        }
        enter(State.HIRE_TAB, STEP_DELAY_MS);
    }

    private void onClosing(DispatchManager dm) {
        closeRewardPopups(dm);
        dm.show(false);
        log("Dispatch window closed");
        finish(nextCheckDelay(dm));
    }

    private void enter(State next, long delayMs) {
        state = next;
        stateStart = System.currentTimeMillis();
        waitUntil = stateStart + delayMs;
    }

    private void finish(long delayMs) {
        state = State.IDLE;
        nextCheck = System.currentTimeMillis() + delayMs;
    }

    /** Bot stopped mid-cycle: close the window and popups so they don't sit over the game UI, then go idle. */
    private void abort(DispatchManager dm) {
        closeRewardPopups(dm);
        dm.show(false);
        finish(0);
    }

    /**
     * Wake up shortly after a running retriever is due to finish, but never later than the configured interval.
     * A small margin absorbs timer/estimate error. An already-finished but still-listed retriever is retried on a
     * longer delay, and only when collecting is enabled, so the window is never reopened in a tight loop.
     */
    private long nextCheckDelay(DispatchManager dm) {
        long delay = config.checkInterval * 1000L;
        for (DispatchAPI.Retriever retriever : dm.getInProgressRetrievers()) {
            double duration = retriever.getDuration();
            if (duration <= 0) {
                if (config.collect) delay = Math.min(delay, RETRY_DELAY_MS);
                continue;
            }
            delay = Math.min(delay, (long) (duration * 1000) + FINISH_MARGIN_MS);
        }
        return delay;
    }

    /** Row in the "in progress" tab of the first finished retriever, or -1. */
    private static int findFinishedRow(DispatchManager dm) {
        List<DispatchAPI.Retriever> inProgress = new ArrayList<>(dm.getInProgressRetrievers());
        inProgress.sort(Comparator.comparingInt(DispatchAPI.Retriever::getSlotId));
        for (int i = 0; i < inProgress.size(); i++) {
            if (inProgress.get(i).getDuration() <= 0) return i;
        }
        return -1;
    }

    private void closeRewardPopups(DispatchManager dm) {
        int rewards = dm.getRewardLoot().size();
        if (rewards > 0) log("Closing reward popup (" + rewards + " item(s))");
        dm.clickOkRewardsPopup(rewards);
        if (rewardsGui.isVisible()) rewardsGui.show(false);
    }

    /** Clicks the Hire button, scaling its reference position to the dispatch window's current size. */
    private void clickHireButton(DispatchManager dm) {
        int w = dm.width, h = dm.height;
        if (w <= 0 || h <= 0) {
            dm.clickHire(); // window size unknown: fall back to DarkBot's fixed position
            return;
        }
        dm.click((int) Math.round(w * (HIRE_BTN_X / HIRE_REF_W)),
                (int) Math.round(h * (HIRE_BTN_Y / HIRE_REF_H)));
    }

    private boolean isInGalaxyGate() {
        GameMap map = starSystem.getCurrentMap();
        return map != null && map.isGG();
    }

    // Console logging matches the rest of SharedPlugin (see AGENTS.md); silence Sonar's logger rule.
    @SuppressWarnings("java:S106")
    private static void log(String message) {
        System.out.println("[AutoDispatch] " + message);
    }
}
