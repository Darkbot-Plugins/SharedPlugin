package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.QuestAPI;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
            "\\bx(?:-koordinat[iı]?)?\\s*[:=]?\\s*(-?\\d{1,5})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern Y_COORDINATE = Pattern.compile(
            "\\by(?:-koordinat[iı]?)?\\s*[:=]?\\s*(-?\\d{1,5})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAIRED_COORDINATES = Pattern.compile(
            "(?<![\\d-])(-?\\d{2,5})\\s*[/,;|:]\\s*(-?\\d{2,5})(?![\\d-])");

    static final class Plan {
        private final String targetMapName;
        private final String targetNpcDescription;
        private final OreAPI.Ore oreToSell;
        private final boolean collectBonus;
        private final boolean collectCargo;
        private final Locatable targetCoordinates;
        private final String primaryDescription;

        Plan(String targetMapName, String targetNpcDescription, OreAPI.Ore oreToSell,
             boolean collectBonus, boolean collectCargo, Locatable targetCoordinates,
             String primaryDescription) {
            this.targetMapName = targetMapName;
            this.targetNpcDescription = targetNpcDescription;
            this.oreToSell = oreToSell;
            this.collectBonus = collectBonus;
            this.collectCargo = collectCargo;
            this.targetCoordinates = targetCoordinates;
            this.primaryDescription = primaryDescription;
        }

        String targetMapName() {
            return targetMapName;
        }

        String targetNpcDescription() {
            return targetNpcDescription;
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

        boolean executable() {
            return targetMapName != null || targetNpcDescription != null || oreToSell != null ||
                    collectBonus || collectCargo || targetCoordinates != null;
        }

        boolean coordinatesOnly() {
            return targetCoordinates != null && targetNpcDescription == null && oreToSell == null &&
                    !collectBonus && !collectCargo;
        }
    }

    private DailyQuestConditionEngine() {
    }

    static Plan build(QuestAPI.Quest quest, String companyPrefix) {
        List<QuestAPI.Requirement> requirements = DailyTaskPlanner.actionable(quest);

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
        if (targetMap == null && targetNpc != null) {
            targetMap = DailyTaskPlanner.preferredMapForNpc(targetNpc, companyPrefix).orElse(null);
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

        return new Plan(targetMap, targetNpc, ore, collectBonus, collectCargo, coordinates, primary);
    }

    static boolean supports(QuestAPI.Quest quest) {
        return build(quest, "1").executable();
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
        return isNpcCondition(requirement) || isMapCondition(requirement) ||
                isCoordinateCondition(requirement) || isBonusCondition(requirement) ||
                isCargoCondition(requirement) ||
                (requirement != null && requirement.getRequirementType() ==
                        QuestAPI.Requirement.RequirementType.SELL_ORE);
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
