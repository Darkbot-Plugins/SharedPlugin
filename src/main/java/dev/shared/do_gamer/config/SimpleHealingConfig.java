package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;

@Configuration("do_gamer.simple_healing.config")
public class SimpleHealingConfig {
    public @Option("do_gamer.simple_healing.config.hp_repair") HpRepairConfig hp = new HpRepairConfig();

    public @Option("do_gamer.simple_healing.config.shield_repair") ShieldRepairConfig shield = new ShieldRepairConfig();

    public @Option("do_gamer.simple_healing.config.repaid_pod") HpRepairConfig hpPod = new HpRepairConfig();

    public @Option("do_gamer.simple_healing.config.pet_combo_repair") HpRepairConfig petCombo = new HpRepairConfig();

    public static class HpRepairConfig {

        @Option("general.enabled")
        public boolean ENABLED = false;

        @Option("do_gamer.simple_healing.config.min_hp")
        @Percentage
        public double MIN_HP = 0.5;
    }

    public static class ShieldRepairConfig {

        @Option("general.enabled")
        public boolean ENABLED = false;

        @Option("do_gamer.simple_healing.config.min_shield")
        @Percentage
        public double MIN_SHIELD = 0.5;
    }
}
