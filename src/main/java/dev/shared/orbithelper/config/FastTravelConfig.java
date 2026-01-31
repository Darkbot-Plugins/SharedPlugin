package dev.shared.orbithelper.config;

import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Number;

public class FastTravelConfig {
    @Option("general.enabled")
    public boolean enabled = true;

    @Option("orbithelper.fast_travel.use_coupon")
    public boolean useJumpCoupon = true;

    @Option("orbithelper.fast_travel.max_jump_attempt_time")
    @Number(min = 30, max = 300, step = 1)
    public int maxJumpAttemptTime = 30;

    @Option("orbithelper.fast_travel.min_jumps")
    @Number(min = 2, max = 6, step = 1)
    public int minJumps = 2;
}
