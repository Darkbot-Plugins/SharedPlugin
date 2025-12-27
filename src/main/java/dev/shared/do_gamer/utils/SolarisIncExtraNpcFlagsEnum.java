package dev.shared.do_gamer.utils;

import com.github.manolo8.darkbot.config.NpcExtraFlag;

public enum SolarisIncExtraNpcFlagsEnum implements NpcExtraFlag {
    SOLARIS_ABILITY("SA", "Solaris Ability", "NPC that can trigger Solaris ability");

    private final String shortName;
    private final String name;
    private final String description;

    SolarisIncExtraNpcFlagsEnum(String shortName, String name, String description) {
        this.shortName = shortName;
        this.name = name;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getShortName() {
        return shortName;
    }
}
