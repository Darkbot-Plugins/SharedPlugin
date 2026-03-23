package dev.shared.canda.remote.telemetry;

public class RemoteSnapshot {
    public long tick;
    public String botPublicId;
    public int heroId;
    public String username;
    public boolean botRunning;
    public String moduleStatus;
    public String moduleId;
    public int mapId;
    public String mapName;
    public double hpPercent;
    public double shieldPercent;

    public String toJson() {
        return "{"
                + "\"tick\":" + tick + ","
                + "\"botPublicId\":\"" + json(botPublicId) + "\","
                + "\"heroId\":" + heroId + ","
                + "\"username\":\"" + json(username) + "\","
                + "\"botRunning\":" + botRunning + ","
                + "\"moduleStatus\":\"" + json(moduleStatus) + "\","
                + "\"moduleId\":\"" + json(moduleId) + "\","
                + "\"mapId\":" + mapId + ","
                + "\"mapName\":\"" + json(mapName) + "\","
                + "\"hpPercent\":" + hpPercent + ","
                + "\"shieldPercent\":" + shieldPercent
                + "}";
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
