package dev.shared.do_gamer.task;

import com.github.manolo8.darkbot.core.manager.GuiManager;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;

@Feature(name = "Close AD Offer", description = "Automatically closes AD Offer on the left side (temporary solution until the bot is fixed).")
public class CloseAdOffer implements Task {
    private final GuiManager gui;

    public CloseAdOffer(PluginAPI api) {
        this.gui = api.requireInstance(GuiManager.class);
    }

    @Override
    public void onTickTask() {
        if (this.gui.targetedOffers.trySetShowing(false)) {
            // Legacy close by clicking top-right corner
            this.gui.targetedOffers.click(this.gui.targetedOffers.width - 3, 3);

            System.out.println("Closed AD Offer");
        }
    }
}
