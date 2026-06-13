package dev.shared.berti.modules.configs;

import com.github.manolo8.darkbot.config.PlayerTag;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.annotations.Tag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.HeroAPI;
import dev.shared.berti.types.enums.BLMaps;
import dev.shared.berti.types.enums.CustomEvent;
import dev.shared.berti.types.suppliers.NewShipModeSupplier;
import eu.darkbot.api.game.enums.PetGear;
import java.util.EnumSet;
import java.util.Set;

@Configuration(value="blconfig")
public class BLConfig {
    @Option("blconfig.config_settings")
    public CONFIG_SETTINGS CONFIG_SETTINGS = new CONFIG_SETTINGS();
    @Option("blconfig.map_settings")
    public MAP_SETTINGS MAP_SETTINGS = new MAP_SETTINGS();
    @Option("blconfig.clear_settings")
    public CLEAR_SETTINGS CLEAR_SETTINGS = new CLEAR_SETTINGS();
    @Option("blconfig.wait_settings")
    public WAIT_SETTINGS WAIT_SETTINGS = new WAIT_SETTINGS();
    @Option("blconfig.collect_setting")
    public COLLECT_SETTING COLLECT_SETTING = new COLLECT_SETTING();
    @Option("blconfig.ammo_settings")
    public AMMO AMMO_SETTINGS = new AMMO();
    @Option("blconfig.pet_settings")
    public PET PET_SETTINGS = new PET();
    @Option("blconfig.custom_event")
    public CUSTOM_EVENT CUSTOM_EVENT = new CUSTOM_EVENT();
    @Option("blconfig.pvp_helper_settings")
    public PVP_HELPER_SETTINGS PVP_HELPER_SETTINGS = new PVP_HELPER_SETTINGS();
    @Option("blconfig.cloak_setting")
    public CLOAK_SETTING CLOAK_SETTING = new CLOAK_SETTING();
    @Option("blconfig.server_restart")
    public SERVER_RESTART SERVER_RESTART = new SERVER_RESTART();

    @Option("blconfig.slave_mode")
    public boolean slave_mode = false;

    @Option("blconfig.clear_attend")
    public boolean clear_attend = false;

    @Option("blconfig.reload_map")
    public boolean reload_map = false;

    @Option("blconfig.abl_key")
    public SelectableItem.Laser abl_key;

    @Option("blconfig.enable_rsb")
    public boolean enable_rsb = false;

    @Option("blconfig.rsb_key")
    public SelectableItem.Laser rsb_key;

    @Option("blconfig.guard_mode_key")
    public PetGear guard_mode_key;

    @Option("blconfig.spearhead")
    public boolean spearhead = false;

    @Option("blconfig.dmg_beacon")
    public boolean dmg_beacon = false;

    @Option("blconfig.hp_beacon")
    public boolean hp_beacon = false;

    @Option("blconfig.spearhead_key")
    public SelectableItem.Ability spearhead_key;

    @Option("blconfig.dmg_beacon_key")
    public SelectableItem.Pet dmg_beacon_key;

    @Option("blconfig.hp_beacon_key")
    public SelectableItem.Pet hp_beacon_key;

    public static class CUSTOM_EVENT {
        @Option("blconfig.custom_event.custom_events")
        @Dropdown(multi=true)
        public Set<CustomEvent> CUSTOM_EVENTS = EnumSet.allOf(CustomEvent.class);
        @Option("blconfig.custom_event.maps_to_use_custom_events")
        @Dropdown(multi=true)
        public Set<BLMaps> MAPS_TO_USE_CUSTOM_EVENTS = EnumSet.allOf(BLMaps.class);
        @Option("blconfig.custom_event.spawn_beacon_x_sec_before_main_npc")
        @Number(min=0.0, max=240.0)
        public int SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC = 10;
        @Option("blconfig.custom_event.ish")
        @Percentage
        public double ISH = 0.0;
        @Option("blconfig.custom_event.leech")
        @Percentage
        public double LEECH = 0.0;
        @Option("invokeconfig.map_switch_count")
        @Number(min=1.0, max=100.0, step=1.0)
        public int MAP_SWITCH_COUNT = 1;
    }

    public static class PET {
        @Option("blconfig.pet_settings.pet_link_hp")
        @Percentage
        public double PET_LINK_HP = 0.9;
        @Option("blconfig.pet_settings.pet_anti_push")
        public boolean PET_ANTI_PUSH = false;
        @Option("blconfig.pet_settings.combo_repair")
        public boolean COMBO_REPAIR = false;
    }

    public static class AMMO {
        @Option("blconfig.ammo_settings.abl_first")
        public boolean ABL_FIRST = false;
    }

    public static class COLLECT_SETTING {
        @Option("blconfig.collect_setting.collect_config")
        @Editor(value=NewShipModeSupplier.class)
        public ShipMode.ShipModeImpl COLLECT_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.VETERAN);
        @Option("blconfig.collect_setting.collect")
        public boolean COLLECT = true;
        @Option("blconfig.collect_setting.min_exp_boost")
        @Number(min=0.0, max=300.0)
        public int MIN_EXP_BOOST = 0;
        @Option("blconfig.collect_setting.force")
        @Number(min=1.0, max=100000.0, step=1.0)
        public int FORCE = 13;
        @Option("blconfig.collect_setting.collect_npc")
        @Percentage
        public double COLLECT_NPC = 0.15;
    }

    public static class CLOAK_SETTING {
        @Option("blconfig.cloak_setting.buy_cloak_on_main_npc_death")
        public boolean BUY_CLOAK_ON_MAIN_NPC_DEATH = false;
        @Option("blconfig.cloak_setting.buy_cloak_on_npcs_cleared")
        public boolean BUY_CLOAK_ON_NPCS_CLEARED = false;
        @Option("blconfig.cloak_setting.rebuy_cloak")
        public boolean REBUY_CLOAK = false;
    }

    public static class SERVER_RESTART {
        @Option("blconfig.server_restart.force_collect_server_restart")
        public boolean FORCE_COLLECT_SERVER_RESTART = false;
        @Option("blconfig.server_restart.minimum_box_amount")
        @Number(min=1.0, max=100.0, step=1.0)
        public int MINIMUM_BOX_AMOUNT = 5;
    }

    public static class PVP_HELPER_SETTINGS {
        @Option("blconfig.pvp_helper_settings.whitelist_tag")
        @Tag(value=Tag.Default.NONE)
        public PlayerTag WHITELIST_TAG;
        @Option("blconfig.pvp_helper_settings.blacklist_tag")
        @Tag(value=Tag.Default.ALL)
        public PlayerTag BLACKLIST_TAG;
        @Option("blconfig.pvp_helper_settings.focus_others_on_empty_blacklist")
        public boolean FOCUS_OTHERS_ON_EMPTY_BLACKLIST = false;
        @Option("blconfig.pvp_helper_settings.focus_enemy_first")
        public boolean FOCUS_ENEMY_FIRST = false;
        @Option("blconfig.pvp_helper_settings.focus_enemy_last")
        public boolean FOCUS_ENEMY_LAST = false;
        @Option("blconfig.pvp_helper_settings.focus_enemy_after_big_npcs")
        public boolean FOCUS_ENEMY_AFTER_BIG_NPCS = false;
        @Option("blconfig.pvp_helper_settings.enemy_radius")
        @Number(min=0.0, max=1000.0, step=10.0)
        public double ENEMY_RADIUS = 350.0;
        @Option("blconfig.pvp_helper_settings.attack_radius")
        @Number(max=2500.0, step=10.0)
        public double ATTACK_RADIUS = 1250.0;
        @Option("blconfig.pvp_helper_settings.only_respond")
        public boolean ONLY_RESPOND = false;
    }

    public static class CLEAR_SETTINGS {
        @Option("blconfig.clear_settings.maps_to_clean")
        @Dropdown(multi=true)
        public Set<BLMaps> MAPS_TO_CLEAN = EnumSet.allOf(BLMaps.class);
        @Option("blconfig.clear_settings.clean_impulse")
        public boolean CLEAN_IMPULSE = false;
        @Option("blconfig.clear_settings.clean_aggressive_npc")
        public boolean CLEAN_AGGRESSIVE_NPC = false;
    }

    public static class WAIT_SETTINGS {
        @Option("blconfig.wait_settings.time_to_spawn")
        @Number(min=0.0, max=900.0, step=15.0)
        public int TIME_TO_SPAWN = 0;
        @Option("blconfig.wait_settings.wait_for_group")
        public boolean WAIT_FOR_GROUP = false;
        @Option("blconfig.wait_settings.wait_on_portal")
        public boolean WAIT_ON_PORTAL = false;
        @Option("blconfig.wait_settings.wait_on_preferred_zone")
        public boolean WAIT_ON_PREFERRED_ZONE = false;
        @Option("blconfig.wait_settings.roam_on_spot")
        public boolean ROAM_ON_SPOT = false;
    }

    public static class MAP_SETTINGS {
        @Option("blconfig.map_settings.rotation")
        public boolean ROTATION = true;
        @Option("blconfig.map_settings.maps_to_visit_list")
        @Dropdown(multi=true)
        public Set<BLMaps> MAPS_TO_VISIT_LIST = EnumSet.allOf(BLMaps.class);
        @Option("blconfig.map_settings.npc_radius")
        @Number(min=0.0, max=5000.0, step=5.0)
        public double NPC_RADIUS = 700.0;
        @Option("blconfig.map_settings.enable_death_count")
        public boolean ENABLE_DEATH_COUNT = false;
        @Option("blconfig.map_settings.death_count")
        @Number(min=1.0, max=25.0, step=1.0)
        public int DEATH_COUNT = 1;
        @Option("blconfig.map_settings.safety")
        public boolean SAFETY = false;
        @Option("blconfig.map_settings.refresh_in_main_npc_zone")
        public boolean REFRESH_IN_MAIN_NPC_ZONE = true;
    }

    public static class CONFIG_SETTINGS {
        @Option("blconfig.config_settings.attack_config")
        @Editor(value=NewShipModeSupplier.class)
        public ShipMode.ShipModeImpl ATTACK_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.FIRST, SelectableItem.Formation.DRILL);
        @Option("blconfig.config_settings.roam_config")
        @Editor(value=NewShipModeSupplier.class)
        public ShipMode.ShipModeImpl ROAM_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.STAR);
        @Option("blconfig.config_settings.run_config")
        @Editor(value=NewShipModeSupplier.class)
        public ShipMode.ShipModeImpl RUN_CONFIG = new ShipMode.ShipModeImpl(HeroAPI.Configuration.SECOND, SelectableItem.Formation.STAR);
    }
}
