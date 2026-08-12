package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DailyTaskPlannerTest {
    private static final double DAY_MS = 86_400_000d;

    @Test
    void identifiesDailyQuestsFromTheSourceTimer() {
        QuestAPI.Requirement timer = requirement("Complete within one day", "REAL_TIME_HASTE",
                QuestAPI.Requirement.RequirementType.REAL_TIME_HASTE, 10, DAY_MS, false);
        QuestAPI.Requirement kill = requirement("Destroy Lordakium", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 2, 5, false);

        assertTrue(DailyTaskPlanner.isDaily(quest(1, true, false, List.of(kill, timer))));
        assertTrue(DailyTaskPlanner.isDaily(quest(2, false, false, List.of(kill, timer))));
        assertFalse(DailyTaskPlanner.isDaily(quest(3, true, false, List.of(kill))));
        assertTrue(DailyTaskPlanner.isDailyType("questType_daily1"));
        assertFalse(DailyTaskPlanner.isDailyType("questType_kill"));
    }

    @Test
    void retainsNestedNpcAndMapRequirements() {
        QuestAPI.Requirement map = requirement("On map 1-4", "MAP",
                QuestAPI.Requirement.RequirementType.MAP, 0, 0, false);
        QuestAPI.Requirement npc = requirement("Destroy Mordon", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 0, 8, false, List.of(map));
        QuestAPI.Quest daily = quest(4, true, false, List.of(npc, dailyTimer()));

        assertTrue(DailyTaskPlanner.actionable(daily).contains(npc));
        assertTrue(DailyTaskPlanner.actionable(daily).contains(map));

        DailyQuestConditionEngine.Plan plan = DailyQuestConditionEngine.build(daily, "1");
        assertEquals("1-4", plan.targetMapName());
        assertTrue(plan.targetNpcDescription().contains("Mordon"));
    }

    @Test
    void parsesCoordinatesAndTypedMapVisits() {
        QuestAPI.Requirement coordinates = requirement("X: 12400 Y: 7600", "COORDINATES",
                QuestAPI.Requirement.RequirementType.COORDINATES, 0, 1, false);
        DailyQuestConditionEngine.Plan coordinatePlan = DailyQuestConditionEngine.build(
                quest(5, true, false, List.of(coordinates, dailyTimer())), "1");
        assertEquals(12400, coordinatePlan.targetCoordinates().x());
        assertEquals(7600, coordinatePlan.targetCoordinates().y());
        assertTrue(coordinatePlan.coordinatesOnly());

        QuestAPI.Requirement visit = requirement("Visit map 1-8", "VISIT_MAP",
                QuestAPI.Requirement.RequirementType.VISIT_MAP, 0, 1, false);
        QuestAPI.Quest visitQuest = quest(6, true, false, List.of(visit, dailyTimer()));
        assertEquals("1-8", DailyQuestConditionEngine.build(visitQuest, "1").targetMapName());
        assertTrue(DailyQuestConditionEngine.supports(visitQuest));
    }

    @Test
    void matchesNpcVariantsAndPreferredMaps() {
        List<String> names = List.of("-=[ Lordakium ]=-", "..::{ Boss Lordakium }::..", "( UberLordakium )");
        assertEquals("-=[ Lordakium ]=-", DailyTaskPlanner.findNpc("Destroy Lordakium", names).orElseThrow());
        assertTrue(DailyTaskPlanner.findNpc("Destroy Boss Lordakium", names).orElseThrow()
                .contains("Boss Lordakium"));
        assertTrue(DailyTaskPlanner.findNpc("Destroy Boss Sibelonit", List.of("-=[ Sibelonit ]=-")).isEmpty());
        assertTrue(DailyTaskPlanner.findNpc("Destroy UberLordakium",
                List.of("-=[ Lordakium ]=-", "..::{ Boss Lordakium }::..")).isEmpty());
        assertEquals("1-3", DailyTaskPlanner.preferredMapForNpc("Destroy Saimon", "1").orElseThrow());
        assertEquals("1-6", DailyTaskPlanner.preferredMapForNpc("Destroy Lordakium", "1").orElseThrow());
        assertEquals("1-5", DailyTaskPlanner.preferredMapForNpc("Destroy Boss Lordakium", "1").orElseThrow());
        assertEquals("4-5", DailyTaskPlanner.preferredMapForNpc("Destroy UberKristallin", "1").orElseThrow());
        assertEquals("1-8", DailyTaskPlanner.preferredMapForNpc("Destroy StreuneR", "1").orElseThrow());
        assertEquals(OreAPI.Ore.PROMETIUM, DailyTaskPlanner.findOre("Sell Prometium").orElseThrow());
    }

    @Test
    void acceptsOnlyValidMapNames() {
        assertEquals("1-6", DailyTaskPlanner.findMap("Travel to 1-6").orElseThrow());
        assertEquals("4-5", DailyTaskPlanner.findMap("Travel to 4-5").orElseThrow());
        assertEquals("5-3", DailyTaskPlanner.findMap("Travel to 5-3").orElseThrow());
        assertTrue(DailyTaskPlanner.findMap("Travel to 4-8").isEmpty());
        assertTrue(DailyTaskPlanner.findMap("Travel to 5-8").isEmpty());
    }

    @Test
    void filtersOffersBySourceRewardTypes() {
        assertTrue(DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                reward("currency_experience", 1000), reward("currency_credits", 1000),
                reward("resource_tetrathrin", 10))));
        assertFalse(DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                reward("resource_tetrathrin", 10), reward("ammunition_laser_ucb-100", 5000))));
        assertFalse(DailyTaskPlanner.isOnlyTetrathrinReward(List.of(
                reward("resource_tetrathrin", 10), reward("currency_uridium", 1500))));
        assertFalse(DailyTaskPlanner.isOnlyTetrathrinReward(List.of(reward("currency_experience", 1000))));
        assertTrue(DailyTaskPlanner.hasUridiumReward(List.of(reward("currency_uridium", 1500))));
        assertFalse(DailyTaskPlanner.hasUridiumReward(List.of(reward("resource_tetrathrin", 10))));
    }

    @Test
    void calculatesProgressWithoutTimerRequirements() {
        QuestAPI.Requirement partial = requirement("Destroy NPC", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 2, 5, false);
        QuestAPI.Requirement timer = dailyTimer();
        assertEquals(0.4d, DailyTaskPlanner.progress(
                quest(7, true, false, List.of(partial, timer))), 0.000001d);
    }

    @Test
    void handlesZeroGoalsCompletedRequirementsAndClamping() {
        QuestAPI.Requirement incompleteZero = requirement("Visit target", "MAP",
                QuestAPI.Requirement.RequirementType.MAP, 0, 0, false);
        QuestAPI.Requirement completedZero = requirement("Visit target", "MAP",
                QuestAPI.Requirement.RequirementType.MAP, 0, 0, true);
        QuestAPI.Requirement overGoal = requirement("Destroy NPC", "KILL_NPC",
                QuestAPI.Requirement.RequirementType.KILL_NPC, 8, 5, false);
        assertEquals(0d, DailyTaskPlanner.progress(quest(8, true, false, List.of(incompleteZero))), 0d);
        assertEquals(1d, DailyTaskPlanner.progress(quest(9, true, true, List.of(completedZero))), 0d);
        assertEquals(1d, DailyTaskPlanner.progress(quest(10, true, false, List.of(overGoal))), 0d);
    }

    private static QuestAPI.Requirement dailyTimer() {
        return requirement("Complete within one day", "REAL_TIME_HASTE",
                QuestAPI.Requirement.RequirementType.REAL_TIME_HASTE, 0, DAY_MS, false);
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

    private static QuestAPI.Quest quest(int id, boolean active, boolean completed,
                                        List<? extends QuestAPI.Requirement> requirements) {
        return new QuestAPI.Quest() {
            public int getId() { return id; }
            public boolean isActive() { return active; }
            public String getTitle() { return "Quest " + id; }
            public String getDescription() { return ""; }
            public boolean isCompleted() { return completed; }
            public List<? extends QuestAPI.Requirement> getRequirements() { return requirements; }
            public List<? extends QuestAPI.Reward> getRewards() { return List.of(); }
        };
    }
}
