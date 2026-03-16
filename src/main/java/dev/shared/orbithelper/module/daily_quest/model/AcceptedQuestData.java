package dev.shared.orbithelper.module.daily_quest.model;

import java.util.List;

@SuppressWarnings("java:S1104")
public class AcceptedQuestData {
    public int id;
    public String title;
    public String type;
    public boolean completed;
    public boolean isSequential;
    public List<String> rewards;
    public List<String> requirements; // raw log strings
    public List<QuestRequirement> parsedRequirements; // structured data
    public boolean hasAvoidDeath = false;
}