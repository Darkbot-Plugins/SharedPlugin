package dev.shared.do_gamer.module.simple_galaxy_gate.config;

import com.github.manolo8.darkbot.config.NpcExtraFlag;

public enum GateNpcFlag implements NpcExtraFlag {
    KAMIKAZE("K", "Kamikaze", "Use Kamikaze for this NPC"),
    STICK_TO_TARGET("ST", "Stick to Target", "Stick to current target, don't switch away");

    private final String shortName;
    private final String name;
    private final String description;

    GateNpcFlag(String shortName, String name, String description) {
        this.shortName = shortName;
        this.name = name;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getShortName() {
        return this.shortName;
    }
}
