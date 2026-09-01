package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an executable daily-quest plan from DarkBot's typed QuestAPI source.
 *
 * <p>Quest discovery stays in {@link DailyTaskAPI}, while this class translates
 * enabled, unfinished requirements into map, coordinate, NPC, collection and
 * ore actions. It has no dependency on DmPlugin and does not alter another
 * module's saved configuration.</p>
 */
final class DailyQuestConditionEngine {
    private static final Pattern X_COORDINATE = Pattern.compile(
            "\\bx(?:-koordinat[i\\x{0131}]?)?\\s*+(?:[:=]\\s*+)?(-?\\d{1,5}+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern Y_COORDINATE = Pattern.compile(
            "\\by(?:-koordinat[i\\x{0131}]?)?\\s*+(?:[:=]\\s*+)?(-?\\d{1,5}+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAIRED_COORDINATES = Pattern.compile(
            "(?<![\\d-])(-?\\d{2,5})\\s*[/,;|:]\\s*(-?\\d{2,5})(?![\\d-])");

    enum StepType {
        MAP, COORDINATES, NPC_COMBAT, PLAYER_COMBAT, SELL_ORE, COLLECT_BONUS, COLLECT_CARGO
    }

    static final class Step {
        private final StepType type;
        private final String description;

        Step(StepType type, String description) {
            this.type = type;
            this.description = description;
        }

        StepType type() {
            return type;
        }

        String description() {
            return description;
        }
    }

    static final class Plan {
        private final String targetMapName;
        private final String targetNpcDescription;
        private final String targetPlayerDescription;
        private final OreAPI.Ore oreToSell;
        private final boolean collectBonus;
        private final boolean collectCargo;
        private final Locatable targetCoordinates;
        private final String primaryDescription;
        private final List<Step> steps;

        Plan(String targetMapName, ActiveObjective objective,
             String primaryDescription, List<Step> steps) {
            this.targetMapName = targetMapName;
            this.targetNpcDescription = objective.targetNpcDescription;
            this.targetPlayerDescription = objective.targetPlayerDescription;
            this.oreToSell = objective.oreToSell;
            this.collectBonus = objective.collectBonus;
            this.collectCargo = objective.collectCargo;
            this.targetCoordinates = objective.targetCoordinates;
            this.primaryDescription = primaryDescription;
            this.steps = List.copyOf(steps);
        }

        String targetMapName() {
            return targetMapName;
        }

        String targetNpcDescription() {
            return targetNpcDescription;
        }

        String targetPlayerDescription() {
            return targetPlayerDescription;
        }

        OreAPI.Ore oreToSell() {
            return oreToSell;
        }

        boolean collectBonus() {
            return collectBonus;
        }

        boolean collectCargo() {
            return collectCargo;
        }

        Locatable targetCoordinates() {
            return targetCoordinates;
        }

        String primaryDescription() {
            return primaryDescription;
        }

        List<Step> steps() {
            return steps;
        }

        int priorityScore() {
            if (targetPlayerDescription != null) return 4_000;
            if (oreToSell != null) return 3_000;
            if (collectBonus || collectCargo) return 2_000;
            if (targetNpcDescription != null) return 1_000;
            return 0;
        }

        boolean executable() {
            return targetMapName != null || targetNpcDescription != null || targetPlayerDescription != null ||
                    oreToSell != null ||
                    collectBonus || collectCargo || targetCoordinates != null;
        }

        boolean coordinatesOnly() {
            return targetCoordinates != null && targetNpcDescription == null && targetPlayerDescription == null &&
                    oreToSell == null &&
                    !collectBonus && !collectCargo;
        }
    }

    private static final class ActiveObjective {
        private final String targetNpcDescription;
        private final String targetPlayerDescription;
        private final OreAPI.Ore oreToSell;
        private final boolean collectBonus;
        private final boolean collectCargo;
        private final Locatable targetCoordinates;

        private ActiveObjective(String targetNpcDescription, String targetPlayerDescription,
                                OreAPI.Ore oreToSell, boolean collectBonus, boolean collectCargo,
                                Locatable targetCoordinates) {
            this.targetNpcDescription = targetNpcDescription;
            this.targetPlayerDescription = targetPlayerDescription;
            this.oreToSell = oreToSell;
            this.collectBonus = collectBonus;
            this.collectCargo = collectCargo;
            this.targetCoordinates = targetCoordinates;
        }
    }

    private DailyQuestConditionEngine() {
    }

    static Plan build(QuestAPI.Quest quest) {
        return build(quest, ignored -> Optional.empty());
    }

    static Plan build(QuestAPI.Quest quest, Function<String, Optional<String>> npcMapResolver) {
        List<QuestAPI.Requirement> requirements = DailyTaskPlanner.actionable(quest);
        List<Step> steps = buildSteps(requirements);

        String targetMap = requirements.stream()
                .filter(DailyQuestConditionEngine::isMapCondition)
                .map(QuestAPI.Requirement::getDescription)
                .map(DailyTaskPlanner::findMap)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> requirements.stream()
                        .map(QuestAPI.Requirement::getDescription)
                        .map(DailyTaskPlanner::findMap)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .orElse(null));

        String targetNpc = requirements.stream()
                .filter(DailyQuestConditionEngine::isNpcCondition)
                .map(QuestAPI.Requirement::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .findFirst()
                .orElse(null);
        String targetPlayer = requirements.stream()
                .filter(DailyQuestConditionEngine::isPlayerCondition)
                .map(QuestAPI.Requirement::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .findFirst()
                .orElse(null);
        if (targetMap == null && targetNpc != null) {
            targetMap = npcMapResolver.apply(targetNpc).orElse(null);
        }

        OreAPI.Ore ore = requirements.stream()
                .filter(requirement -> requirement.getRequirementType() ==
                        QuestAPI.Requirement.RequirementType.SELL_ORE)
                .map(QuestAPI.Requirement::getDescription)
                .map(DailyTaskPlanner::findOre)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);

        boolean collectBonus = requirements.stream().anyMatch(DailyQuestConditionEngine::isBonusCondition);
        boolean collectCargo = requirements.stream().anyMatch(DailyQuestConditionEngine::isCargoCondition);

        Locatable coordinates = requirements.stream()
                .filter(DailyQuestConditionEngine::isCoordinateCondition)
                .map(QuestAPI.Requirement::getDescription)
                .map(DailyQuestConditionEngine::findCoordinates)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);

        StepType activeType = steps.stream()
                .map(Step::type)
                .filter(type -> type != StepType.MAP)
                .findFirst()
                .orElse(StepType.MAP);
        if (activeType != StepType.NPC_COMBAT) targetNpc = null;
        if (activeType != StepType.PLAYER_COMBAT) targetPlayer = null;
        if (activeType != StepType.SELL_ORE) ore = null;
        if (activeType != StepType.COLLECT_BONUS) collectBonus = false;
        if (activeType != StepType.COLLECT_CARGO) collectCargo = false;
        if (activeType != StepType.COORDINATES) coordinates = null;

        String primary = requirements.stream()
                .filter(DailyQuestConditionEngine::isExecutableCondition)
                .map(QuestAPI.Requirement::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .findFirst()
                .orElseGet(() -> requirements.stream()
                        .map(QuestAPI.Requirement::getDescription)
                        .filter(description -> description != null && !description.isBlank())
                        .findFirst()
                        .orElse("etkin hedef yok"));

        ActiveObjective objective = new ActiveObjective(targetNpc, targetPlayer, ore,
                collectBonus, collectCargo, coordinates);
        return new Plan(targetMap, objective, primary, steps);
    }

    static boolean supports(QuestAPI.Quest quest) {
        return supports(quest, false);
    }

    static boolean supports(QuestAPI.Quest quest, boolean allowPlayerCombat) {
        List<QuestAPI.Requirement> requirements = DailyTaskPlanner.actionable(quest);
        if (requirements.isEmpty()) return false;
        for (QuestAPI.Requirement requirement : requirements) {
            if (isPlayerCondition(requirement) && !allowPlayerCombat) return false;
            if (!isExecutableCondition(requirement)) return false;
        }
        return build(quest).executable();
    }

    static boolean hasPlayerCombat(QuestAPI.Quest quest) {
        return DailyTaskPlanner.actionable(quest).stream()
                .anyMatch(DailyQuestConditionEngine::isPlayerCondition);
    }

    static boolean isNpcCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.KILL_NPC ||
                type == QuestAPI.Requirement.RequirementType.KILL_NPCS ||
                type == QuestAPI.Requirement.RequirementType.DAMAGE_NPCS ||
                type == QuestAPI.Requirement.RequirementType.DAMAGE ||
                type == QuestAPI.Requirement.RequirementType.RESTRICT_AMMUNITION_KILL_NPC;
    }

    static boolean isPlayerCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.KILL_PLAYERS ||
                type == QuestAPI.Requirement.RequirementType.DAMAGE_PLAYERS ||
                type == QuestAPI.Requirement.RequirementType.DAMAGE_ENEMY_PLAYERS ||
                type == QuestAPI.Requirement.RequirementType.RESTRICT_AMMUNITION_KILL_PLAYER;
    }

    private static boolean isMapCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.MAP ||
                type == QuestAPI.Requirement.RequirementType.MAP_DIVERSE ||
                type == QuestAPI.Requirement.RequirementType.TRAVEL ||
                type == QuestAPI.Requirement.RequirementType.VISIT_MAP ||
                type == QuestAPI.Requirement.RequirementType.VISIT_MULTIPLE_MAPS ||
                type == QuestAPI.Requirement.RequirementType.VISIT_JUMP_GATE_TO_MAP_TYPE ||
                type == QuestAPI.Requirement.RequirementType.VISIT_MAP_ASSET ||
                type == QuestAPI.Requirement.RequirementType.VISIT_DISRUPTION_ZONE;
    }

    private static boolean isCoordinateCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.COORDINATES ||
                type == QuestAPI.Requirement.RequirementType.PROXIMITY ||
                type == QuestAPI.Requirement.RequirementType.DISTANCE;
    }

    private static boolean isBonusCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.COLLECT_BONUS_BOX ||
                type == QuestAPI.Requirement.RequirementType.COLLECT_BONUS_BOX_TYPE;
    }

    private static boolean isCargoCondition(QuestAPI.Requirement requirement) {
        if (requirement == null) return false;
        QuestAPI.Requirement.RequirementType type = requirement.getRequirementType();
        return type == QuestAPI.Requirement.RequirementType.SALVAGE ||
                type == QuestAPI.Requirement.RequirementType.COLLECT_LOOT ||
                type == QuestAPI.Requirement.RequirementType.CARGO ||
                type == QuestAPI.Requirement.RequirementType.COLLECT;
    }

    private static boolean isExecutableCondition(QuestAPI.Requirement requirement) {
        return isNpcCondition(requirement) || isPlayerCondition(requirement) || isMapCondition(requirement) ||
                isCoordinateCondition(requirement) || isBonusCondition(requirement) ||
                isCargoCondition(requirement) ||
                (requirement != null && requirement.getRequirementType() ==
                        QuestAPI.Requirement.RequirementType.SELL_ORE);
    }

    private static List<Step> buildSteps(List<QuestAPI.Requirement> requirements) {
        List<Step> steps = new ArrayList<>();
        for (QuestAPI.Requirement requirement : requirements) {
            StepType type = stepType(requirement);
            if (type != null) steps.add(new Step(type, requirement.getDescription()));
        }
        return steps;
    }

    private static StepType stepType(QuestAPI.Requirement requirement) {
        if (isPlayerCondition(requirement)) return StepType.PLAYER_COMBAT;
        if (isNpcCondition(requirement)) return StepType.NPC_COMBAT;
        if (isMapCondition(requirement)) return StepType.MAP;
        if (isCoordinateCondition(requirement)) return StepType.COORDINATES;
        if (isBonusCondition(requirement)) return StepType.COLLECT_BONUS;
        if (isCargoCondition(requirement)) return StepType.COLLECT_CARGO;
        if (requirement != null && requirement.getRequirementType() ==
                QuestAPI.Requirement.RequirementType.SELL_ORE) return StepType.SELL_ORE;
        return null;
    }

    static Optional<Locatable> findCoordinates(String description) {
        if (description == null || description.isBlank()) return Optional.empty();
        String normalized = description.toLowerCase(Locale.ROOT);
        Matcher labeledX = X_COORDINATE.matcher(normalized);
        Matcher labeledY = Y_COORDINATE.matcher(normalized);
        if (labeledX.find() && labeledY.find()) {
            return coordinates(labeledX.group(1), labeledY.group(1));
        }
        Matcher paired = PAIRED_COORDINATES.matcher(normalized);
        if (paired.find()) return coordinates(paired.group(1), paired.group(2));
        return Optional.empty();
    }

    private static Optional<Locatable> coordinates(String rawX, String rawY) {
        try {
            double x = Double.parseDouble(rawX);
            double y = Double.parseDouble(rawY);
            if (!Double.isFinite(x) || !Double.isFinite(y)) return Optional.empty();
            return Optional.of(Locatable.of(x, y));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
