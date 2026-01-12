package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.types.Condition;

@Configuration(value="simple_pet")
public class SimplePetConfig {
    @Option("simple_pet.activated")
    public boolean ACTIVATED = false;
    @Option("simple_pet.pet_anti_push")
    public boolean PET_ANTI_PUSH = false;
    @Option("simple_pet.npc_flag")
    public boolean NPC_FLAG = false;
    @Option("simple_pet.combo_repair")
    public boolean COMBO_REPAIR = false;
    @Option("simple_pet.pet_link_hp")
    @Percentage
    public double PET_LINK_HP = 0.0;
    @Option("simple_pet.condition")
    public Condition CONDITION;

    @Configuration(value="simple_pet.pet_link_flag")
    public static enum ItemFlags {
        @Option("simple_pet.pet_link_flag.hp_link_flag")
        HP_LINK_FLAG;
    }
}
