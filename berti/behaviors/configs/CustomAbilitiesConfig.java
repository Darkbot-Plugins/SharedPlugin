package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.game.items.SelectableItem;

@Configuration(value="config.custom_abilities")
public class CustomAbilitiesConfig {
    @Option("config.custom_abilities.ability1")
    public Ability ABILITY1 = new Ability();
    @Option("config.custom_abilities.ability2")
    public Ability ABILITY2 = new Ability();
    @Option("config.custom_abilities.ability3")
    public Ability ABILITY3 = new Ability();
    @Option("config.custom_abilities.ability4")
    public Ability ABILITY4 = new Ability();
    @Option("config.custom_abilities.group_ability")
    public GroupAbility GROUP_ABILITY = new GroupAbility();

    @Configuration(value="config.ability_flags")
    public static enum AbilityFlags {
        @Option("config.ability_flags.ability1")
        ABILITY1,
        @Option("config.ability_flags.ability2")
        ABILITY2,
        @Option("config.ability_flags.ability3")
        ABILITY3,
        @Option("config.ability_flags.ability4")
        ABILITY4;
    }

    @Configuration(value="config.group_ability")
    public static class GroupAbility {
        @Option("config.group_ability.activated")
        public boolean ACTIVATED = false;
        @Option("config.group_ability.group_heal")
        @Percentage
        public double GROUP_HEAL = 0.75;
    }

    @Configuration(value="config.custom_abilities")
    public static class Ability {
        @Option("config.custom_abilities.activated")
        public boolean ACTIVATED = false;
        @Option("config.custom_abilities.npc_flag")
        public boolean NPC_FLAG = false;
        @Option("config.custom_abilities.abilities")
        @Dropdown
        public SelectableItem.Ability ABILITIES;
        @Option("config.custom_abilities.condition")
        public Condition CONDITION;
    }
}
