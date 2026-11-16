package dev.shared.fix3dll.behaviour.auto_dispatch;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.managers.GameScreenAPI;

/**
 * Keeps the feature from clicking while another game window is in the way.
 *
 * <p>Everything inside the dispatch window is done with mouse clicks at screen positions. A
 * click therefore reaches whatever window is on top at that position, which may belong to a
 * different feature and could trigger something unintended there.
 *
 * <p>Two checks are offered:
 * <ul>
 *   <li>{@link #isBusy()} before a cycle: windows that other features work in are open, so the
 *       cycle should not start yet.</li>
 *   <li>{@link #coveredBy()} during a cycle: a window appeared after the cycle started and
 *       overlaps the dispatch window. The game puts newly opened windows on top, so such a
 *       window covers the dispatch window; windows that were already open stay beneath it.</li>
 * </ul>
 *
 * <p>Only windows known to the bot can be seen here; a window the bot does not track is not
 * detected.
 */
final class WindowGuard {
    /** Window of the feature itself. */
    private static final String DISPATCH = "dispatch";
    /** Dialogs and popups that belong to the dispatch window and never count as foreign. */
    private static final String[] OWN_WINDOWS = {
        DISPATCH, "popup_generic_icon", "popup_generic_icon_ok", "dispatch_popup_reward_list"
    };
    /** Windows other features work in: logout countdown, galaxy gate spinner, assembly. */
    private static final String[] BUSY_WINDOWS = {"logout", "ggBuilder", "assembly"};

    private final GameScreenAPI screen;
    /** Windows that were already visible when the dispatch window was opened, compared by identity. */
    private final Set<Gui> beneath = Collections.newSetFromMap(new IdentityHashMap<>());

    WindowGuard(final GameScreenAPI screen) {
        this.screen = screen;
    }

    /**
     * @return {@code true} if a window that another feature is likely working in is open
     */
    boolean isBusy() {
        for (String key : BUSY_WINDOWS) {
            Gui gui = this.screen.getGui(key);
            if (gui != null && gui.isVisible()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remembers which windows are visible right now. To be called right before the dispatch
     * window is opened: those windows end up beneath it and are ignored by
     * {@link #coveredBy()}, while anything that appears from then on, including while the
     * dispatch window is still opening, counts as covering it.
     */
    void markBeneath() {
        this.beneath.clear();
        for (Gui gui : this.screen.getGuis()) {
            if (gui.isVisible()) {
                this.beneath.add(gui);
            }
        }
    }

    /**
     * Looks for a window that appeared after {@link #markBeneath()} and overlaps the dispatch
     * window.
     *
     * @return a description of the covering window (its position and size, as windows carry
     *         no name here), or {@code null} if the dispatch window is not covered
     */
    String coveredBy() {
        Gui dispatch = this.screen.getGui(DISPATCH);
        if (dispatch == null || !dispatch.isVisible()) {
            return null;
        }
        for (Gui gui : this.screen.getGuis()) {
            if (gui.isVisible() && !this.beneath.contains(gui) && !this.isOwn(gui) && overlaps(dispatch, gui)) {
                return "a window at " + (int) gui.getX() + "," + (int) gui.getY()
                        + " sized " + (int) gui.getWidth() + "x" + (int) gui.getHeight();
            }
        }
        return null;
    }

    private boolean isOwn(final Gui gui) {
        for (String key : OWN_WINDOWS) {
            if (this.screen.getGui(key) == gui) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} if both windows have a real size and their rectangles intersect
     */
    private static boolean overlaps(final Gui a, final Gui b) {
        return b.getWidth() > 0 && b.getHeight() > 0
                && a.getX() < b.getX2() && b.getX() < a.getX2()
                && a.getY() < b.getY2() && b.getY() < a.getY2();
    }
}
