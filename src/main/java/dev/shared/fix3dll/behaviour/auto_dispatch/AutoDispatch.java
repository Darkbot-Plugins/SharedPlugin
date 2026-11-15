package dev.shared.fix3dll.behaviour.auto_dispatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import dev.shared.fix3dll.behaviour.auto_dispatch.config.AutoDispatchConfig;
import dev.shared.utils.CaptchaBoxDetector;
import dev.shared.utils.TemporalModuleDetector;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.DispatchAPI.Retriever;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.util.Timer;

/**
 * Automates the in-game dispatch window: collects finished retrievers and keeps the retrievers
 * chosen in the settings running.
 *
 * <h2>How it works</h2>
 * <ul>
 *   <li><b>Pool model.</b> The dispatch slots are treated as an unordered pool. Whatever the user
 *       chose and is not running is hired into any free slot; slots have no identity.</li>
 *   <li><b>Cycles.</b> Work happens in short cycles driven by a state machine
 *       ({@link DispatchState}): open the window, collect, hire, close. A cycle performs at most
 *       one step per tick, with a short pause between steps.</li>
 *   <li><b>Verification.</b> Every click is followed by a check of the game state, and the next
 *       step only runs once the game has reacted. A click that had no effect is retried a limited
 *       number of times and never repeated blindly.</li>
 *   <li><b>Scheduling.</b> Each cycle reads the finishing times while the in-progress list is on
 *       screen. Between cycles the feature sleeps until the first retriever is due, so the
 *       window is not opened when there is nothing to do. The one exception is the first check
 *       after the bot was started, which opens the window to read those times.</li>
 * </ul>
 *
 * <h2>Safeguards</h2>
 * <ul>
 *   <li>Hire is only clicked while the game has the intended retriever selected.</li>
 *   <li>Credits and uridium are checked against the configured reserves before hiring.</li>
 *   <li>A collect or hire that keeps failing is given up after a few attempts and left alone
 *       for a while.</li>
 *   <li>A cycle that makes no progress is abandoned and the window is closed.</li>
 *   <li>Nothing is started while a captcha is present, the hero is not ready, or the ship is
 *       travelling between maps.</li>
 * </ul>
 *
 * <p>Runs as a {@link Behavior}, i.e. on the bot's main tick and only while the bot is running,
 * because it reads game data owned by that thread and clicks inside the game client.
 */
@Feature(name = "Auto Dispatch", description = "Collects finished retrievers and keeps the chosen ones running.")
public final class AutoDispatch implements Behavior, Configurable<AutoDispatchConfig> {
    private static final String LOG_PREFIX = "AutoDispatch: ";
    private static final long FIRST_CHECK_MS = 5_000L;
    /**
     * A cycle that makes no progress for this long is abandoned: the window is closed and the
     * next check is scheduled as usual. Progress means a retriever was collected or hired, or
     * an attempt at either was concluded as failed; the attempts are limited, so the cycle
     * still ends.
     */
    private static final long STALL_TIMEOUT_MS = 60_000L;
    private static final long SETUP_CHECK_MS = 1_000L;
    /**
     * Pause between two steps of a cycle while everything works. Each failed attempt at the
     * current collect or hire adds the same amount again, so a game that reacts slowly is
     * given more time instead of being clicked faster than it can follow.
     */
    private static final long STEP_DELAY_MS = 1_000L;
    /** A click counts as failed if the game has not reacted after this many step delays. */
    private static final int VERIFY_STEPS = 3;
    private static final long MINUTE_MS = 60_000L;
    private static final long RETRY_PAUSE_MS = 60_000L;
    /** Attempts at one collect or one hire before it is given up for this cycle. */
    private static final int MAX_ATTEMPTS = 3;
    /** Steps to wait for the game to register a row click before going on regardless. */
    private static final int ROW_CLICK_WAITS = 3;
    /** Rows of the offer list that are fully visible and can be clicked. */
    private static final int VISIBLE_ROWS = 6;
    /** Upper bound of scroll rounds spent on bringing one retriever into view. */
    private static final int MAX_SCROLLS = 6;
    /** Attempts at closing the window after a cycle was cut short before giving up. */
    private static final int MAX_CLOSE_TRIES = 10;
    private static final int REMEMBERED_HIRES = 5;

    private final BotAPI bot;
    private final HeroAPI hero;
    private final EntitiesAPI entities;
    private final StatsAPI stats;
    private final DispatchAPI dispatch;
    private final DispatchWindow window;
    private final WindowGuard guard;
    private final HirePlanner planner = new HirePlanner();
    private final Deque<String> recentHires = new ArrayDeque<>();
    private final DispatchSchedule schedule;

    private final Timer nextCycle = Timer.get();
    private final Timer cycleTimeout = Timer.get();
    private final Timer stepDelay = Timer.get();
    private final Timer verify = Timer.get();
    private final Timer setupCheck = Timer.get();

    private AutoDispatchConfig config;
    private DispatchState state = DispatchState.IDLE;
    private Retriever pending;
    private String pendingName = "";
    private int pendingId;
    private boolean rewardPopupExpected;
    private String lastSetup = "";
    private String lastBlocker;
    private int expectedCount;
    private int skippedRows;
    /** Failed attempts at the collect currently being tried. */
    private int collectAttempts;
    /** Failed attempts at the hire currently being tried. */
    private int hireAttempts;
    private int rowClickWaits;
    /** Scroll rounds spent so far on bringing the wanted retriever into view. */
    private int scrolls;
    /** Id the game had selected before the last row click, to tell when the click registered. */
    private int idBeforeClick;
    /** List position of the first visible row at the previous look, to tell if a scroll moved the list. */
    private int lastTop;
    /** Notches sent by the previous scroll, to learn how far a notch moves the list. */
    private int lastNotches;
    /** Rows the list moves per wheel notch, as observed; one until a scroll has been measured. */
    private int rowsPerNotch = 1;
    private int collected;
    private int hired;
    /** Set when the window still has to be closed although no cycle is running. */
    private boolean closePending;
    private int closeTries;
    private boolean wasRunning;

    public AutoDispatch(final PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
        this.dispatch = api.requireAPI(DispatchAPI.class);
        this.window = new DispatchWindow(api);
        this.guard = new WindowGuard(api.requireAPI(GameScreenAPI.class));
        this.schedule = api.requireInstance(DispatchSchedule.class);
        this.nextCycle.activate(FIRST_CHECK_MS);
    }

    @Override
    public void setConfig(final ConfigSetting<AutoDispatchConfig> config) {
        this.config = config.getValue();
    }

    /**
     * Runs on the bot's main tick while the bot is running and the ship is ready.
     */
    @Override
    public void onTickBehavior() {
        this.finishClosing();
        if (this.config == null || !this.config.enabled) {
            this.stopQuietly();
            return;
        }
        if (!this.wasRunning) {
            this.wasRunning = true;
            this.nextCycle.activate(FIRST_CHECK_MS);
        }
        if (this.state == DispatchState.IDLE) {
            if (!this.closePending) {
                this.tickIdle();
            }
        } else if (this.isUnsafe()) {
            this.finish("hero invalid or captcha present");
        } else if (this.cycleTimeout.isInactive()) {
            this.leaveWindow();
            this.finish("no progress for too long in " + this.state);
        } else if (this.stepDelay.isInactive()) {
            this.stepDelay.activate(this.currentStepDelay());
            this.step();
        }
    }

    /**
     * Runs instead of {@link #onTickBehavior()} while the bot is stopped or the game is not ready.
     * Only a stop by the user ends a running cycle. A game that is not ready for a moment just
     * makes the cycle wait; if that lasts, the stall timeout ends it once ticks resume.
     */
    @Override
    public void onStoppedBehavior() {
        this.finishClosing();
        if (this.config == null || !this.config.enabled) {
            this.stopQuietly();
        } else if (!this.bot.isRunning()) {
            this.wasRunning = false;
            if (this.state != DispatchState.IDLE) {
                this.leaveWindow();
                this.finish("bot was stopped");
            }
        }
    }

    /**
     * Starts a cycle when a check is due or the setup changed, provided nothing blocks it.
     *
     * <p>Starting the bot makes a check due a few seconds later (see {@link #onTickBehavior()}),
     * so work that came up while the bot was stopped, such as a newly bought slot, is picked up
     * without restarting the bot.
     */
    private void tickIdle() {
        if ((this.nextCycle.isInactive() || this.setupChanged()) && this.canStart()) {
            this.begin();
        }
    }

    /**
     * Switched off in the middle of a cycle: leave nothing open behind.
     */
    private void stopQuietly() {
        if (this.state != DispatchState.IDLE) {
            this.leaveWindow();
            this.state = DispatchState.IDLE;
        }
    }

    /**
     * Starts closing the dispatch window outside of a cycle.
     *
     * <p>A single close request is not enough: the game ignores it while an animation is
     * playing, and an open dialog has to go first. The request is therefore repeated by
     * {@link #finishClosing()} on the following ticks until the window is gone.
     */
    private void leaveWindow() {
        this.closePending = true;
        this.closeTries = 0;
    }

    /**
     * Continues closing the window after a cycle was cut short. Runs on stopped ticks as well,
     * because the bot itself only tidies up game windows while it is running.
     */
    private void finishClosing() {
        if (!this.closePending || this.state != DispatchState.IDLE || this.stepDelay.isActive()) {
            return;
        }
        this.stepDelay.activate(STEP_DELAY_MS);
        if (++this.closeTries > MAX_CLOSE_TRIES) {
            this.closePending = false;
            this.log("could not close the dispatch window");
        } else if (!this.closeOpenDialog()) {
            this.closePending = !this.window.close();
        }
    }

    /**
     * Tells whether opening the window would achieve anything.
     *
     * <p>That is the case when a retriever should be finished by now or a free slot can be
     * filled. It is also the case when the recorded finishing times do not cover the occupied
     * slots: after the bot was started, or when retrievers were hired or collected by hand. The
     * finishing times can only be read from the in-progress list, so the cycle starts to look.
     */
    private boolean hasWorkNow() {
        int owned = this.dispatch.getTotalSlots();
        int free = this.dispatch.getAvailableSlots();
        if (owned <= 0 || this.schedule.recordedCount() != owned - free) {
            return true;
        }
        return this.config.collect && this.schedule.hasDue() || free > 0 && this.pickNext().isPresent();
    }

    /**
     * Checked for the whole cycle; once it is true nothing more is clicked.
     */
    private boolean isUnsafe() {
        return !this.hero.isValid() || CaptchaBoxDetector.hasCaptchaBoxes(this.entities);
    }

    private boolean canStart() {
        String blocker = this.blocker();
        if (blocker != null && !blocker.equals(this.lastBlocker)) {
            this.log("waiting: " + blocker);
        }
        this.lastBlocker = blocker;
        return blocker == null;
    }

    /**
     * Names the condition that keeps a cycle from starting.
     *
     * <p>The conditions are re-evaluated on every tick while a check is due, so a cycle starts
     * as soon as the last of them clears; it is not postponed to a later interval.
     *
     * @return a short description for the log, or {@code null} if a cycle may start
     */
    private String blocker() {
        if (this.isUnsafe()) {
            return "hero not ready or captcha present";
        } else if (TemporalModuleDetector.using(this.bot).isMapModule()) {
            return "travelling between maps";
        } else if (this.config.onlyOutsideGg && this.hero.getMap().isGG()) {
            return "inside a galaxy gate";
        } else if (this.guard.isBusy()) {
            return "another window is in use";
        }
        return null;
    }

    /**
     * Owned slots and the wanted pool; a change is acted on without waiting for the timer.
     */
    private String setup() {
        int owned = this.dispatch.getTotalSlots();
        return owned + "|" + this.config.targets();
    }

    /**
     * Looked at once a second at most; building the comparison text on every tick would only produce garbage.
     */
    private boolean setupChanged() {
        if (this.setupCheck.isActive()) {
            return false;
        }
        this.setupCheck.activate(SETUP_CHECK_MS);
        return this.dispatch.getTotalSlots() > 0 && !this.lastSetup.equals(this.setup());
    }

    private void begin() {
        this.lastSetup = this.setup();
        if (!this.hasWorkNow()) {
            if (this.nextCycle.isInactive()) {
                this.scheduleNext();
            }
            return;
        }
        this.pending = null;
        this.hireAttempts = 0;
        this.rewardPopupExpected = false;
        this.skippedRows = 0;
        this.collectAttempts = 0;
        this.collected = 0;
        this.hired = 0;
        this.cycleTimeout.activate(STALL_TIMEOUT_MS);
        this.log("cycle started - running " + this.dispatch.getInProgressRetrievers().size()
                + ", free slots " + this.dispatch.getAvailableSlots() + "/" + this.dispatch.getTotalSlots());
        this.state = DispatchState.OPENING;
    }

    /**
     * Performs one step of the running cycle.
     *
     * <p>Two things are checked before any step, whatever the state: nothing may have opened on
     * top of the dispatch window, and no dialog may be left open from an earlier step. Handling
     * both here keeps the individual steps free of that concern.
     */
    private void step() {
        String cover = this.state == DispatchState.OPENING ? null : this.guard.coveredBy();
        if (cover != null) {
            this.leaveWindow();
            this.finish(cover + " opened on top");
            this.retrySoon();
            return;
        }
        if (this.closeOpenDialog()) {
            return;
        }
        switch (this.state) {
            case OPENING: this.handleOpening(); break;
            case SHOWING_RETRIEVERS: this.handleShowingRetrievers(); break;
            case SHOWING_PROGRESS: this.handleShowingProgress(); break;
            case COLLECTING: this.handleCollecting(); break;
            case AWAIT_COLLECT: this.handleAwaitCollect(); break;
            case SELECTING: this.handleSelecting(); break;
            case PRIMING: this.handlePriming(); break;
            case TARGETING: this.handleTargeting(); break;
            case HIRING: this.handleHiring(); break;
            case CONFIRMING: this.handleConfirming(); break;
            case AWAIT_HIRE: this.handleAwaitHire(); break;
            case REFRESHING: this.handleRefreshing(); break;
            case READING: this.handleReading(); break;
            case CLOSING: this.handleClosing(); break;
            default: this.finish("unexpected state");
        }
    }

    private void handleOpening() {
        if (this.window.open()) {
            this.guard.markOpened();
            this.state = DispatchState.SHOWING_RETRIEVERS;
        }
    }

    private void handleShowingRetrievers() {
        if (this.window.showRetrievers()) {
            this.state = DispatchState.SHOWING_PROGRESS;
        }
    }

    /**
     * The tab gets its own step: a collect click sent right after switching tabs lands
     * before the list is drawn and does nothing.
     */
    private void handleShowingProgress() {
        if (this.window.showInProgress()) {
            this.state = DispatchState.COLLECTING;
        }
    }

    /**
     * Collects the next finished retriever. Once none is left, the finishing times of the
     * remaining ones are recorded, as the list is on screen and its values are current.
     */
    private void handleCollecting() {
        List<? extends Retriever> inProgress = this.dispatch.getInProgressRetrievers();
        int row = this.config.collect ? this.nextFinishedRow(inProgress) : -1;
        if (row < 0) {
            this.schedule.record(inProgress);
            this.log("nothing left to collect, " + this.dispatch.getAvailableSlots() + " free slots, wanted "
                    + this.config.targets());
            this.state = DispatchState.SELECTING;
        } else if (this.window.clickCollect(row)) {
            this.log("collecting " + inProgress.get(row).getName() + " in row " + (row + 1));
            this.expectedCount = inProgress.size();
            this.rewardPopupExpected = true;
            this.await(DispatchState.AWAIT_COLLECT);
        }
    }

    private void handleAwaitCollect() {
        if (this.dispatch.getInProgressRetrievers().size() < this.expectedCount) {
            this.collected++;
            this.collectAttempts = 0;
            this.cycleTimeout.activate(STALL_TIMEOUT_MS);
            this.state = DispatchState.COLLECTING;
        } else if (this.verify.isInactive()) {
            this.retryOrSkipRow();
            this.state = DispatchState.COLLECTING;
        }
    }

    private void handleSelecting() {
        Optional<Retriever> next = this.canHireMore() ? this.pickNext() : Optional.empty();
        if (next.isEmpty()) {
            this.logWhyNotHiring();
            this.state = this.hired > 0 ? DispatchState.REFRESHING : DispatchState.CLOSING;
            return;
        }
        if (this.pending != next.get() || !this.pendingName.equals(next.get().getName())) {
            this.hireAttempts = 0;
        }
        this.pending = next.get();
        this.pendingName = this.pending.getName();
        this.pendingId = this.pending.getId();
        this.scrolls = 0;
        this.lastTop = -1;
        this.lastNotches = 0;
        if (this.window.showAvailable()) {
            this.log("hiring " + this.pendingName + " (offer " + (this.indexOfOffer(this.pendingId) + 1)
                    + " of " + this.dispatch.getAvailableRetrievers().size() + ")");
            this.state = DispatchState.PRIMING;
        }
    }

    private void logWhyNotHiring() {
        if (!this.canHireMore()) {
            return;
        }
        this.log(this.occupiedNames().isPresent()
                ? "free slots left, but nothing wanted can be hired right now"
                : "running list incomplete, not hiring on a guess");
    }

    /**
     * Clicks the first visible row of the offer list. This enables the hire button and, since
     * the game then reports that row as selected, tells which part of the list is in view.
     */
    private void handlePriming() {
        this.idBeforeClick = idOf(this.dispatch.getSelectedRetriever());
        if (this.window.selectFirstRow()) {
            this.rowClickWaits = 0;
            this.state = DispatchState.TARGETING;
        }
    }

    /**
     * Brings the wanted retriever into view and selects it by clicking its row.
     *
     * <p>The position of the list is not known up front, so it is measured: the retriever the
     * game selected after the click on the first row is the one the list currently starts
     * with. If the wanted one is among the visible rows it is clicked; otherwise the list is
     * scrolled (see {@link #scrollTowards(int, int)}) and measured again.
     *
     * <p>If scrolling is not possible or has no effect, {@link #scrollTowards(int, int)} decides
     * what happens. Either way {@link #handleHiring()} verifies the selection before hiring.
     */
    private void handleTargeting() {
        if (this.hasPendingChanged()) {
            this.retryHire("offer list changed");
            return;
        }
        int selectedId = idOf(this.dispatch.getSelectedRetriever());
        if ((selectedId < 0 || selectedId == this.idBeforeClick) && ++this.rowClickWaits < ROW_CLICK_WAITS) {
            return;
        }
        int top = this.indexOfOffer(selectedId);
        int offset = top < 0 ? -1 : this.indexOfOffer(this.pendingId) - top;
        if (top >= 0 && offset == 0) {
            this.state = DispatchState.HIRING;
        } else if (top >= 0 && offset > 0 && offset < VISIBLE_ROWS) {
            if (this.window.clickOfferRow(offset)) {
                this.log(this.pendingName + " is in view, clicked row " + (offset + 1));
                this.state = DispatchState.HIRING;
            }
        } else {
            this.scrollTowards(top, offset);
        }
    }

    /**
     * Scrolls the offer list towards the wanted retriever and measures again.
     *
     * <p>When scrolling does not get anywhere, the selection is written to the game directly
     * if the user allows it ({@code directSelect}); otherwise the hire counts as failed. The
     * setting exists because selecting a row that is not on screen may crash the game client.
     *
     * <p>All notches needed are sent in one go instead of one per step. How many rows a notch
     * moves the list is learned from the previous scroll, starting from the assumption of one
     * row; since the position is measured again afterwards, a wrong estimate only costs one
     * more round and never a wrong selection.
     *
     * @param top    list position of the first visible row, or negative if unknown
     * @param offset rows between the first visible row and the wanted retriever; positive if it
     *               is further down
     */
    private void scrollTowards(final int top, final int offset) {
        boolean moved = top >= 0 && top != this.lastTop;
        if (moved && this.lastTop >= 0 && this.lastNotches > 0) {
            this.rowsPerNotch = Math.max(1, Math.abs(top - this.lastTop) / this.lastNotches);
        }
        boolean stuck = !moved || this.scrolls >= MAX_SCROLLS;
        this.lastTop = top;
        int rows = offset > 0 ? offset - (VISIBLE_ROWS - 1) : -offset;
        int notches = Math.max(1, (rows + this.rowsPerNotch - 1) / this.rowsPerNotch);
        if (!stuck && this.window.scrollOffers(offset > 0, notches)) {
            this.scrolls++;
            this.lastNotches = notches;
            this.log("scrolling " + (offset > 0 ? "down" : "up") + " by " + notches + " from offer " + (top + 1));
            this.state = DispatchState.PRIMING;
            return;
        }
        String where = "list starts at offer " + (top + 1) + " after " + this.scrolls + " scrolls";
        if (!this.config.directSelect) {
            this.retryHire("could not scroll to it, " + where + "; direct selection is turned off");
            return;
        }
        this.log("could not scroll to " + this.pendingName + " (" + where + "), selecting it directly");
        this.dispatch.overrideSelectedRetriever(this.pending);
        this.state = DispatchState.HIRING;
    }

    /**
     * The game's list entries are reused objects; this tells whether the one picked
     * earlier has turned into a different retriever, in which case nothing is spent on it.
     */
    private boolean hasPendingChanged() {
        return this.pending.getId() != this.pendingId || !this.pendingName.equals(this.pending.getName());
    }

    /**
     * Clicks hire, but only if the game has exactly the wanted retriever selected.
     */
    private void handleHiring() {
        Retriever selected = this.dispatch.getSelectedRetriever();
        if (this.hasPendingChanged()) {
            this.retryHire("offer list changed");
        } else if (idOf(selected) != this.pendingId) {
            this.retryHire("game has " + describe(selected) + " selected instead of id " + this.pendingId);
        } else if (this.window.clickHire()) {
            this.log(this.pendingName + " is selected, clicked hire");
            this.expectedCount = this.dispatch.getAvailableSlots();
            this.rewardPopupExpected = false;
            this.await(DispatchState.CONFIRMING);
        }
    }

    private static String describe(final Retriever retriever) {
        return retriever == null ? "nothing" : retriever.getName() + " (id " + retriever.getId() + ")";
    }

    /**
     * @return the retriever's id, or a negative value if there is none
     */
    private static int idOf(final Retriever retriever) {
        return retriever == null ? -1 : retriever.getId();
    }

    /**
     * @return the zero-based position of a retriever in the game's offer list, or -1 if it is not offered
     */
    private int indexOfOffer(final int retrieverId) {
        List<? extends Retriever> offers = this.dispatch.getAvailableRetrievers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getId() == retrieverId) {
                return i;
            }
        }
        return -1;
    }

    private void handleConfirming() {
        if (this.window.hasConfirmPopup()) {
            if (this.window.confirm()) {
                this.log("confirmed the hire of " + this.pendingName);
                this.await(DispatchState.AWAIT_HIRE);
            }
        } else {
            this.handleAwaitHire();
        }
    }

    private void handleAwaitHire() {
        if (this.dispatch.getAvailableSlots() < this.expectedCount) {
            this.hired++;
            this.hireAttempts = 0;
            this.rememberHire(this.pendingName);
            this.log("hired " + this.pendingName + ", " + this.dispatch.getAvailableSlots() + " free slots left");
            this.cycleTimeout.activate(STALL_TIMEOUT_MS);
            this.state = DispatchState.SELECTING;
        } else if (this.verify.isInactive()) {
            this.retryHire("no slot was taken");
        }
    }

    /**
     * The game only adds fresh hires to its running list when the in-progress tab is drawn, so
     * the tab is shown once more after hiring.
     */
    private void handleRefreshing() {
        if (this.window.showInProgress()) {
            this.state = DispatchState.READING;
        }
    }

    /**
     * Records the finishing times again, now including the retrievers hired in this cycle.
     */
    private void handleReading() {
        this.schedule.record(this.dispatch.getInProgressRetrievers());
        this.state = DispatchState.CLOSING;
    }

    private void handleClosing() {
        if (this.window.close()) {
            this.finish("done");
        }
    }

    /**
     * Handles a collect click the game did not react to: the same row is tried again with a
     * longer pause, and left alone for this cycle once the attempts are used up.
     */
    private void retryOrSkipRow() {
        this.collectAttempts++;
        this.cycleTimeout.activate(STALL_TIMEOUT_MS);
        if (this.collectAttempts < MAX_ATTEMPTS) {
            this.log("collect had no effect, attempt " + this.collectAttempts + " of " + MAX_ATTEMPTS);
            return;
        }
        this.collectAttempts = 0;
        this.skippedRows++;
        this.log("collect had no effect " + MAX_ATTEMPTS + " times, leaving that row for the next check");
    }

    /**
     * Closes a dialog of the dispatch window that is open when it should not be.
     *
     * <p>A result note (rewards, confirmation or error) is clicked away; the position of its
     * button depends on how many rewards it lists, which only matters after collecting. A hire
     * confirmation is expected while a hire is being confirmed and until the game has reacted
     * to the confirmation; found at any other time it is a leftover and is declined.
     *
     * @return {@code true} if a dialog was handled, in which case the step is skipped this tick
     */
    private boolean closeOpenDialog() {
        if (this.window.hasResultPopup()) {
            this.window.dismissResult(this.rewardPopupExpected ? this.dispatch.getRewardLoot().size() : 0);
            return true;
        }
        boolean confirming = this.state == DispatchState.CONFIRMING || this.state == DispatchState.AWAIT_HIRE;
        if (this.window.hasConfirmPopup() && !confirming) {
            this.window.declineConfirm();
            return true;
        }
        return false;
    }

    /**
     * Rows follow the in-progress list order; a finished retriever has no time left.
     */
    private int nextFinishedRow(final List<? extends Retriever> inProgress) {
        for (int row = this.skippedRows; row < inProgress.size(); row++) {
            if (inProgress.get(row).getDuration() <= 0) {
                return row;
            }
        }
        return -1;
    }

    private boolean canHireMore() {
        return this.dispatch.getAvailableSlots() > 0;
    }

    private void rememberHire(final String name) {
        this.recentHires.addLast(name);
        while (this.recentHires.size() > REMEMBERED_HIRES) {
            this.recentHires.removeFirst();
        }
    }

    /**
     * Names of everything occupying a slot. The game's running list can lag behind a fresh hire,
     * so slots that are taken but not listed yet are filled in from the latest hires made here.
     * Empty when the gap cannot be explained; nothing is hired on a guess.
     */
    private Optional<List<String>> occupiedNames() {
        List<String> names = new ArrayList<>();
        for (Retriever retriever : this.dispatch.getInProgressRetrievers()) {
            names.add(retriever.getName());
        }
        int unlisted = this.dispatch.getTotalSlots() - this.dispatch.getAvailableSlots() - names.size();
        if (unlisted <= 0) {
            this.recentHires.clear();
            return Optional.of(names);
        }
        if (unlisted > this.recentHires.size()) {
            return Optional.empty();
        }
        Iterator<String> latest = this.recentHires.descendingIterator();
        for (int i = 0; i < unlisted; i++) {
            names.add(latest.next());
        }
        return Optional.of(names);
    }

    private Optional<Retriever> pickNext() {
        return this.occupiedNames().flatMap(occupied -> this.planner.next(
                this.config.targets(),
                occupied,
                this.dispatch.getAvailableRetrievers(),
                this.stats.getTotalCredits() - this.config.minCredits,
                this.stats.getTotalUridium() - this.config.minUridium));
    }

    private void await(final DispatchState next) {
        this.verify.activate(VERIFY_STEPS * this.currentStepDelay());
        this.state = next;
    }

    /**
     * @return the pause between steps: the base delay, plus the same again for every failed
     *         attempt at the collect or hire currently being tried
     */
    private long currentStepDelay() {
        return STEP_DELAY_MS * (1 + Math.max(this.collectAttempts, this.hireAttempts));
    }

    /**
     * Handles a hire that did not go through, whatever the reason.
     *
     * <p>The hire is started over, with a longer pause between steps on every attempt. Once
     * the attempts are used up the retriever is left alone for the fallback interval and the
     * cycle goes on with whatever else is wanted, so one stubborn retriever does not keep the
     * other slots empty.
     *
     * @param reason what went wrong, for the log
     */
    private void retryHire(final String reason) {
        if (this.window.hasConfirmPopup()) {
            this.window.declineConfirm();
        }
        this.hireAttempts++;
        this.cycleTimeout.activate(STALL_TIMEOUT_MS);
        if (this.hireAttempts < MAX_ATTEMPTS) {
            this.log("hire of " + this.pendingName + " failed (" + reason + "), attempt "
                    + this.hireAttempts + " of " + MAX_ATTEMPTS);
        } else {
            this.hireAttempts = 0;
            this.planner.pause(this.pendingName, this.config.fallbackInterval * MINUTE_MS);
            this.log("hire of " + this.pendingName + " failed (" + reason + ") " + MAX_ATTEMPTS
                    + " times, leaving it for " + this.config.fallbackInterval + " min");
        }
        this.state = DispatchState.SELECTING;
    }

    private void finish(final String reason) {
        this.log(reason + " - this cycle: collected " + this.collected + ", hired " + this.hired
                + " | now running " + this.dispatch.getInProgressRetrievers().size()
                + ", free slots " + this.dispatch.getAvailableSlots());
        this.state = DispatchState.IDLE;
        this.scheduleNext();
    }

    /**
     * Arms the timer for the next cycle. The timing itself is decided by
     * {@link DispatchSchedule#scheduleNextCheck}; this only supplies what the decision depends on.
     */
    private void scheduleNext() {
        long delay = this.schedule.scheduleNextCheck(
                this.config.fallbackInterval * MINUTE_MS,
                this.hasUnfilledSlot(),
                this.planner.msUntilRetry());
        this.nextCycle.activate(delay);
        this.lastSetup = this.setup();
        this.log("next check in " + delay / MINUTE_MS + " min");
    }

    /**
     * Overrides the scheduled time of the next cycle with a short pause, for a cycle that had
     * to be cut short and should simply be tried again.
     */
    private void retrySoon() {
        this.nextCycle.activate(RETRY_PAUSE_MS);
        this.schedule.setNextCheckIn(RETRY_PAUSE_MS);
    }

    /**
     * Tells whether a slot is free although the wanted pool is not fully running, i.e. whether
     * a hire is still outstanding. An occupancy that cannot be determined counts as outstanding.
     */
    private boolean hasUnfilledSlot() {
        if (this.dispatch.getAvailableSlots() <= 0) {
            return false;
        }
        Optional<List<String>> occupied = this.occupiedNames();
        return occupied.isEmpty() || this.planner.hasMissing(this.config.targets(), occupied.get());
    }

    private void log(final String message) {
        System.out.println(LOG_PREFIX + message);
    }
}
