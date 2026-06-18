package dev.shared.do_gamer.module.simple_galaxy_gate.utils;

import dev.shared.do_gamer.module.simple_galaxy_gate.SimpleGalaxyGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
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
