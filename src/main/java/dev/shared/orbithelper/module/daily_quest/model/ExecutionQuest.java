package dev.shared.orbithelper.module.daily_quest.model;

import java.util.List;

@SuppressWarnings("java:S1104")
public class ExecutionQuest {

    public String type; // KILL_NPC or COLLECT
    public String map;
    public String targetNpc;
    public String targetOre;
    public int questId;
    public String questTitle;
    public double remaining;
    public boolean active;
    public String requirementUuid; // Links to specific QuestRequirement.uuid
    public List<String> validMaps; // Used during planning only, not persisted

    @Override
    public String toString() {
        String target = "Enemy Players";

        if (targetNpc != null) {
            target = targetNpc;
        } else if (targetOre != null) {
            target = targetOre;
        }

        return type + " | " + target + " | Map: " + map + " | " + remaining + " left";
    }
}