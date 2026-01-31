package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.types.Condition;
import eu.darkbot.api.config.annotations.Option;

@Configuration(value="config.simple_item")
public class SimpleItemsConfig {
    @Option("config.simple_item.simpleitem1")
    public SimpleItem1 SIMPLEITEM1 = new SimpleItem1();
    @Option("config.simple_item.simpleitem2")
    public SimpleItem SIMPLEITEM2 = new SimpleItem();
    @Option("config.simple_item.simpleitem3")
    public SimpleItem SIMPLEITEM3 = new SimpleItem();

    @Configuration(value="config.simple_flags")
    public static enum ItemFlags {
        @Option("config.simple_flags.item2")
        ITEM2,
        @Option("config.simple_flags.item3")
        ITEM3;
    }

    @Configuration(value="config.simple_item")
    public static class SimpleItem {
        @Option("config.simple_item.activated")
        public boolean ACTIVATED = false;
        @Option("config.simple_item.npc_flag")
        public boolean NPC_FLAG = false;
        @Option("config.simple_item.condition")
        public Condition CONDITION;
    }

    @Configuration(value="config.simple_item")
    public static class SimpleItem1 {
        @Option("config.simple_item.activated")
        public boolean ACTIVATED = false;
    }
}
