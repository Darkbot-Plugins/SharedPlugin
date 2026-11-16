package dev.shared.fix3dll.behaviour.auto_dispatch;

import java.util.Arrays;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.api.Capability;
import com.github.manolo8.darkbot.core.manager.DispatchManager;
import com.github.manolo8.darkbot.core.objects.gui.DispatchIconGui;
import com.github.manolo8.darkbot.core.objects.gui.DispatchIconOkGui;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.utils.NativeAction;

/**
 * Thin adapter over the in-game dispatch window and its two dialogs.
 *
 * <p>DarkBotAPI's {@code DispatchAPI} exposes the dispatch data read-only; the clicks needed to
 * collect and hire only exist on DarkBot's own {@link DispatchManager}. This class is the single
 * place of the feature that depends on those DarkBot internals, so that a future API covering
 * the clicks would only require changes here.
 *
 * <p>The window is opened and closed through direct game calls, but everything inside it is a
 * mouse click at a fixed offset from the window's position. The click methods return
 * {@code false} when the window was not ready, in which case <em>nothing was clicked</em> and the
 * caller is expected to try again on a later tick.
 */
final class DispatchWindow {
    /** Position of the first row of the offer list, relative to the window. */
    private static final int FIRST_ROW_X = 300;
    private static final int FIRST_ROW_Y = 150;
    /** Distance between two rows of the offer list. */
    private static final int ROW_HEIGHT = 40;
    /** A point in the middle of the offer list, where wheel events are sent. */
    private static final int LIST_MIDDLE_Y = 250;

    private final DispatchManager manager;
    /** Dialog asking to confirm a hire. */
    private final DispatchIconGui confirmPopup;
    /** Note shown after an action: the collected rewards, a hire confirmation or an error. */
    private final DispatchIconOkGui resultPopup;

    DispatchWindow(final PluginAPI api) {
        this.manager = api.requireInstance(DispatchManager.class);
        this.confirmPopup = api.requireInstance(DispatchIconGui.class);
        this.resultPopup = api.requireInstance(DispatchIconOkGui.class);
    }

    /**
     * Requests the window to be shown.
     *
     * @return {@code true} once the window is shown and its opening animation has finished
     */
    boolean open() {
        return this.manager.show(true);
    }

    /**
     * Requests the window to be hidden.
     *
     * @return {@code true} once the window is hidden and its closing animation has finished
     */
    boolean close() {
        return this.manager.show(false);
    }

    /**
     * Switches to the retriever tab.
     *
     * <p>The window also has a gate tab whose controls sit at the same positions. Every cycle
     * selects the retriever tab first, so that later clicks cannot act on a gate by accident.
     *
     * @return {@code true} if the tab was clicked
     */
    boolean showRetrievers() {
        return this.manager.openRetrieverTab();
    }

    /**
     * Switches to the list of retrievers in progress. Drawing this list is also what makes the
     * game update it with retrievers that were hired a moment ago.
     *
     * @return {@code true} if the tab was clicked
     */
    boolean showInProgress() {
        return this.manager.openInProgressTab();
    }

    /**
     * Switches to the list of retrievers offered for hire.
     *
     * @return {@code true} if the tab was clicked
     */
    boolean showAvailable() {
        return this.manager.openAvailableTab();
    }

    /**
     * Clicks the first row of the offer list.
     *
     * <p>The hire button stays disabled until a row has been clicked; a selection that was only
     * written to memory is not enough for the game to enable it.
     *
     * @return {@code true} if the row was clicked
     */
    boolean selectFirstRow() {
        return this.manager.clickFirstItem();
    }

    /**
     * Clicks one of the visible rows of the offer list, which selects that retriever.
     *
     * @param row zero-based row counted from the first visible one
     * @return {@code true} if the row position was clicked
     */
    boolean clickOfferRow(final int row) {
        if (!this.manager.show(true)) {
            return false;
        }
        this.manager.click(FIRST_ROW_X, FIRST_ROW_Y + ROW_HEIGHT * row);
        return true;
    }

    /**
     * Scrolls the offer list with the mouse wheel.
     *
     * @param down    {@code true} to scroll towards the end of the list
     * @param notches number of wheel notches, sent together as one batch
     * @return {@code true} if the wheel events were sent; {@code false} if the window was not
     *         ready or the game client in use cannot receive such events
     */
    boolean scrollOffers(final boolean down, final int notches) {
        if (!Main.API.hasCapability(Capability.DIRECT_POST_ACTIONS) || !this.manager.show(true)) {
            return false;
        }
        int x = (int) this.manager.getX() + FIRST_ROW_X;
        int y = (int) this.manager.getY() + LIST_MIDDLE_Y;
        long[] wheel = new long[notches];
        Arrays.fill(wheel, down ? NativeAction.MouseWheel.down(x, y) : NativeAction.MouseWheel.up(x, y));
        Main.API.postActions(wheel);
        return true;
    }

    /**
     * Clicks the collect button of a retriever in progress.
     *
     * @param row zero-based row in the in-progress list
     * @return {@code true} if the button position was clicked
     */
    boolean clickCollect(final int row) {
        return this.manager.clickCollect(row);
    }

    /**
     * Clicks the hire button for the retriever that is currently selected.
     *
     * @return {@code true} if the button was clicked
     */
    boolean clickHire() {
        return this.manager.clickHire();
    }

    /**
     * @return {@code true} if the dialog asking to confirm a hire is open
     */
    boolean hasConfirmPopup() {
        return this.confirmPopup.isVisible();
    }

    /**
     * Accepts the hire confirmation dialog.
     *
     * @return {@code true} if the dialog was open and its accept button was clicked
     */
    boolean confirm() {
        return this.manager.clickAcceptPopup();
    }

    /**
     * Declines the hire confirmation dialog, if it is open.
     */
    void declineConfirm() {
        this.manager.clickDeclinePopup();
    }

    /**
     * @return {@code true} if a result note is open and has to be dismissed before continuing
     */
    boolean hasResultPopup() {
        return this.resultPopup.isVisible();
    }

    /**
     * Clicks the OK button of the result note.
     *
     * <p>The note grows with its content, so the button sits one line lower for every reward
     * listed. Without rewards the note is a plain message with the button at its default place.
     *
     * @param rewardLines number of rewards listed in the note, or zero for a plain message
     */
    void dismissResult(final int rewardLines) {
        if (rewardLines > 0) {
            this.manager.clickOkRewardsPopup(rewardLines);
        } else {
            this.manager.clickCloseOkPopup();
        }
    }
}
