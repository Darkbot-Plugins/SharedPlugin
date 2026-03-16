package dev.shared.orbithelper.module.daily_quest;

import java.util.ArrayList;
import java.util.List;

public final class StateStore {
    public enum State {
        INITIALIZING("Initializing..."),
        TRAVELING_TO_QUEST_GIVER("Traveling to Quest Giver..."),
        NAVIGATING_TO_STATION("Navigating to station..."),
        OPENING_QUEST_WINDOW("Opening quest window..."),
        SCANNING_ACTIVE_QUESTS("Scanning active quests..."),
        OPENING_QUEST_GIVER_WINDOW("Opening Quest Giver window..."),
        CHANGING_QUEST_GIVER_TAB("Changing Quest Giver tab..."),
        BROWSING_QUESTS("Browsing quests..."),
        CLOSING_QUEST_GIVER("Closing Quest Giver..."),
        EXECUTING_QUEST("Executing quest..."),
        COMPLETE("All Quests Complete");

        public final String message;

        State(String message) {
            this.message = message;
        }
    }

    private static final List<State> REQUESTS = new ArrayList<>();
    private static State state = State.INITIALIZING;

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
        State resolved = REQUESTS.isEmpty()
                ? StateStore.state
                : REQUESTS.get(REQUESTS.size() - 1);
        if (resolved != null) {
            StateStore.state = resolved;
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
