package dev.shared.do_gamer.module.simple_galaxy_gate.config;

import com.github.manolo8.darkbot.config.NpcExtraFlag;

public enum KamikazeNpcFlag implements NpcExtraFlag {
    KAMIKAZE("K", "Kamikaze", "Use Kamikaze for this NPC");

    private final String shortName;
    private final String name;
    private final String description;

    KamikazeNpcFlag(String shortName, String name, String description) {
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
