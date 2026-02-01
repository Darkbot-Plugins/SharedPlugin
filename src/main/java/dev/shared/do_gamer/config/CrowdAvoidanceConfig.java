package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

public class CrowdAvoidanceConfig {
    @Option("do_gamer.crowd_avoidance.numb")
    @Number(min = 1, step = 1)
    public int numb = 5;

    @Option("do_gamer.crowd_avoidance.radius")
    @Number(min = 100, step = 50, max = 2000)
    public int radius = 300;

    @Option("do_gamer.crowd_avoidance.consider")
    public ConsiderConfig consider = new ConsiderConfig();

    @Option("do_gamer.crowd_avoidance.avoid_draw_fire")
    public AvoidDrawFireConfig avoidDrawFire = new AvoidDrawFireConfig();

    @Option("do_gamer.crowd_avoidance.other")
    public OtherConfig other = new OtherConfig();

    public static class ConsiderConfig {
        @Option("do_gamer.crowd_avoidance.consider.npcs")
        public boolean npcs = true;

        @Option("do_gamer.crowd_avoidance.consider.enemies")
        public boolean enemies = true;

        @Option("do_gamer.crowd_avoidance.consider.allies")
        public boolean allies = false;
    }

    public static class AvoidDrawFireConfig {
        @Option("do_gamer.crowd_avoidance.avoid_draw_fire.enabled")
        public boolean enabled = true;

        @Option("do_gamer.crowd_avoidance.avoid_draw_fire.use_emp")
        public boolean useEmp = false;
    }

    public static class OtherConfig {
        @Option("do_gamer.crowd_avoidance.other.run_mode")
        public boolean runMode = false;
    }
}
