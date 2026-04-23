package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.ArrayList;
import java.util.List;

import eu.darkbot.util.Timer;

public final class StateStore {
    public enum State {
        WAITING("Waiting"),
        WAITING_IN_GATE("Waiting in Gate"),
        BUILDING("Building Gate"),
        TRAVELING_TO_GATE("Traveling to Gate"),
        ATTACKING("Attacking"),
        COLLECTING("Collecting"),
        KAMIKAZE("Kamikaze"),
        GUARDING("Guarding"),
        REPAIRING("Repairing"),
        JUMPING("Jumping to next map"),
        MOVE_TO_SAFE_POSITION("Moving to safe position");

        public final String message;

        State(String message) {
            this.message = message;
        }
    }

    private static final List<State> REQUESTS = new ArrayList<>();
    private static final Timer delayTimer = Timer.get(1000L);
    private static State state = State.WAITING;

    private StateStore() {
    }

    /**
     * Request a state change.
     */
    public static void request(State state) {
        REQUESTS.add(state);
    }

    /**
     * Apply requested state changes.
     */
    public static void apply() {
        State resolved = REQUESTS.isEmpty() ? state : REQUESTS.get(REQUESTS.size() - 1);
        if (resolved != null) {
            if (resolved == state) {
                delayTimer.activate();
            } else if (delayTimer.isInactive()) {
                state = resolved;
                delayTimer.activate();
            }
        }
        REQUESTS.clear();
    }

    /**
     * Get the current state.
     */
    public static State current() {
        return state;
    }
}
