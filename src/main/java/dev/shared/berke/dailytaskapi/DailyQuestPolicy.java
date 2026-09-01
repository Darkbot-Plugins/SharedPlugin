package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.managers.QuestAPI;

import java.util.Collection;

/** Central acceptance and execution policy for daily quests. */
final class DailyQuestPolicy {
    enum Decision {
        ACCEPT("accepted"),
        WAIT_FOR_REWARDS("reward data is not ready"),
        SKIP_NO_URIDIUM("no Uridium reward"),
        SKIP_TETRATHRIN_ONLY("Tetrathrin-only reward"),
        SKIP_PROTEGIT("Protegit objective is disabled"),
        SKIP_PLAYER_COMBAT("player-combat objectives are disabled"),
        SKIP_UNSUPPORTED("unsupported objective");

        private final String message;

        Decision(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }

        boolean accepted() {
            return this == ACCEPT;
        }
    }

    private DailyQuestPolicy() {
    }

    static Decision evaluateOffer(QuestAPI.Quest quest, DailyTaskConfig config) {
        Decision objectiveDecision = evaluateObjectives(quest, config);
        if (!objectiveDecision.accepted()) return objectiveDecision;

        Collection<? extends QuestAPI.Reward> rewards = quest == null ? null : quest.getRewards();
        if (rewards == null || rewards.isEmpty()) return Decision.WAIT_FOR_REWARDS;
        if (config.skipTetrathrinOnly && DailyTaskPlanner.isOnlyTetrathrinReward(rewards)) {
            return Decision.SKIP_TETRATHRIN_ONLY;
        }
        if (config.requireUridiumReward && !DailyTaskPlanner.hasUridiumReward(rewards)) {
            return Decision.SKIP_NO_URIDIUM;
        }
        return Decision.ACCEPT;
    }

    static Decision evaluateActive(QuestAPI.Quest quest, DailyTaskConfig config) {
        return evaluateObjectives(quest, config);
    }

    static int priority(QuestAPI.Quest quest, DailyTaskConfig config) {
        DailyQuestConditionEngine.Plan plan = DailyQuestConditionEngine.build(quest);
        int score = plan.priorityScore();
        if (config.preferShorterQuests) {
            score += (int) Math.min(10_000d, Math.round(DailyTaskPlanner.remainingWork(quest)));
        }
        return score;
    }

    private static Decision evaluateObjectives(QuestAPI.Quest quest, DailyTaskConfig config) {
        if (quest == null) return Decision.SKIP_UNSUPPORTED;
        if (config.skipProtegitQuests && containsProtegit(quest)) return Decision.SKIP_PROTEGIT;
        if (!config.acceptPlayerKillQuests && DailyQuestConditionEngine.hasPlayerCombat(quest)) {
            return Decision.SKIP_PLAYER_COMBAT;
        }
        if (!DailyQuestConditionEngine.supports(quest, config.acceptPlayerKillQuests)) {
            return Decision.SKIP_UNSUPPORTED;
        }
        return Decision.ACCEPT;
    }

    private static boolean containsProtegit(QuestAPI.Quest quest) {
        return DailyTaskPlanner.actionable(quest).stream()
                .filter(DailyQuestConditionEngine::isNpcCondition)
                .map(QuestAPI.Requirement::getDescription)
                .map(DailyTaskPlanner::normalizeNpcName)
                .anyMatch(description -> description.contains("protegit"));
    }
}
