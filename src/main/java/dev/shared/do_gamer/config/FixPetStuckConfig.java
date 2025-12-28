package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

public class FixPetStuckConfig {

    @Option("general.enabled")
    public boolean enabled = true;

    @Option("do_gamer.fix_pet_stuck.stuck_seconds")
    @Number(min = 10, max = 300, step = 10)
    public int stuckSeconds = 60;
}