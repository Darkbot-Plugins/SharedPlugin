package dev.shared.fix3dll.behaviour.auto_dispatch.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.DispatchAPI.Retriever;

/**
 * Supplies the choices of the retriever dropdowns.
 *
 * <p>The list is read from the game instead of being hard-coded, so retrievers that are only
 * offered for a limited time, such as event retrievers, appear on their own while they are
 * available and need no code change.
 *
 * <p>Names seen during the current bot session are remembered in memory, which keeps a choice
 * selectable even if the game stops offering it for a moment. Nothing is written to disk.
 */
public class RetrieverOptions implements Dropdown.Options<String> {
    /**
     * Value meaning "no retriever". A dash is used as the value itself so that the dropdown's
     * default rendering already shows something meaningful.
     */
    public static final String NONE = "-";

    private final DispatchAPI dispatch;
    /** Retriever names seen so far, in the order the game listed them. */
    private final Set<String> seen = new LinkedHashSet<>();

    public RetrieverOptions(final DispatchAPI dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * @return {@link #NONE} followed by every retriever name seen in this session; only
     *         {@link #NONE} until the game has loaded its dispatch data
     */
    @Override
    public Collection<String> options() {
        for (Retriever retriever : this.dispatch.getAvailableRetrievers()) {
            String name = retriever.getName();
            if (name != null && !name.isEmpty()) {
                this.seen.add(name);
            }
        }
        List<String> options = new ArrayList<>(this.seen.size() + 1);
        options.add(NONE);
        options.addAll(this.seen);
        return options;
    }
}
