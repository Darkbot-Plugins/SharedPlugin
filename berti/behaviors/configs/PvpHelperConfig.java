package dev.shared.berti.behaviors.configs;

import com.github.manolo8.darkbot.config.PlayerTag;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Tag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.HeroAPI;
import dev.shared.berti.types.suppliers.NewShipModeSupplier;

@Configuration(value="config.pvphelperconfig")
public class PvpHelperConfig {
    @Option("config.pvphelperconfig.use_rsb")
    public boolean USE_RSB = false;
    @Option("config.pvphelperconfig.lock_and_attack")
    public boolean LOCK_AND_ATTACK = false;
    @Option("config.pvphelperconfig.chase_enemy")
    public boolean CHASE_ENEMY = false;
    @Option("config.pvphelperconfig.radius")
    @Number(max=600.0, step=10.0)
    public double RADIUS = 500.0;
    @Option("config.pvphelperconfig.attack_radius")
    @Number(max=2500.0, step=10.0)
    public double ATTACK_RADIUS = 1250.0;
    @Option("config.pvphelperconfig.ignore_radius")
    @Number(max=2500.0, step=10.0)
    public double IGNORE_RADIUS = 0.0;
    @Option("config.pvphelperconfig.attack_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ATTACK_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.FIRST, SelectableItem.Formation.DRILL);
    @Option("config.pvphelperconfig.roam_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ROAM_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.STAR);
    @Option("config.pvphelperconfig.run_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl RUN_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.STAR);
    @Option("config.pvphelperconfig.whitelist_tag")
    @Tag(value=Tag.Default.NONE)
    public PlayerTag WHITELIST_TAG;
    @Option("config.pvphelperconfig.blacklist_tag")
    @Tag(value=Tag.Default.ALL)
    public PlayerTag BLACKLIST_TAG;
    @Option("config.pvphelperconfig.focus_others_on_empty_blacklist")
    public boolean FOCUS_OTHERS_ON_EMPTY_BLACKLIST = false;
}
