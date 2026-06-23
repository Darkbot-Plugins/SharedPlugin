package dev.shared.berti.types.suppliers;

import com.github.manolo8.darkbot.gui.tree.utils.SizedLabel;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.config.util.OptionEditor;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.HeroAPI;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class NewShipModeSupplier
extends JPanel
implements OptionEditor<ShipMode> {
    private final JComboBox<SelectableItem.Formation> comboBox = new JComboBox<SelectableItem.Formation>(SelectableItem.Formation.values());
    private final List<ConfigButton> configButtons = Arrays.stream(HeroAPI.Configuration.values()).filter(c -> c != HeroAPI.Configuration.UNKNOWN).map(config -> new ConfigButton(this, (HeroAPI.Configuration)config)).collect(Collectors.toList());
    private HeroAPI.Configuration config;
    private SelectableItem.Formation formation;

    public NewShipModeSupplier(PluginAPI paramPluginAPI) {
        super(new FlowLayout(0, 0, 0));
        this.setOpaque(false);
        for (ConfigButton configButton : this.configButtons) {
            this.add(configButton);
        }
        this.comboBox.addItemListener(item -> {
            if (item.getStateChange() == 1) {
                this.setFormation((SelectableItem.Formation)item.getItem());
            }
        });
        this.add((Component)new SizedLabel("  "));
        this.add(this.comboBox);
    }

    public JComponent getEditorComponent(ConfigSetting<ShipMode> shipConfig) {
        ShipMode value = (ShipMode)shipConfig.getValue();
        this.setConfig(value.getConfiguration());
        this.setFormation(value.getFormation());
        return this;
    }

    public ShipMode getEditorValue() {
        return ShipMode.of((HeroAPI.Configuration)this.config, (SelectableItem.Formation)this.formation);
    }

    public Dimension getReservedSize() {
        return new Dimension(250, 0);
    }

    private void setConfig(HeroAPI.Configuration config) {
        this.config = config;
        this.configButtons.forEach(Component::repaint);
    }

    private void setFormation(SelectableItem.Formation formation) {
        this.formation = formation;
        this.comboBox.setSelectedItem(formation);
    }

    private class ConfigButton
    extends JButton {
        private final NewShipModeSupplier shipMode;
        private final HeroAPI.Configuration config;

        ConfigButton(NewShipModeSupplier shipMode, HeroAPI.Configuration config) {
            super(String.valueOf(config.ordinal()));
            this.shipMode = shipMode;
            this.config = config;
            this.setPreferredSize(new Dimension(17, 17));
            this.addActionListener(a -> NewShipModeSupplier.this.setConfig(config));
        }

        @Override
        public boolean isDefaultButton() {
            return this.shipMode.config == this.config;
        }
    }
}
