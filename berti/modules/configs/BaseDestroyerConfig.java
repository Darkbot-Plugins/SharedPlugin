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

@Configuration("config.basedestroyer")
public class BaseDestroyerConfig {
    @Option("config.basedestroyer.wait_for_group")
    public boolean wait_for_group = false;
    @Option("config.basedestroyer.cyclemapsautomatically")
    public boolean cycleMapsAutomatically = false;
    @Option("config.basedestroyer.waitonastroid")
    public boolean waitOnAstroid = false;
    @Option("config.basedestroyer.attackenemyfirst")
    public boolean attackEnemyFirst = false;
    @Option("config.basedestroyer.attackenemylast")
    public boolean attackEnemyLast = false;
    @Option("config.basedestroyer.attackmodulesfirst")
    public boolean attackModulesFirst = false;
    @Option("config.basedestroyer.whitelist_tag")
    @Tag(value = Tag.Default.NONE)
    public PlayerTag WHITELIST_TAG;
    @Option("config.basedestroyer.blacklist_tag")
    @Tag(value = Tag.Default.ALL)
    public PlayerTag BLACKLIST_TAG;
    @Option("config.basedestroyer.focus_others_on_empty_blacklist")
    public boolean FOCUS_OTHERS_ON_EMPTY_BLACKLIST = false;
    @Option("config.basedestroyer.radius")
    @Number(max = 650, step = 25)
    public double radius = 275;
    @Option("config.basedestroyer.attack_radius")
    @Number(max = 2500, step = 10)
    public double ATTACK_RADIUS = 1250;
    @Option("config.basedestroyer.base_radius")
    @Number(max = 2500, step = 10)
    public double BASE_RADIUS = 1250;
    @Option("config.basedestroyer.chasepercent")
    @Percentage
    public double chasePercent = 0.25;
    @Option("config.basedestroyer.usersb")
    public boolean useRSB = false;
    @Option("config.basedestroyer.usedmgbeacon")
    public boolean usedmgbeacon = false;
    @Option("config.basedestroyer.attack_config")
    @Editor(NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ATTACK_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.FIRST, SelectableItem.Formation.RING);
    @Option("config.basedestroyer.roam_config")
    @Editor(NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl ROAM_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.RING);
    @Option("config.basedestroyer.run_config")
    @Editor(NewShipModeSupplier.class)
    public ShipMode.ShipModeImpl RUN_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.RING);
}
