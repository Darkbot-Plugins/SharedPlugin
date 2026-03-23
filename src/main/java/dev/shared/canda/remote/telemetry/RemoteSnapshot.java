package dev.shared.canda.remote.telemetry;

public class RemoteSnapshot {
    private long tick;
    private String botPublicId;
    private int heroId;
    private String username;
    private boolean botRunning;
    private String moduleStatus;
    private String moduleId;
    private int mapId;
    private String mapName;
    private double hpPercent;
    private double shieldPercent;

    public void setTick(long tick) {
        this.tick = tick;
    }

    public void setBotPublicId(String botPublicId) {
        this.botPublicId = botPublicId;
    }

    public void setHeroId(int heroId) {
        this.heroId = heroId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setBotRunning(boolean botRunning) {
        this.botRunning = botRunning;
    }

    public void setModuleStatus(String moduleStatus) {
        this.moduleStatus = moduleStatus;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public void setHpPercent(double hpPercent) {
        this.hpPercent = hpPercent;
    }

    public void setShieldPercent(double shieldPercent) {
        this.shieldPercent = shieldPercent;
    }

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
