package dev.shared.do_gamer.config;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

public class FixPetStuckConfig {

    @Option("general.enabled")
    public boolean enabled = true;

    @Option("do_gamer.fix_pet_stuck.stuck_seconds")
    @Number(min = 10, max = 300, step = 10)
    public int stuckSeconds = 60;

    @Option("do_gamer.fix_pet_stuck.force_reload")
    public ForceReloadConfig forceReload = new ForceReloadConfig();

    public static class ForceReloadConfig {
        @Option("do_gamer.fix_pet_stuck.force_reload.in_gg")
        public boolean inGG = false;

        @Option("do_gamer.fix_pet_stuck.force_reload.while_attacking")
        public boolean whileAttacking = false;

        @Option("do_gamer.fix_pet_stuck.force_reload.under_attack")
        public boolean underAttack = false;
    }
}
