package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;

public class RepairPetConfig {
    @Option("general.enabled")
    public boolean enabled = true;

    @Option("do_gamer.repair_pet.hp")
    @Percentage
    public double hp = 0.8;
}
