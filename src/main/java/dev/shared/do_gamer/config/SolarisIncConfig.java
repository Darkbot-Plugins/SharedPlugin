package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.managers.HeroAPI;

public class SolarisIncConfig {
    @Option("general.enabled")
    public boolean enabled = true;

    @Option("do_gamer.solaris_inc.config.npc")
    public NpcConfig npc = new NpcConfig();

    @Option("do_gamer.solaris_inc.config.stick")
    public StickConfig stick = new StickConfig();

    @Option("do_gamer.solaris_inc.config.other")
    public OtherConfig other = new OtherConfig();

    public static class NpcConfig {
        @Option("do_gamer.solaris_inc.config.npc.max_distance")
        @Number(min = 50, max = 600, step = 50)
        public int maxDistance = 500;

        @Option("do_gamer.solaris_inc.config.npc.min_numb")
        @Number(min = 1, max = 50, step = 1)
        public int minNumb = 5;

        @Option("do_gamer.solaris_inc.config.npc.extra_flag_only")
        public boolean extraFlagOnly = false;
    }

    public static class StickConfig {
        @Option("do_gamer.solaris_inc.config.stick.min_hp")
        @Percentage
        public double minHp = 0.8;

        @Option("do_gamer.solaris_inc.config.stick.use_config")
        public boolean useConfig = false;

        @Option("do_gamer.solaris_inc.config.stick.ship_mode")
        public ShipMode shipMode = ShipMode.of(HeroAPI.Configuration.FIRST, null);
    }

    public static class OtherConfig {
        @Option("do_gamer.solaris_inc.config.other.min_wait")
        @Number(min = 200, max = 1000, step = 50)
        public int minWait = 500;

        @Option("do_gamer.solaris_inc.config.other.use_pet_kamikaze")
        public boolean usePetKamikaze = false;
    }

}
