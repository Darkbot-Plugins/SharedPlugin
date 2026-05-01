package dev.shared.witkor01.task.auction;

import com.github.manolo8.darkbot.config.tree.ConfigField;
import com.github.manolo8.darkbot.gui.tree.OptionEditor;

import javax.swing.*;
import java.awt.*;

public class CountdownEditor implements OptionEditor {

    private final JLabel label;

    public CountdownEditor(Auction.AuctionConfig config) {
        this.label = new JLabel("Time until auction end: N/A");
        this.label.setOpaque(false);
        this.label.setFont(this.label.getFont().deriveFont(Font.BOLD));
    }

    @Override
    public JComponent getComponent() { return label; }

    @Override
    public void edit(ConfigField field) {
        Auction.AuctionConfig cfg = (Auction.AuctionConfig) field.getParent();
        int endMinute = (cfg != null) ? cfg.auctionEndMinute : 35;
        long secs = Auction.computeSecondsUntilMinute(endMinute);
        if (secs <= 30) {
            label.setText("Time until auction end: " + secs + " sec  \u26A1");
            label.setForeground(new Color(200, 0, 0));
        } else if (secs <= 120) {
            label.setText("Time until auction end: " + secs + " sec");
            label.setForeground(new Color(180, 100, 0));
        } else {
            label.setText("Time until auction end: " + secs + " sec");
            label.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    @Override
    public Dimension getReservedSize() {
        return new Dimension(300, 20);
    }
}

