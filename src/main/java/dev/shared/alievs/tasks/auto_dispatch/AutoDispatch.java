package dev.shared.alievs.tasks.auto_dispatch;

import com.github.manolo8.darkbot.core.manager.DispatchManager;
import com.github.manolo8.darkbot.core.objects.gui.DispatchPopupRewardGui;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.DispatchAPI;
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
    private static final long FINISH_MARGIN_MS = 10_000;
    private static final int MAX_ACTIONS_PER_CYCLE = 20;

    private enum State { IDLE, OPENING, COLLECT, AFTER_COLLECT, HIRE_TAB, HIRE_SELECT, HIRE_CLICK, AFTER_HIRE, CLOSING }

    private final BotAPI bot;
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

    public AutoDispatch(PluginAPI api, BotAPI bot, StatsAPI stats) {
        this.bot = bot;
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
            if (state != State.IDLE) finish(0);
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
        actions = 0;
        enter(State.OPENING, 0);
    }

    private void onOpening(DispatchManager dm) {
        if (dm.show(true)) {
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
        dm.overrideSelectedRetriever(hiring);
        enter(State.HIRE_CLICK, SELECT_DELAY_MS);
    }

    private void onHireClick(DispatchManager dm) {
        log("Hiring " + hiring.getName() + " (" + RetrieverSelector.describeCost(hiring) + ")");
        dm.clickHire();
        enter(State.AFTER_HIRE, STEP_DELAY_MS * 2);
    }

    private void onAfterHire(DispatchManager dm) {
        // Some hires ask for confirmation first: accept once, then check the result
        if (!acceptClicked && dm.clickAcceptPopup()) {
            acceptClicked = true;
            waitUntil = System.currentTimeMillis() + STEP_DELAY_MS * 2;
            return;
        }
        if (dm.getAvailableSlots() < slotsBeforeHire) {
            log("Hired " + hiring.getName());
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

    /** Wake up when the next retriever finishes, but never later than the configured interval. */
    private long nextCheckDelay(DispatchManager dm) {
        long delay = config.checkInterval * 1000L;
        for (DispatchAPI.Retriever retriever : dm.getInProgressRetrievers()) {
            if (retriever.getDuration() > 0)
                delay = Math.min(delay, (long) (retriever.getDuration() * 1000) + FINISH_MARGIN_MS);
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
        dm.clickOkRewardsPopup(dm.getRewardLoot().size());
        if (rewardsGui.isVisible()) rewardsGui.show(false);
    }

    // Console logging matches the rest of SharedPlugin (see AGENTS.md); silence Sonar's logger rule.
    @SuppressWarnings("java:S106")
    private static void log(String message) {
        System.out.println("[AutoDispatch] " + message);
    }
}
