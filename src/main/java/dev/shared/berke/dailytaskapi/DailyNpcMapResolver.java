package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.StarSystemAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves NPC maps from DarkBot's own NPC metadata instead of a static list. */
final class DailyNpcMapResolver {
    private final ConfigSetting<Map<String, NpcInfo>> npcInfos;
    private final StarSystemAPI starSystem;

    DailyNpcMapResolver(ConfigSetting<Map<String, NpcInfo>> npcInfos, StarSystemAPI starSystem) {
        this.npcInfos = npcInfos;
        this.starSystem = starSystem;
    }

    Optional<String> resolve(String description, String companyPrefix) {
        try {
            Map<String, NpcInfo> configured = npcInfos.getValue();
            if (configured == null || configured.isEmpty()) return Optional.empty();
            return resolve(description, companyPrefix, new ArrayList<>(configured.values()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> resolve(String description, String companyPrefix, List<NpcInfo> configured) {
        Optional<NpcInfo> target = configured.stream()
                .filter(info -> info != null && DailyTaskPlanner.matchesNpcName(description, info.getName()))
                .max(Comparator.comparingInt(info ->
                        DailyTaskPlanner.normalizeNpcName(info.getName()).length()));
        if (target.isEmpty()) return Optional.empty();

        Collection<Integer> mapIds = target.get().getMapIds();
        if (mapIds == null || mapIds.isEmpty()) return Optional.empty();

        GameMap current = starSystem.getCurrentMap();
        if (current != null && mapIds.contains(current.getId()) && starSystem.isAccessible(current)) {
            return Optional.of(current.getName());
        }

        String prefix = companyPrefix != null && companyPrefix.matches("[1-3]")
                ? companyPrefix + "-"
                : "";
        return mapIds.stream()
                .map(starSystem::findMap)
                .flatMap(Optional::stream)
                .filter(starSystem::isAccessible)
                .filter(map -> !map.isGG())
                .min(Comparator
                        .comparingInt((GameMap map) -> map.getName().startsWith(prefix) ? 0 : 1)
                        .thenComparing(GameMap::getName))
                .map(GameMap::getName);
    }
}
