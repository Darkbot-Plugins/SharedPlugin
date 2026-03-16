package dev.shared.orbithelper.module.daily_quest.handlers;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.other.EntityInfo.Faction;
import java.util.*;

public class TravelHandler {
    private final DailyQuestModule module;

    // Static Map Data
    private static final Map<String, List<String>> MAP_CONNECTIONS = new HashMap<>();
    private static final Map<String, Integer> MMO_LEVELS = Map.ofEntries(
            Map.entry("1-1", 1), Map.entry("1-2", 1), Map.entry("1-3", 2), Map.entry("1-4", 3),
            Map.entry("2-3", 5), Map.entry("2-4", 5), Map.entry("3-3", 5), Map.entry("3-4", 5),
            Map.entry("1-5", 10), Map.entry("1-6", 11), Map.entry("1-7", 11), Map.entry("1-8", 12),
            Map.entry("2-2", 13), Map.entry("3-2", 13), Map.entry("2-5", 13), Map.entry("3-5", 13),
            Map.entry("2-6", 14), Map.entry("2-7", 14), Map.entry("3-6", 14), Map.entry("3-7", 14),
            Map.entry("2-1", 15), Map.entry("3-1", 15));

    private static final Map<String, Integer> EIC_LEVELS = Map.ofEntries(
            Map.entry("2-1", 1), Map.entry("2-2", 1), Map.entry("2-3", 2), Map.entry("2-4", 3),
            Map.entry("3-3", 5), Map.entry("3-4", 5), Map.entry("1-3", 5), Map.entry("1-4", 5),
            Map.entry("2-5", 10), Map.entry("2-6", 11), Map.entry("2-7", 11), Map.entry("2-8", 12),
            Map.entry("3-2", 13), Map.entry("1-2", 13), Map.entry("3-5", 13), Map.entry("1-5", 13),
            Map.entry("3-6", 14), Map.entry("3-7", 14), Map.entry("1-6", 14), Map.entry("1-7", 14),
            Map.entry("3-1", 15), Map.entry("1-1", 15));

    private static final Map<String, Integer> VRU_LEVELS = Map.ofEntries(
            Map.entry("3-1", 1), Map.entry("3-2", 1), Map.entry("3-3", 2), Map.entry("3-4", 3),
            Map.entry("1-3", 5), Map.entry("1-4", 5), Map.entry("2-3", 5), Map.entry("2-4", 5),
            Map.entry("3-5", 10), Map.entry("3-6", 11), Map.entry("3-7", 11), Map.entry("3-8", 12),
            Map.entry("1-2", 13), Map.entry("2-2", 13), Map.entry("1-5", 13), Map.entry("2-5", 13),
            Map.entry("1-6", 14), Map.entry("1-7", 14), Map.entry("2-6", 14), Map.entry("2-7", 14),
            Map.entry("1-1", 15), Map.entry("2-1", 15));

    private static final Map<String, Integer> PVP_LEVELS = Map.ofEntries(
            Map.entry("4-1", 8), Map.entry("4-2", 8), Map.entry("4-3", 8),
            Map.entry("4-4", 9), Map.entry("4-5", 12));

    static {
        MAP_CONNECTIONS.put("1-1", List.of("1-2"));
        MAP_CONNECTIONS.put("1-2", List.of("1-1", "1-3", "1-4"));
        MAP_CONNECTIONS.put("1-3", List.of("1-2", "2-3", "1-4"));
        MAP_CONNECTIONS.put("1-4", List.of("1-2", "1-3", "4-1", "3-4"));
        MAP_CONNECTIONS.put("1-5", List.of("4-4", "1-6", "1-7", "4-5"));
        MAP_CONNECTIONS.put("1-6", List.of("1-5", "1-8"));
        MAP_CONNECTIONS.put("1-7", List.of("1-5", "1-8"));
        MAP_CONNECTIONS.put("1-8", List.of("1-6", "1-7"));

        MAP_CONNECTIONS.put("2-1", List.of("2-2"));
        MAP_CONNECTIONS.put("2-2", List.of("2-1", "2-3", "2-4"));
        MAP_CONNECTIONS.put("2-3", List.of("2-2", "2-4", "1-3"));
        MAP_CONNECTIONS.put("2-4", List.of("2-2", "2-3", "3-3", "4-2"));
        MAP_CONNECTIONS.put("2-5", List.of("4-4", "4-5", "2-6", "2-7"));
        MAP_CONNECTIONS.put("2-6", List.of("2-5", "2-8"));
        MAP_CONNECTIONS.put("2-7", List.of("2-5", "2-8"));
        MAP_CONNECTIONS.put("2-8", List.of("2-6", "2-7"));

        MAP_CONNECTIONS.put("3-1", List.of("3-2"));
        MAP_CONNECTIONS.put("3-2", List.of("3-1", "3-3", "3-4"));
        MAP_CONNECTIONS.put("3-3", List.of("3-2", "3-4", "2-4"));
        MAP_CONNECTIONS.put("3-4", List.of("3-2", "3-3", "4-3", "1-4"));
        MAP_CONNECTIONS.put("3-5", List.of("4-4", "4-5", "3-6", "3-7"));
        MAP_CONNECTIONS.put("3-6", List.of("3-5", "3-8"));
        MAP_CONNECTIONS.put("3-7", List.of("3-5", "3-8"));
        MAP_CONNECTIONS.put("3-8", List.of("3-7", "3-6"));

        MAP_CONNECTIONS.put("4-1", List.of("4-2", "4-3", "4-4", "1-4"));
        MAP_CONNECTIONS.put("4-2", List.of("4-1", "4-3", "4-4", "2-4"));
        MAP_CONNECTIONS.put("4-3", List.of("4-1", "4-2", "4-4", "3-4"));
        MAP_CONNECTIONS.put("4-4", List.of("4-1", "4-2", "4-3", "1-5", "2-5", "3-5"));
        MAP_CONNECTIONS.put("4-5", List.of("1-5", "2-5", "3-5", "5-1"));

        MAP_CONNECTIONS.put("5-1", List.of("5-2"));
        MAP_CONNECTIONS.put("5-2", List.of("5-3", "5-4"));
        MAP_CONNECTIONS.put("5-3", List.of("4-4"));
        MAP_CONNECTIONS.put("5-4", List.of("4-4"));
    }

    public static final Map<String, Integer> MAP_IDS = Map.ofEntries(
            Map.entry("1-1", 1), Map.entry("1-2", 2), Map.entry("1-3", 3), Map.entry("1-4", 4),
            Map.entry("1-5", 17), Map.entry("1-6", 18), Map.entry("1-7", 19), Map.entry("1-8", 20),
            Map.entry("2-1", 5), Map.entry("2-2", 6), Map.entry("2-3", 7), Map.entry("2-4", 8),
            Map.entry("2-5", 21), Map.entry("2-6", 22), Map.entry("2-7", 23), Map.entry("2-8", 24),
            Map.entry("3-1", 9), Map.entry("3-2", 10), Map.entry("3-3", 11), Map.entry("3-4", 12),
            Map.entry("3-5", 25), Map.entry("3-6", 26), Map.entry("3-7", 27), Map.entry("3-8", 28),
            Map.entry("4-1", 13), Map.entry("4-2", 14), Map.entry("4-3", 15), Map.entry("4-4", 16),
            Map.entry("4-5", 29));

    public TravelHandler(DailyQuestModule module) {
        this.module = module;
    }

    public void handleTravelingToQuestGiver() {
        if (module.targetQuestGiverMap == null) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        if (module.lastJumpTime > 0) {
            long elapsed = System.currentTimeMillis() - module.lastJumpTime;
            if (elapsed < DailyQuestModule.MAP_CHANGE_WAIT_MS) {
                return;
            }
            module.lastJumpTime = 0;
            module.lastMapBeforeJump = null;
        }

        String currentMap = getCurrentMap();

        if (currentMap.equals(module.targetQuestGiverMap)) {
            module.state = DailyQuestModule.State.NAVIGATING_TO_STATION;
            return;
        }

        moveToMap(module.targetQuestGiverMap);
    }

    public void handleNavigatingToStation() {
        Station.QuestGiver questGiver = module.entities.getStations().stream()
                .filter(s -> s instanceof Station.QuestGiver)
                .map(s -> (Station.QuestGiver) s)
                .findFirst()
                .orElse(null);

        if (questGiver == null) {
            module.state = DailyQuestModule.State.COMPLETE;
            return;
        }

        double distToQG = module.hero.distanceTo(questGiver);
        if (distToQG < 200) {
            module.state = DailyQuestModule.State.OPENING_QUEST_WINDOW;
        } else {
            module.movement.moveTo(questGiver);
        }
    }

    public void moveToMap(String targetMap) {
        if (targetMap == null)
            return;

        if (module.lastJumpTime > 0) {
            long elapsed = System.currentTimeMillis() - module.lastJumpTime;
            if (elapsed < DailyQuestModule.MAP_CHANGE_WAIT_MS) {
                return;
            }
            module.lastJumpTime = 0;
            module.lastMapBeforeJump = null;
        }

        String currentMap = getCurrentMap();
        if (currentMap.equals(targetMap))
            return;

        String nextMap = getNextMapOnPath(currentMap, targetMap);
        if (nextMap == null) {
            return;
        }

        Portal targetPortal = findPortalTo(nextMap);
        if (targetPortal == null) {
            return;
        }

        double distToPortal = module.hero.distanceTo(targetPortal);
        if (distToPortal < 200) {
            module.lastMapBeforeJump = currentMap;
            module.lastJumpTime = System.currentTimeMillis();
            module.movement.jumpPortal(targetPortal);
        } else {
            module.movement.moveTo(targetPortal);
        }
    }

    public void findNearestQuestGiverMap() {
        String currentMap = getCurrentMap();
        List<String> questGiverMaps = getQuestGiverMaps();

        int minDistance = Integer.MAX_VALUE;
        String nearestMap = null;

        for (String qgMap : questGiverMaps) {
            if (!levelAccessible(qgMap)) {
                continue;
            }

            int distance = getShortestPath(currentMap, qgMap);
            if (distance >= 0 && distance < minDistance) {
                minDistance = distance;
                nearestMap = qgMap;
            }
        }

        if (nearestMap != null) {
            module.targetQuestGiverMap = nearestMap;
            setWorkingMap(nearestMap);
        } else {
            module.targetQuestGiverMap = null;
        }
    }

    public void setWorkingMap(String mapName) {
        Integer mapId = module.starSystem.getMaps().stream()
                .filter(m -> m.getName().equalsIgnoreCase(mapName) || m.getShortName().equalsIgnoreCase(mapName))
                .findFirst()
                .map(eu.darkbot.api.game.other.GameMap::getId)
                .orElseGet(() -> MAP_IDS.get(mapName));

        if (mapId != null) {
            if (module.getWorkingMapSetting().getValue().equals(mapId))
                return; // Already set
            module.getWorkingMapSetting().setValue(mapId);
        }
    }

    public String getWorkingMap() {
        return module.starSystem.findMap(
                module.configApi.<Integer>requireConfig("general.working_map").getValue()).map(m -> m.getName())
                .orElse(null);
    }

    public List<String> getQuestGiverMaps() {
        int code = getFactionCode();
        return List.of(code + "-1", code + "-4", code + "-5", code + "-8");
    }

    public int getFactionCode() {
        Faction faction = module.hero.getEntityInfo().getFaction();
        if (faction == Faction.MMO)
            return 1;
        if (faction == Faction.EIC)
            return 2;
        if (faction == Faction.VRU)
            return 3;
        return 1;
    }

    public String getCurrentMap() {
        return module.starSystem.getCurrentMap().getShortName();
    }

    public String getFactionSpecificMapName(String mapName) {
        if (mapName == null || !mapName.startsWith("x-"))
            return mapName;

        int factionId = getFactionCode();
        if (factionId >= 1 && factionId <= 3) {
            return mapName.replace("x-", factionId + "-");
        }
        return mapName;
    }

    private boolean levelAccessible(String destMap) {
        int level = module.stats.getLevel();
        if (level >= 17) {
            return true;
        }

        Integer pvpLevel = PVP_LEVELS.get(destMap);
        if (pvpLevel != null) {
            return level >= pvpLevel;
        }

        Faction faction = module.hero.getEntityInfo().getFaction();
        Map<String, Integer> factionMap = null;
        if (faction == Faction.MMO)
            factionMap = MMO_LEVELS;
        else if (faction == Faction.EIC)
            factionMap = EIC_LEVELS;
        else if (faction == Faction.VRU)
            factionMap = VRU_LEVELS;

        if (factionMap == null) {
            return false;
        }
        Integer reqLevel = factionMap.get(destMap);
        return reqLevel == null || level >= reqLevel;
    }

    public int getShortestPath(String start, String end) {
        if (!MAP_CONNECTIONS.containsKey(start) || !MAP_CONNECTIONS.containsKey(end)) {
            return -1;
        }

        if (start.equals(end)) {
            return 0;
        }

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> distance = new HashMap<>();

        queue.add(start);
        visited.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distance.get(current);

            if (current.equals(end)) {
                return currentDist;
            }

            for (String neighbor : MAP_CONNECTIONS.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distance.put(neighbor, currentDist + 1);
                    queue.add(neighbor);

                    if (neighbor.equals(end)) {
                        return currentDist + 1;
                    }
                }
            }
        }

        return -1;
    }

    public String getNextMapOnPath(String start, String end) {
        if (!MAP_CONNECTIONS.containsKey(start) || !MAP_CONNECTIONS.containsKey(end)) {
            return null;
        }

        if (start.equals(end)) {
            return null;
        }

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(end)) {
                String step = end;
                while (parent.get(step) != null && !parent.get(step).equals(start)) {
                    step = parent.get(step);
                }
                return step;
            }

            for (String neighbor : MAP_CONNECTIONS.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return null;
    }

    private Portal findPortalTo(String targetMap) {
        return module.entities.getPortals().stream()
                .filter(p -> p.getTargetMap()
                        .map(m -> m.getShortName().equals(targetMap))
                        .orElse(false))
                .min(Comparator.comparingDouble(p -> module.hero.distanceTo(p)))
                .orElse(null);
    }
}
