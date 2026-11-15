package dev.shared.fix3dll.behaviour.auto_dispatch;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.darkbot.api.API;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.DispatchAPI.Retriever;

/**
 * Timing state of the feature: when the running retrievers finish and when the next check is due.
 *
 * <p>The game does not count the remaining time of a retriever down in memory; the value is
 * only correct at the moment the in-progress list is drawn. The behaviour therefore hands the
 * list over once per cycle, while it is on screen (see {@link #record(List)}), and the remaining
 * times are turned into absolute finishing times right then. Nothing is watched in between.
 *
 * <p>Shared as a singleton between the behaviour, which updates it on the bot's main tick, and
 * the status row of the settings window, which reads it from the UI thread. Everything the UI
 * thread touches is either {@code volatile} or immutable.
 *
 * <p>All state lives in memory of the running bot instance. Nothing is persisted, so several bot
 * instances with different accounts never see each other's schedule.
 */
public final class DispatchSchedule implements API.Singleton {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private static final long MINUTE_MS = 60_000L;
    /**
     * Pause between a retriever's expected end and the check that collects it. Covers the few
     * seconds by which the recorded finishing time may run ahead of the game.
     */
    private static final long WAKE_DELAY_MS = 60_000L;
    /** Added to a retry pause so the check runs just after the pause has ended, not on its edge. */
    private static final long RETRY_SLACK_MS = 1_000L;
    /** Longest wait ever scheduled, so that a misread remaining time cannot stall the feature. */
    private static final long MAX_WAIT_MS = 24 * 60 * MINUTE_MS;

    private final BotAPI bot;
    /** Retrievers in progress as of the last {@link #record(List)}; replaced as a whole. */
    private volatile List<Running> running = Collections.emptyList();
    /** Epoch milliseconds of the next scheduled check; read by the UI thread. */
    private volatile long nextCheckAt;

    public DispatchSchedule(final BotAPI bot) {
        this.bot = bot;
    }

    /**
     * Records the finishing times of the retrievers in progress.
     *
     * <p>To be called while the in-progress list is shown in game, since that is when the
     * remaining times are correct.
     *
     * @param inProgress the game's list of retrievers in progress
     */
    void record(final List<? extends Retriever> inProgress) {
        long now = System.currentTimeMillis();
        List<Running> entries = new ArrayList<>(inProgress.size());
        for (Retriever retriever : inProgress) {
            entries.add(new Running(retriever.getName(), now + (long) (retriever.getDuration() * 1000)));
        }
        this.running = Collections.unmodifiableList(entries);
    }

    /**
     * @return number of retrievers in progress as of the last {@link #record(List)}; a caller
     *         seeing a different number of occupied slots knows the record is outdated
     */
    int recordedCount() {
        return this.running.size();
    }

    /**
     * @return {@code true} if a recorded retriever should be finished by now
     */
    boolean hasDue() {
        long now = System.currentTimeMillis();
        for (Running entry : this.running) {
            if (entry.endAt <= now) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decides when the next check should run and remembers it for the status row.
     *
     * <p>The check is timed for shortly after the first running retriever is due. Two situations
     * shorten that wait:
     * <ul>
     *   <li>A slot is free although something wanted is not running. Waiting for the next
     *       retriever could take hours, so the check runs when the pending retry pause ends, or
     *       after the fallback interval if no retry is pending (for example when the balance is
     *       too low).</li>
     *   <li>No future finishing time is known, in which case the fallback interval is used.</li>
     * </ul>
     *
     * @param fallbackMs      the configured fallback interval, in milliseconds
     * @param hasUnfilledSlot whether a slot is free while a wanted retriever is not running
     * @param msUntilRetry    milliseconds until a paused retriever may be tried again, or a
     *                        negative value if none is paused
     * @return the delay from now until the next check, in milliseconds
     */
    long scheduleNextCheck(final long fallbackMs, final boolean hasUnfilledSlot, final long msUntilRetry) {
        long delay = Math.min(this.dueDelay(fallbackMs), MAX_WAIT_MS);
        if (hasUnfilledSlot) {
            long retry = msUntilRetry >= 0 ? msUntilRetry + RETRY_SLACK_MS : fallbackMs;
            delay = Math.min(delay, retry);
        }
        this.nextCheckAt = System.currentTimeMillis() + delay;
        return delay;
    }

    /**
     * Remembers an explicitly chosen time for the next check, for display in the status row.
     *
     * @param delayMs time from now until the next check, in milliseconds
     */
    void setNextCheckIn(final long delayMs) {
        this.nextCheckAt = System.currentTimeMillis() + delayMs;
    }

    /**
     * Computes the wait until the first running retriever should be collected.
     *
     * @return the time until the earliest recorded end plus {@link #WAKE_DELAY_MS}; or
     *         {@code fallbackMs} if nothing is recorded or a recorded retriever is already
     *         finished, which means it was left uncollected and waiting for it is pointless
     */
    private long dueDelay(final long fallbackMs) {
        long now = System.currentTimeMillis();
        long earliest = Long.MAX_VALUE;
        for (Running entry : this.running) {
            earliest = Math.min(earliest, entry.endAt);
        }
        return earliest <= now || earliest == Long.MAX_VALUE ? fallbackMs : earliest - now + WAKE_DELAY_MS;
    }

    /**
     * Describes the running retrievers for the status row.
     *
     * <p>Retrievers with the same name and finishing minute are grouped into one entry, for
     * example {@code "3x R-01 ~15:07"}, or {@code "2x R-01 ready"} once they should be finished.
     * Times are estimates and are rounded up to the next full minute.
     *
     * @return one line per group; empty if nothing is running or nothing was recorded yet
     */
    public List<String> describeRunning() {
        long now = System.currentTimeMillis();
        Map<String, Integer> groups = new LinkedHashMap<>();
        for (Running entry : this.running) {
            String when = entry.endAt <= now ? "ready" : "~" + clock(entry.endAt);
            groups.merge(entry.name + " " + when, 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>(groups.size());
        for (Map.Entry<String, Integer> group : groups.entrySet()) {
            lines.add(group.getValue() + "x " + group.getKey());
        }
        return lines;
    }

    /**
     * Describes when the next check will run, for the status row.
     *
     * @return an approximate clock time, {@code "now"} if the check is due, or a hint that the
     *         bot has to be started first, since checks only run while the bot is running
     */
    public String describeNextCheck() {
        if (!this.bot.isRunning()) {
            return "when the bot is started";
        }
        return this.nextCheckAt <= System.currentTimeMillis() ? "now" : "~" + clock(this.nextCheckAt);
    }

    /**
     * Formats a point in time as {@code HH:mm}, rounded up to the next full minute so that the
     * displayed time is never earlier than the actual one.
     */
    private static String clock(final long epochMs) {
        long roundedUp = (epochMs + MINUTE_MS - 1) / MINUTE_MS * MINUTE_MS;
        return CLOCK.format(Instant.ofEpochMilli(roundedUp));
    }

    /** Immutable name and expected finishing time (epoch milliseconds) of one retriever in progress. */
    private static final class Running {
        private final String name;
        private final long endAt;

        Running(final String name, final long endAt) {
            this.name = name;
            this.endAt = endAt;
        }
    }
}
