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

    @Option("do_gamer.crowd_avoidance.npcs")
    public boolean npcs = true;

    @Option("do_gamer.crowd_avoidance.enemies")
    public boolean enemies = true;

    @Option("do_gamer.crowd_avoidance.allies")
    public boolean allies = false;

    @Option("do_gamer.crowd_avoidance.avoid_distance")
    @Number(min = 1000, step = 500, max = 6000)
    public int avoidDistance = 2000;
}
