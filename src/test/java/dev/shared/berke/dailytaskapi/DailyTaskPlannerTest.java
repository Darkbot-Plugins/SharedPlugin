package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;

import java.util.List;

public final class DailyTaskPlannerTest {
    public static void main(String[] args) {
        QuestAPI.Requirement dailyTimer = requirement("Bu görevi 1 gün içinde bitir", "REAL_TIME_HASTE",
                QuestAPI.Requirement.RequirementType.REAL_TIME_HASTE, 10, 86_400_000, false);
        QuestAPI.Requirement kill = requirement("Lordakium imha et.", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 2, 5, false);
        QuestAPI.Quest daily = quest(1, true, false, "Galaksi Politikaları", List.of(kill, dailyTimer));
        require(DailyTaskPlanner.isDaily(daily), "24 saatlik görev günlük tanınmalı");

        QuestAPI.Quest normal = quest(2, true, false, "Normal", List.of(kill));
        require(!DailyTaskPlanner.isDaily(normal), "süresiz görev günlük sayılmamalı");
        QuestAPI.Quest dailyOffer = quest(3, false, false, "Teklif", List.of(kill, dailyTimer));
        require(DailyTaskPlanner.isDaily(dailyOffer),
                "henüz aktif olmayan istasyon teklifi 24 saat şartından günlük tanınmalı");

        QuestAPI.Requirement mapChild = requirement("1-4 haritasında", "MAP",
                QuestAPI.Requirement.RequirementType.MAP, 0, 0, false);
        QuestAPI.Requirement nestedMordon = requirement("Mordon imha et.", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 0, 8, false, List.of(mapChild));
        QuestAPI.Quest nestedNpcQuest = quest(4, true, false, "Hedef: Biliniyor",
                List.of(nestedMordon, dailyTimer));
        require(DailyTaskPlanner.actionable(nestedNpcQuest).contains(nestedMordon),
                "altında harita şartı bulunan NPC öldürme hedefi kaybolmamalı");
        require(DailyTaskPlanner.actionable(nestedNpcQuest).contains(mapChild),
                "NPC hedefinin altındaki harita şartı da korunmalı");

        DailyQuestConditionEngine.Plan nestedPlan = DailyQuestConditionEngine.build(nestedNpcQuest, "1");
        require("1-4".equals(nestedPlan.targetMapName()),
                "Quest Engine must include a nested MAP condition");
        require(nestedPlan.targetNpcDescription().contains("Mordon"),
                "Quest Engine must include a nested NPC condition");

        QuestAPI.Requirement coordinates = requirement("X: 12400 Y: 7600", "COORDINATES",
                QuestAPI.Requirement.RequirementType.COORDINATES, 0, 1, false);
        QuestAPI.Quest coordinateQuest = quest(5, true, false, "Coordinates", List.of(coordinates, dailyTimer));
        DailyQuestConditionEngine.Plan coordinatePlan = DailyQuestConditionEngine.build(coordinateQuest, "1");
        require(coordinatePlan.targetCoordinates() != null &&
                        coordinatePlan.targetCoordinates().x() == 12400 &&
                        coordinatePlan.targetCoordinates().y() == 7600,
                "Quest Engine must parse coordinates from the source condition");
        require(coordinatePlan.coordinatesOnly(), "coordinate-only plans must be recognized");

        QuestAPI.Requirement visitMap = requirement("Visit map 1-8", "VISIT_MAP",
                QuestAPI.Requirement.RequirementType.VISIT_MAP, 0, 1, false);
        QuestAPI.Quest visitQuest = quest(6, true, false, "Map visit", List.of(visitMap, dailyTimer));
        require("1-8".equals(DailyQuestConditionEngine.build(visitQuest, "1").targetMapName()),
                "Quest Engine must support typed VISIT_MAP conditions");
        require(DailyQuestConditionEngine.supports(visitQuest),
                "a map visit must be considered executable");

        require(DailyTaskPlanner.isDailyType("questType_daily1"),
                "quest catalog daily type should be recognized");
        require(!DailyTaskPlanner.isDailyType("questType_kill"),
                "normal quest catalog type must not be daily");

        List<String> names = List.of("-=[ Lordakium ]=-", "..::{ Boss Lordakium }::..", "( UberLordakium )");
        require(DailyTaskPlanner.findNpc("Mordon imha et.", List.of("-=[ Mordon ]=-")).isPresent(),
                "decorated Mordon name should match the quest target");
        require(DailyTaskPlanner.findNpc("Lordakium imha et.", names).orElseThrow().equals("-=[ Lordakium ]=-"),
                "normal Lordakium seçilmeli");
        require(DailyTaskPlanner.findNpc("Boss Lordakium imha et.", names).orElseThrow().contains("Boss Lordakium"),
                "Boss Lordakium seçilmeli");
        require(DailyTaskPlanner.findNpc("Boss Sibelonit imha et.", List.of("-=[ Sibelonit ]=-")).isEmpty(),
                "Boss isteniyorsa normal NPC kesinlikle seçilmemeli");
        require(DailyTaskPlanner.findNpc("UberLordakium imha et.",
                        List.of("-=[ Lordakium ]=-", "..::{ Boss Lordakium }::..")).isEmpty(),
                "Uber isteniyorsa normal veya Boss NPC seçilmemeli");
        require(DailyTaskPlanner.findMap("1-6 haritasına git").orElseThrow().equals("1-6"),
                "harita ayrıştırılmalı");
        require(DailyTaskPlanner.preferredMapForNpc("Saimon imha et.", "1").orElseThrow().equals("1-3"),
                "Saimon MMO X-3 haritasına yönlendirilmeli");
        require(DailyTaskPlanner.preferredMapForNpc("Lordakium imha et.", "1").orElseThrow().equals("1-6"),
                "hesap sahibinin Lordakium X-6 tercihi korunmalı");
        require(DailyTaskPlanner.preferredMapForNpc("Boss Lordakium imha et.", "1").orElseThrow().equals("1-5"),
                "Boss Lordakium MMO X-5 haritasına yönlendirilmeli");
        require(DailyTaskPlanner.preferredMapForNpc("UberKristallin imha et.", "1").orElseThrow().equals("4-5"),
                "Uber NPC 4-5 haritasına yönlendirilmeli");
        require(DailyTaskPlanner.preferredMapForNpc("StreuneR imha et.", "1").orElseThrow().equals("1-8"),
                "StreuneR X-8 haritasına yönlendirilmeli");
        require(DailyTaskPlanner.findOre("Prometium sat.").orElseThrow() == OreAPI.Ore.PROMETIUM,
                "Prometium ayrıştırılmalı");
        require(DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                        reward("currency_experience", 1000), reward("currency_credits", 1000),
                        reward("resource_tetrathrin", 10))),
                "currency rewards plus only Tetrathrin must be skipped");
        require(!DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                        reward("resource_tetrathrin", 10), reward("ammunition_laser_ucb-100", 5000))),
                "a quest with Tetrathrin and another special reward must be accepted");
        require(!DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                        reward("resource_tetrathrin", 10), reward("currency_uridium", 1500))),
                "a quest containing Uridium must be accepted even with Tetrathrin");
        require(!DailyTaskPlanner.isOnlyTetrathrinReward(List.of(reward("currency_experience", 1000))),
                "currency-only rewards are not a Tetrathrin variant");
        require(DailyTaskPlanner.hasUridiumReward(List.of(
                        reward("currency_experience", 1000), reward("currency_uridium", 1500))),
                "Uridium reward must be detected from the source reward type");
        require(!DailyTaskPlanner.hasUridiumReward(List.of(reward("resource_tetrathrin", 10))),
                "a reward list without Uridium must be rejected");
        System.out.println("DailyTaskPlannerTest: OK");
    }

    private static QuestAPI.Reward reward(String type, int amount) {
        return new QuestAPI.Reward() {
            public int getAmount() { return amount; }
            public String getType() { return type; }
        };
    }

    private static QuestAPI.Requirement requirement(String description, String type,
                                                     QuestAPI.Requirement.RequirementType requirementType,
                                                     double progress, double goal, boolean completed) {
        return requirement(description, type, requirementType, progress, goal, completed, List.of());
    }

    private static QuestAPI.Requirement requirement(String description, String type,
                                                     QuestAPI.Requirement.RequirementType requirementType,
                                                     double progress, double goal, boolean completed,
                                                     List<? extends QuestAPI.Requirement> children) {
        return new QuestAPI.Requirement() {
            public String getDescription() { return description; }
            public double getProgress() { return progress; }
            public double getGoal() { return goal; }
            public boolean isCompleted() { return completed; }
            public String getType() { return type; }
            public List<? extends QuestAPI.Requirement> getRequirements() { return children; }
            public boolean isEnabled() { return true; }
            public QuestAPI.Requirement.RequirementType getRequirementType() { return requirementType; }
        };
    }

    private static QuestAPI.Quest quest(int id, boolean active, boolean completed, String title,
                                        List<? extends QuestAPI.Requirement> requirements) {
        return new QuestAPI.Quest() {
            public int getId() { return id; }
            public boolean isActive() { return active; }
            public String getTitle() { return title; }
            public String getDescription() { return ""; }
            public boolean isCompleted() { return completed; }
            public List<? extends QuestAPI.Requirement> getRequirements() { return requirements; }
            public List<? extends QuestAPI.Reward> getRewards() { return List.of(); }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
