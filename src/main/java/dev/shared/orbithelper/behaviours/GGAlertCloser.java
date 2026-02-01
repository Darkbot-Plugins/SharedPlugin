package dev.shared.orbithelper.behaviours;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.util.Timer;

@Feature(name = "GG Alert Closer", description = "Closes alerts at Galaxy Gates (Hades, LoW and Kuiper) [Special thanks: @do-gamer]")
public class GGAlertCloser implements Behavior {
    private final GameScreenAPI gameScreen;
    private final StarSystemAPI starSystem;

    private Timer actionTimer = Timer.get();

    public GGAlertCloser(PluginAPI api) {
        this.gameScreen = api.requireAPI(GameScreenAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
    }

    private String getName(int number) {
        return "video" + number;
    }

    @Override
    public void onTickBehavior() {
        if (!this.isInGalaxyGate() || this.actionTimer.isActive()) {
            return; // Not in GG or waiting for timer
        }

        // Scan range 1000-3000 for alert windows
        this.scanRange(1000, 3000);

        this.actionTimer.activate(2_000L); // 2 seconds delay
    }

    private void scanRange(int start, int end) {
        for (int i = start; i < end; i++) {
            String name = this.getName(i);
            Gui gui = this.gameScreen.getGui(name);

            if (this.performClick(gui)) {
                return; // Process one window at a time
            }
        }
    }

    private boolean performClick(Gui gui) {
        // Check if the GUI exists and is visible (neagtive X means off-screen)
        if (gui != null && gui.getWidth() > 0 && gui.getX() > 0) {
            // Buttom click coordinates are relative to the GUI's top-left corner
            // Max click offsets - x: +24, y: +9
            // Reccomended offsets - x: +10, y: -1 (to avoid misclicks)
            int relativeX = (int) gui.getWidth() + 10;
            int relativeY = (int) gui.getHeight() - 1;

            gui.click(relativeX, relativeY);
            return true;
        }
        return false;
    }

    private boolean isInGalaxyGate() {
        GameMap map = this.starSystem.getCurrentMap();
        if (map == null || !map.isGG()) {
            return false;
        }
        String name = map.getShortName();
        // Check for Hades, LoW and Kuiper gates
        return name.equals("Hades") || name.equals("LoW") || name.equals("GG ς");
    }
}
