package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

public class CrowdAvoidanceConfig {
    @Option("do_gamer.crowd_avoidance.numb")
    @Number(min = 1, step = 1)
    public int numb = 5;

    @Option("do_gamer.crowd_avoidance.radius")
    @Number(min = 100, step = 100, max = 2000)
    public int radius = 300;

    @Option("do_gamer.crowd_avoidance.consider")
    public ConsiderConfig consider = new ConsiderConfig();

    public static class ConsiderConfig {
        @Option("do_gamer.crowd_avoidance.consider.npcs")
        public boolean npcs = true;

        @Option("do_gamer.crowd_avoidance.consider.enemies")
        public boolean enemies = true;

        @Option("do_gamer.crowd_avoidance.consider.allies")
        public boolean allies = false;
    }
}
