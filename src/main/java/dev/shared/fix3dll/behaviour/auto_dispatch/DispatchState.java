package dev.shared.fix3dll.behaviour.auto_dispatch;

/**
 * Steps of one dispatch cycle, listed in the order they normally occur.
 *
 * <p>A cycle advances by at most one step per tick and only after the game has reacted to the
 * previous one. The {@code AWAIT_*} states wait for that reaction and time out if it never comes.
 */
enum DispatchState {
    /** No cycle is running; waiting for the next scheduled check. */
    IDLE,
    /** Opening the dispatch window and waiting for its animation to finish. */
    OPENING,
    /** Switching to the retriever tab so that no click can land in the gate view. */
    SHOWING_RETRIEVERS,
    /** Switching to the in-progress list, which also makes the game refresh that list. */
    SHOWING_PROGRESS,
    /** Looking for a finished retriever and clicking its collect button. */
    COLLECTING,
    /** Waiting for the collected retriever to leave the list and dismissing the reward note. */
    AWAIT_COLLECT,
    /** Choosing the next retriever to hire and switching to the list of offers. */
    SELECTING,
    /** Clicking a row of the offer list, which is what enables the hire button. */
    PRIMING,
    /** Waiting for the game to register the row click, then selecting the wanted retriever. */
    TARGETING,
    /** Verifying the selection and clicking the hire button. */
    HIRING,
    /** Accepting the confirmation dialog of the hire. */
    CONFIRMING,
    /** Waiting for a free slot to be taken, which proves the hire went through. */
    AWAIT_HIRE,
    /** Showing the in-progress list once more so the game lists the retrievers just hired. */
    REFRESHING,
    /** Recording the finishing times from the refreshed in-progress list. */
    READING,
    /** Closing the dispatch window and scheduling the next check. */
    CLOSING
}
