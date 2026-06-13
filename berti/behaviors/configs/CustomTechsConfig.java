package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.game.items.SelectableItem;

@Configuration(value="config.custom_techs")
public class CustomTechsConfig {
    @Option("config.custom_techs.tech1")
    public Tech TECH1 = new Tech();
    @Option("config.custom_techs.tech2")
    public Tech TECH2 = new Tech();
    @Option("config.custom_techs.tech3")
    public Tech TECH3 = new Tech();
    @Option("config.custom_techs.tech4")
    public Tech TECH4 = new Tech();
    @Option("config.custom_techs.tech5")
    public Tech TECH5 = new Tech();
    @Option("config.custom_techs.tech6")
    public Tech TECH6 = new Tech();
    @Option("config.custom_techs.tech7")
    public Tech TECH7 = new Tech();
    @Option("config.custom_techs.tech8")
    public Tech TECH8 = new Tech();
    @Option("config.custom_techs.tech9")
    public Tech TECH9 = new Tech();
    @Option("config.custom_techs.tech10")
    public Tech TECH10 = new Tech();

    @Configuration(value="config.tech_flags")
    public static enum TechFlags {
        @Option("config.tech_flags.tech1")
        TECH1,
        @Option("config.tech_flags.tech2")
        TECH2,
        @Option("config.tech_flags.tech3")
        TECH3,
        @Option("config.tech_flags.tech4")
        TECH4,
        @Option("config.tech_flags.tech5")
        TECH5,
        @Option("config.tech_flags.tech6")
        TECH6,
        @Option("config.tech_flags.tech7")
        TECH7,
        @Option("config.tech_flags.tech8")
        TECH8,
        @Option("config.tech_flags.tech9")
        TECH9,
        @Option("config.tech_flags.tech10")
        TECH10;
    }

    @Configuration(value="config.custom_techs")
    public static class Tech {
        @Option("config.custom_techs.activated")
        public boolean ACTIVATED = false;
        @Option("config.custom_techs.npc_flag")
        public boolean NPC_FLAG = false;
        @Option("config.custom_techs.techs")
        @Dropdown
        public SelectableItem.Tech TECHS;
        @Option("config.custom_techs.condition")
        public Condition CONDITION;
    }
}
