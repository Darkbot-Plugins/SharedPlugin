package dev.shared.berti.types.enums;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Option;

@Configuration(value="waitmap")
public enum BLMaps {
    @Option("blmaps.one_bl")
    ONE_BL("1BL", 306),
    @Option("blmaps.two_bl")
    TWO_BL("2BL", 307),
    @Option("blmaps.three_bl")
    THREE_BL("3BL", 308);

    private final String waitMap;
    private final int mapId;

    private BLMaps(String waitMap, int mapId) {
        this.waitMap = waitMap;
        this.mapId = mapId;
    }

    public String toString() {
        return this.waitMap;
    }

    public String getName() {
        return this.waitMap;
    }

    public int getMapId() {
        return this.mapId;
    }
}
