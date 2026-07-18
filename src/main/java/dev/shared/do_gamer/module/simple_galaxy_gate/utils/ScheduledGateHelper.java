package dev.shared.do_gamer.module.simple_galaxy_gate.utils;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import dev.shared.do_gamer.module.simple_galaxy_gate.SimpleGalaxyGate;
import eu.darkbot.util.Timer;

/**
 * Helper class for managing the bot's behavior when waiting for a gate to open.
 */
public class ScheduledGateHelper {

    public static final long PRE_START_WAIT_TIMEOUT = 60L;

    private final Timer stopTimer = Timer.get();
    private boolean autoStart = false;

    public boolean isAutoStart() {
        return this.autoStart;
    }

    /**
     * Prepares the bot for waiting for a gate to open.
     *
     * @param module          gate module
     * @param isAccessible    whether the gate is accessible from the current map
     * @param isOffsetReady   whether the server time offset is available
     * @param waitingDuration supplier of seconds until the next gate opening
     * @param setStatus       consumer that updates the gate's status display
     * @param stopDelayFn     maps remaining seconds to the stop-timer delay (ms)
     * @param reset           called when the gate is ready to start (seconds == 0)
     */
    public boolean prepareTick(
            SimpleGalaxyGate module,
            BooleanSupplier isAccessible,
            BooleanSupplier isOffsetReady,
            LongSupplier waitingDuration,
            LongConsumer setStatus,
            LongUnaryOperator stopDelayFn,
            Runnable reset) {
        if (!isAccessible.getAsBoolean()) {
            return false;
        }
        if (!isOffsetReady.getAsBoolean()) {
            return true;
        }
        long seconds = waitingDuration.getAsLong();
        if (seconds > 0) {
            if (module.moveToRefinery()) {
                StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
            } else {
                StateStore.request(StateStore.State.WAITING);
                setStatus.accept(seconds);
                if (seconds > PRE_START_WAIT_TIMEOUT) {
                    this.handleStopping(module, stopDelayFn.applyAsLong(seconds));
                }
            }
            return true;
        }
        reset.run();
        return false;
    }

    /**
     * Handles the logic for stopping the bot when waiting for a gate to open.
     *
     * @param module gate module
     * @param delay  delay in milliseconds before stopping the bot
     */
    public void handleStopping(SimpleGalaxyGate module, long delay) {
        if (!this.stopTimer.isArmed()) {
            this.stopTimer.activate(delay);
            return;
        }
        if (this.stopTimer.isInactive()) {
            module.bot.setRunning(false);
            this.autoStart = true;
        }
    }

    /**
     * Handles the logic for resuming the bot when the gate is about to open.
     *
     * @param module    gate module
     * @param seconds   number of seconds until the gate opens
     * @param setStatus runnable that updates the gate's status display
     */
    public void stoppedTick(SimpleGalaxyGate module, long seconds, Runnable setStatus) {
        if (!this.autoStart) {
            return;
        }
        StateStore.request(StateStore.State.WAITING);
        setStatus.run();
        if (seconds <= PRE_START_WAIT_TIMEOUT) {
            module.bot.handleRefresh();
            module.bot.setRunning(true);
            this.autoStart = false;
        }
        this.stopTimer.disarm();
    }
}
