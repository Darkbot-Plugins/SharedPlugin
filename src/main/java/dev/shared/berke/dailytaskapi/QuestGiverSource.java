package dev.shared.berke.dailytaskapi;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.game.other.Area;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.utils.NativeAction;

/** Source-verified controls for DarkOrbit's quest-giver modal. */
final class QuestGiverSource {
    static final int MAX_ROWS = 6;
    static final int MAX_SCROLL_PAGES = 10;

    private static final double WINDOW_WIDTH = 840d;
    private static final double WINDOW_HEIGHT = 550d;
    private static final double TABS_X = 8d;
    private static final double TABS_Y = 35d;
    private static final double TABS_WIDTH = 813d;
    private static final double TABS_HEIGHT = 21d;
    private static final int TAB_COUNT = 5;
    private static final int DAILY_TAB_POSITION = 3;
    private static final double LIST_X = 8d;
    private static final double LIST_Y = 305d;
    private static final double LIST_WIDTH = 238d;
    private static final double LIST_HEIGHT = 206d;
    private static final double ACCEPT_X = 668d;
    private static final double ACCEPT_Y = 494d;
    private static final double ACCEPT_WIDTH = 162d;
    private static final double ACCEPT_HEIGHT = 21d;

    private final GameScreenAPI screen;

    QuestGiverSource(GameScreenAPI screen) {
        this.screen = screen;
    }

    void selectDailyTab() {
        double tabWidth = TABS_WIDTH / TAB_COUNT;
        click(TABS_X + tabWidth * DAILY_TAB_POSITION - tabWidth / 2d,
                TABS_Y + TABS_HEIGHT / 2d);
    }

    void scrollDailyListDown() {
        Area.Rectangle viewport = screen.getViewBounds();
        double windowX = viewport.getWidth() / 2d - WINDOW_WIDTH / 2d;
        double windowY = viewport.getHeight() / 2d - WINDOW_HEIGHT / 2d;
        int x = (int) Math.round(windowX + LIST_X + LIST_WIDTH / 2d);
        int y = (int) Math.round(windowY + LIST_Y + LIST_HEIGHT / 2d);
        Main.API.postActions(NativeAction.MouseWheel.down(x, y));
    }

    void selectRow(int oneBasedRow) {
        int row = Math.max(1, Math.min(MAX_ROWS, oneBasedRow));
        double rowHeight = LIST_HEIGHT / MAX_ROWS;
        click(LIST_X + LIST_WIDTH / 2d,
                LIST_Y + rowHeight * row - rowHeight / 2d);
    }

    void acceptSelected() {
        click(ACCEPT_X + ACCEPT_WIDTH / 2d, ACCEPT_Y + ACCEPT_HEIGHT / 2d);
    }

    void close() {
        Main.API.keyClick(27);
    }

    private void click(double localX, double localY) {
        Area.Rectangle viewport = screen.getViewBounds();
        double windowX = viewport.getWidth() / 2d - WINDOW_WIDTH / 2d;
        double windowY = viewport.getHeight() / 2d - WINDOW_HEIGHT / 2d;
        Main.API.mouseClick((int) Math.round(windowX + localX),
                (int) Math.round(windowY + localY));
    }
}
