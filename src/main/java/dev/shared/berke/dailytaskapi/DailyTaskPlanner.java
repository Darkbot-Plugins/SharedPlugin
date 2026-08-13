package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DailyTaskPlanner {
    private static final Pattern MAP_PATTERN = Pattern.compile("(?i)([1-3]-BL|[1-3]-[1-8]|4-[1-5]|5-[1-3])");
    private static final double DAILY_DURATION_MS = 86_400_000d;

    private DailyTaskPlanner() {
    }

    static boolean isDaily(QuestAPI.Quest quest) {
        if (quest == null) return false;
        for (QuestAPI.Requirement requirement : flatten(quest.getRequirements())) {
            if (requirement.getRequirementType() == QuestAPI.Requirement.RequirementType.REAL_TIME_HASTE) {
                double goal = requirement.getGoal();
                return goal >= DAILY_DURATION_MS - 120_000d && goal <= DAILY_DURATION_MS + 120_000d;
            }
        }
        return false;
    }

    static boolean isDailyType(String type) {
        if (type == null) return false;
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("questtype_daily") || normalized.equals("daily");
    }

    static boolean isOnlyTetrathrinReward(Collection<? extends QuestAPI.Reward> rewards) {
        if (rewards == null || rewards.isEmpty()) return false;
        boolean hasTetrathrin = false;
        for (QuestAPI.Reward reward : rewards) {
            if (isPositiveReward(reward)) {
                String type = normalize(reward.getType());
                if (type.contains("uridium")) return false;
                if (!type.startsWith("currency ")) {
                    if (!type.contains("tetrathrin")) return false;
                    hasTetrathrin = true;
                }
            }
        }
        return hasTetrathrin;
    }

    private static boolean isPositiveReward(QuestAPI.Reward reward) {
        return reward != null && reward.getAmount() > 0;
    }

    static boolean hasUridiumReward(Collection<? extends QuestAPI.Reward> rewards) {
        if (rewards == null || rewards.isEmpty()) return false;
        for (QuestAPI.Reward reward : rewards) {
            if (reward != null && reward.getAmount() > 0 && normalize(reward.getType()).contains("uridium")) {
                return true;
            }
        }
        return false;
    }

    static List<QuestAPI.Requirement> actionable(QuestAPI.Quest quest) {
        List<QuestAPI.Requirement> result = new ArrayList<>();
        if (quest == null) return result;
        for (QuestAPI.Requirement requirement : flatten(quest.getRequirements())) {
            if (isActionable(requirement, false)) result.add(requirement);
        }
        return result;
    }

    static List<QuestAPI.Requirement> flatten(Collection<? extends QuestAPI.Requirement> roots) {
        List<QuestAPI.Requirement> result = new ArrayList<>();
        if (roots == null) return result;
        for (QuestAPI.Requirement root : roots) flattenInto(root, result);
        return result;
    }

    private static void flattenInto(QuestAPI.Requirement requirement, List<QuestAPI.Requirement> output) {
        if (requirement == null) return;
        // A requirement may be both actionable itself and contain structural
        // children. DarkBot models quests such as "kill Mordon on 1-4" as a
        // KILL_NPC parent with a MAP child, so retaining leaves only loses the
        // NPC target and prevents both combat and the PET enemy locator.
        output.add(requirement);
        List<? extends QuestAPI.Requirement> children = requirement.getRequirements();
        if (children != null) {
            for (QuestAPI.Requirement child : children) flattenInto(child, output);
        }
    }

    static Optional<String> findNpc(String description, Collection<String> npcNames) {
        if (description == null || npcNames == null) return Optional.empty();
        String text = normalize(description);
        boolean wantsBoss = text.contains("boss");
        boolean wantsUber = text.contains("uber");
        return npcNames.stream()
                .map(name -> new NpcCandidate(name, normalizeNpcName(name)))
                .filter(candidate -> !candidate.clean.isBlank() && text.contains(candidate.clean))
                .filter(candidate -> wantsBoss == candidate.clean.startsWith("boss "))
                .filter(candidate -> wantsUber == candidate.clean.startsWith("uber"))
                .max(Comparator.comparingInt(candidate -> candidate.clean.length()))
                .map(candidate -> candidate.original);
    }

    static boolean matchesNpcName(String description, String npcName) {
        if (description == null || npcName == null) return false;
        String target = normalizeNpcName(description);
        String candidate = normalizeNpcName(npcName);
        boolean wantsBoss = target.contains("boss");
        boolean wantsUber = target.contains("uber");
        return !candidate.isBlank() && target.contains(candidate) &&
                wantsBoss == candidate.startsWith("boss ") &&
                wantsUber == candidate.startsWith("uber");
    }

    static Optional<String> findMap(String description) {
        if (description == null) return Optional.empty();
        Matcher matcher = MAP_PATTERN.matcher(description);
        return matcher.find() ? Optional.of(matcher.group(1).toUpperCase(Locale.ROOT)) : Optional.empty();
    }

    static Optional<String> preferredMapForNpc(String description, String companyPrefix) {
        if (description == null) return Optional.empty();
        String prefix = companyPrefix != null && companyPrefix.matches("[1-3]") ? companyPrefix : "1";
        String npc = normalizeNpcName(description);

        if (npc.contains("uber")) return Optional.of("4-5");
        if (description.contains("StreuneR")) return Optional.of(prefix + "-8");
        String homeMap = homeMapForNpc(npc, prefix);
        if (homeMap != null) return Optional.of(homeMap);
        return pirateMapForNpc(npc);
    }

    private static String homeMapForNpc(String npc, String prefix) {
        if (containsAny(npc, "protecgit", "protegit", "cubikon")) return prefix + "-6";
        if (npc.contains("boss kristallon")) return prefix + "-7";
        if (npc.contains("boss lordakium")) return prefix + "-5";
        if (containsAny(npc, "kristallin", "kristallon", "lordakium")) return prefix + "-6";
        if (npc.contains("sibelonit")) return prefix + "-5";
        if (npc.contains("sibelon")) return prefix + "-4";
        if (containsAny(npc, "saimon", "mordon", "devolarium")) return prefix + "-3";
        if (containsAny(npc, "lordakia", "streuner")) return prefix + "-2";
        return null;
    }

    private static Optional<String> pirateMapForNpc(String npc) {
        if (npc.contains("battleray")) return Optional.of("5-3");
        if (containsAny(npc, "interceptor", "barracuda", "saboteur", "annihilator")) {
            return Optional.of("5-2");
        }
        return Optional.empty();
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    static Optional<OreAPI.Ore> findOre(String description) {
        String text = normalize(description);
        if (text.contains("prometium")) return Optional.of(OreAPI.Ore.PROMETIUM);
        if (text.contains("endurium")) return Optional.of(OreAPI.Ore.ENDURIUM);
        if (text.contains("terbium")) return Optional.of(OreAPI.Ore.TERBIUM);
        if (text.contains("prometid")) return Optional.of(OreAPI.Ore.PROMETID);
        if (text.contains("duranium")) return Optional.of(OreAPI.Ore.DURANIUM);
        if (text.contains("promerium")) return Optional.of(OreAPI.Ore.PROMERIUM);
        if (text.contains("seprom")) return Optional.of(OreAPI.Ore.SEPROM);
        if (text.contains("palladium")) return Optional.of(OreAPI.Ore.PALLADIUM);
        if (text.contains("osmium")) return Optional.of(OreAPI.Ore.OSMIUM);
        if (text.contains("xenomit")) return Optional.of(OreAPI.Ore.XENOMIT);
        return Optional.empty();
    }

    static String normalizeNpcName(String value) {
        return normalize(value)
                .replace('-', ' ')
                .replaceAll("\\b(delta|alpha|beta|gamma)\\s*\\d+\\b", " ")
                .replaceAll("\\b[a-z]\\d+\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replaceAll("[^a-z0-9-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static double progress(QuestAPI.Quest quest) {
        List<QuestAPI.Requirement> requirements = actionableIncludingCompleted(quest);
        if (requirements.isEmpty()) return 0d;
        double sum = 0d;
        for (QuestAPI.Requirement requirement : requirements) {
            sum += requirementProgress(requirement);
        }
        return sum / requirements.size();
    }

    static double remainingWork(QuestAPI.Quest quest) {
        double remaining = 0d;
        for (QuestAPI.Requirement requirement : actionable(quest)) {
            double goal = Math.max(0d, requirement.getGoal());
            remaining += goal <= 0d ? 1d : Math.max(0d, goal - requirement.getProgress());
        }
        return remaining;
    }

    private static double requirementProgress(QuestAPI.Requirement requirement) {
        double goal = requirement.getGoal();
        if (goal <= 0d) return requirement.isCompleted() ? 1d : 0d;
        return Math.max(0d, Math.min(1d, requirement.getProgress() / goal));
    }

    private static List<QuestAPI.Requirement> actionableIncludingCompleted(QuestAPI.Quest quest) {
        List<QuestAPI.Requirement> result = new ArrayList<>();
        if (quest == null) return result;
        for (QuestAPI.Requirement requirement : flatten(quest.getRequirements())) {
            if (isActionable(requirement, true)) result.add(requirement);
        }
        return result;
    }

    private static boolean isActionable(QuestAPI.Requirement requirement, boolean includeCompleted) {
        if (requirement == null || !requirement.isEnabled()) return false;
        if (!includeCompleted && requirement.isCompleted()) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type != QuestAPI.Requirement.RequirementType.REAL_TIME_HASTE &&
                type != QuestAPI.Requirement.RequirementType.HASTE &&
                type != QuestAPI.Requirement.RequirementType.TIMER &&
                type != QuestAPI.Requirement.RequirementType.COUNTDOWN;
    }

    private static final class NpcCandidate {
        private final String original;
        private final String clean;

        private NpcCandidate(String original, String clean) {
            this.original = original;
            this.clean = clean;
        }
    }
}
