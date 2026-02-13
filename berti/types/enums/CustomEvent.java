package dev.shared.berti.types.enums;

import eu.darkbot.api.config.annotations.Configuration;

@Configuration(value="customevent")
public enum CustomEvent {
    DMG_BEACON("Damage_Beacon"),
    SPEARHEAD("Spearhead");

    private final String customEvent;

    private CustomEvent(String customEvent) {
        this.customEvent = customEvent;
    }

    public String toString() {
        return this.customEvent;
    }

    public String getName() {
        return this.customEvent;
    }
}
