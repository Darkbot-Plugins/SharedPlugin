package dev.shared.fix3dll.behaviour.auto_dispatch.config;

import java.util.ArrayList;
import java.util.List;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Readonly;

/**
 * User settings of the Auto Dispatch feature.
 *
 * <p>Field names are part of the saved configuration; renaming one resets that setting for
 * existing users. Labels and tooltips come from the {@code fix3dll.auto_dispatch.*} keys of the
 * language files.
 */
@Configuration("fix3dll.auto_dispatch")
public class AutoDispatchConfig {

    @Option("general.enabled")
    public boolean enabled = false;

    /**
     * Minutes until the next check when no finishing time is known to schedule it by, for example
     * when nothing is running or a free slot cannot be filled yet.
     */
    @Option("fix3dll.auto_dispatch.fallback_interval")
    @Number(min = 5, max = 600, step = 1)
    public int fallbackInterval = 10;

    @Option("fix3dll.auto_dispatch.only_outside_gg")
    public boolean onlyOutsideGg = true;

    @Option("fix3dll.auto_dispatch.collect")
    public boolean collect = true;

    /**
     * The five choices together form the pool of retrievers to keep running. They are not tied
     * to particular slots and their order has no meaning; see {@link #targets()}.
     */
    @Option("fix3dll.auto_dispatch.retriever_1")
    @Dropdown(options = RetrieverOptions.class)
    public String retriever1 = RetrieverOptions.NONE;

    @Option("fix3dll.auto_dispatch.retriever_2")
    @Dropdown(options = RetrieverOptions.class)
    public String retriever2 = RetrieverOptions.NONE;

    @Option("fix3dll.auto_dispatch.retriever_3")
    @Dropdown(options = RetrieverOptions.class)
    public String retriever3 = RetrieverOptions.NONE;

    @Option("fix3dll.auto_dispatch.retriever_4")
    @Dropdown(options = RetrieverOptions.class)
    public String retriever4 = RetrieverOptions.NONE;

    @Option("fix3dll.auto_dispatch.retriever_5")
    @Dropdown(options = RetrieverOptions.class)
    public String retriever5 = RetrieverOptions.NONE;

    /** Credits that are never spent: a hire is skipped if it would leave less than this. */
    @Option("fix3dll.auto_dispatch.min_credits")
    @Number(min = 0, max = 2_000_000_000, step = 100_000)
    public int minCredits = 1_000_000;

    /** Uridium that is never spent: a hire is skipped if it would leave less than this. */
    @Option("fix3dll.auto_dispatch.min_uridium")
    @Number(min = 0, max = 2_000_000_000, step = 1_000)
    public int minUridium = 10_000;

    /**
     * What to do when a retriever cannot be scrolled into view: when set, the selection is
     * written to the game directly instead of clicking a row. This reaches retrievers that
     * scrolling cannot, but selecting a row that is not on screen may crash the game client;
     * when unset, such a hire counts as failed and is retried later.
     */
    @Option("fix3dll.auto_dispatch.direct_select")
    public boolean directSelect = true;

    /**
     * Placeholder for the read-only status row. It never holds a value; the row is rendered by
     * {@link DispatchStatusEditor} from live data, so nothing about it is saved.
     */
    @Option("fix3dll.auto_dispatch.status")
    @Readonly
    @Editor(DispatchStatusEditor.class)
    public String status = null;

    /**
     * Returns the pool of retrievers the user wants to keep running.
     *
     * <p>Each configured choice contributes one entry, so choosing the same retriever three
     * times means three of it should run at once. Unset choices are left out. The pool is
     * unordered: free slots are filled with whatever entry is not running yet.
     *
     * @return the wanted retriever names, possibly containing duplicates; never {@code null}
     */
    public List<String> targets() {
        String[] choices = {
                this.retriever1, this.retriever2, this.retriever3, this.retriever4, this.retriever5
        };
        List<String> targets = new ArrayList<>(choices.length);
        for (String choice : choices) {
            if (choice != null && !choice.isEmpty() && !RetrieverOptions.NONE.equals(choice)) {
                targets.add(choice);
            }
        }
        return targets;
    }
}
