package dev.shared.berti.modules.configs;

import com.github.manolo8.darkbot.config.PlayerTag;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.annotations.Tag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.HeroAPI;
import dev.shared.berti.types.suppliers.NewShipModeSupplier;

@Configuration(value="config.basedefend")
public class BaseDefendModuleConfig {
    @Option("config.basedefend.wait_for_group")
    public boolean wait_for_group = false;
    @Option("config.basedefend.cyclemapsautomatically")
    public boolean cycleMapsAutomatically = false;
    @Option("config.basedefend.waitonastroid")
    public boolean waitOnAstroid = false;
    @Option("config.basedefend.attackenemyfirst")
    public boolean attackEnemyFirst = false;
    @Option("config.basedefend.attackenemylast")
    public boolean attackEnemyLast = false;
    @Option("config.basedefend.attackmodulesfirst")
    public boolean attackModulesFirst = false;
    @Option("config.basedefend.whitelist_tag")
    @Tag(value=Tag.Default.NONE)
    public PlayerTag WHITELIST_TAG;
    @Option("config.basedefend.blacklist_tag")
    @Tag(value=Tag.Default.ALL)
    public PlayerTag BLACKLIST_TAG;
    @Option("config.basedefend.focus_others_on_empty_blacklist")
    public boolean FOCUS_OTHERS_ON_EMPTY_BLACKLIST = false;
    @Option("config.basedefend.radius")
    @Number(max=650.0, step=25.0)
    public double radius = 275.0;
    @Option("config.basedefend.attack_radius")
    @Number(max=2500.0, step=10.0)
    public double ATTACK_RADIUS = 1250.0;
    @Option("config.basedefend.base_radius")
    @Number(max=2500.0, step=10.0)
    public double BASE_RADIUS = 1250.0;
    @Option("config.basedefend.chasepercent")
    @Percentage
    public double chasePercent = 0.25;
    @Option("config.basedefend.usersb")
    public boolean useRSB = false;
    @Option("config.basedefend.usedmgbeacon")
    public boolean usedmgbeacon = false;
    @Option("config.basedefend.attack_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ATTACK_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.FIRST, SelectableItem.Formation.RING);
    @Option("config.basedefend.roam_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ROAM_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.RING);
    @Option("config.basedefend.run_config")
    @Editor(value=NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl RUN_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.RING);
}
