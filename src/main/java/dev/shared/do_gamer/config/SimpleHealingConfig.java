package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;

@Configuration("do_gamer.simple_healing")
public class SimpleHealingConfig {
    public @Option("do_gamer.simple_healing.hp_repair") HpRepairConfig hp = new HpRepairConfig();

    public @Option("do_gamer.simple_healing.shield_repair") ShieldRepairConfig shield = new ShieldRepairConfig();

    public @Option("do_gamer.simple_healing.repair_pod") HpRepairConfig hpPod = new HpRepairConfig();

    public @Option("do_gamer.simple_healing.pet_combo_repair") HpRepairConfig petCombo = new HpRepairConfig();

    public static class HpRepairConfig {

        @Option("general.enabled")
        public boolean enabled = false;

        @Option("do_gamer.simple_healing.min_hp")
        @Percentage
        public double min = 0.5;
    }

    public static class ShieldRepairConfig {

        @Option("general.enabled")
        public boolean enabled = false;

        @Option("do_gamer.simple_healing.min_shield")
        @Percentage
        public double min = 0.5;
    }
}
