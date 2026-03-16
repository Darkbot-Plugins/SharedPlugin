package dev.shared.orbithelper.module.daily_quest.model;

import java.util.List;

@SuppressWarnings("java:S1104")
public class QuestRequirement {
    public String description;
    public String type; // KILL_NPC, KILL_PLAYERS, COLLECT, SELL_ORE
    public String targetNpc; // Parsed NPC name
    public String targetOre; // Parsed Ore name
    public double progress;
    public double goal;
    public boolean completed;
    public boolean enabled;
    public String uuid; // Unique ID for tracking specific requirement instances
    public List<String> allowedMaps; // from L2 MAP sub-requirements
}