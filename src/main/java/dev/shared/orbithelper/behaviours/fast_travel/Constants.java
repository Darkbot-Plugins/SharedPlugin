package dev.shared.orbithelper.behaviours.fast_travel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.darkbot.api.game.other.EntityInfo;

public class Constants {
        private Constants() {
        }

        public static class Coordinate {
                public final int x;
                public final int y;

                public Coordinate(int x, int y) {
                        this.x = x;
                        this.y = y;
                }
        }

        /**
         * Map coordinates for fast travel locations.
         * 
         * The key is the map identifier (e.g., "1-1", "2-3").
         * The value is the Coordinate object containing x and y values.
         */
        protected static final Map<String, Coordinate> MAP_COORDINATES = new HashMap<>();

        static {
                // MMO (1-x)
                MAP_COORDINATES.put("1-1", new Coordinate(107, 477));
                MAP_COORDINATES.put("1-2", new Coordinate(158, 449));
                MAP_COORDINATES.put("1-3", new Coordinate(158, 387));
                MAP_COORDINATES.put("1-4", new Coordinate(210, 420));
                MAP_COORDINATES.put("1-5", new Coordinate(570, 444));
                MAP_COORDINATES.put("1-6", new Coordinate(610, 402));
                MAP_COORDINATES.put("1-7", new Coordinate(610, 485));
                MAP_COORDINATES.put("1-8", new Coordinate(650, 444));

                // EIC (2-x)
                MAP_COORDINATES.put("2-1", new Coordinate(55, 285));
                MAP_COORDINATES.put("2-2", new Coordinate(107, 285));
                MAP_COORDINATES.put("2-3", new Coordinate(158, 309));
                MAP_COORDINATES.put("2-4", new Coordinate(158, 259));
                MAP_COORDINATES.put("2-5", new Coordinate(570, 284));
                MAP_COORDINATES.put("2-6", new Coordinate(610, 243));
                MAP_COORDINATES.put("2-7", new Coordinate(610, 326));
                MAP_COORDINATES.put("2-8", new Coordinate(650, 284));

                // VRU (3-x)
                MAP_COORDINATES.put("3-1", new Coordinate(107, 95));
                MAP_COORDINATES.put("3-2", new Coordinate(158, 123));
                MAP_COORDINATES.put("3-3", new Coordinate(158, 183));
                MAP_COORDINATES.put("3-4", new Coordinate(210, 150));
                MAP_COORDINATES.put("3-5", new Coordinate(570, 126));
                MAP_COORDINATES.put("3-6", new Coordinate(610, 82));
                MAP_COORDINATES.put("3-7", new Coordinate(610, 168));
                MAP_COORDINATES.put("3-8", new Coordinate(650, 125));

                // Battle Maps (4-x)
                MAP_COORDINATES.put("4-1", new Coordinate(280, 347));
                MAP_COORDINATES.put("4-2", new Coordinate(242, 285));
                MAP_COORDINATES.put("4-3", new Coordinate(280, 221));
                MAP_COORDINATES.put("4-4", new Coordinate(425, 466));
                MAP_COORDINATES.put("4-5", new Coordinate(425, 106));

                // JUMP BUTTON
                MAP_COORDINATES.put("JUMP", new Coordinate(671, 570));
        }

        /**
         * List of all allowed maps for fast travel.
         */
        public static final List<String> ALLOWED_MAPS = List.of(
                        "1-1", "1-2", "1-3", "1-4", "1-5", "1-6", "1-7", "1-8",
                        "2-1", "2-2", "2-3", "2-4", "2-5", "2-6", "2-7", "2-8",
                        "3-1", "3-2", "3-3", "3-4", "3-5", "3-6", "3-7", "3-8",
                        "4-1", "4-2", "4-3", "4-4", "4-5");

        /**
         * Map connections graph for calculating path distance.
         * key: Map Name
         * value: List of connected maps (reachable via portal)
         */
        protected static final Map<String, List<String>> MAP_CONNECTIONS = new HashMap<>();

        static {
                // 1-X Maps
                MAP_CONNECTIONS.put("1-1", List.of("1-2"));
                MAP_CONNECTIONS.put("1-2", List.of("1-1", "1-3", "1-4"));
                MAP_CONNECTIONS.put("1-3", List.of("1-2", "2-3", "1-4"));
                MAP_CONNECTIONS.put("1-4", List.of("1-2", "1-3", "4-1", "3-4"));
                MAP_CONNECTIONS.put("1-5", List.of("4-4", "1-6", "1-7", "4-5"));
                MAP_CONNECTIONS.put("1-6", List.of("1-5", "1-8"));
                MAP_CONNECTIONS.put("1-7", List.of("1-5", "1-8"));
                MAP_CONNECTIONS.put("1-8", List.of("1-6", "1-7"));

                // 2-X Maps
                MAP_CONNECTIONS.put("2-1", List.of("2-2"));
                MAP_CONNECTIONS.put("2-2", List.of("2-1", "2-3", "2-4"));
                MAP_CONNECTIONS.put("2-3", List.of("2-2", "2-4", "1-3"));
                MAP_CONNECTIONS.put("2-4", List.of("2-2", "2-3", "3-3", "4-2"));
                MAP_CONNECTIONS.put("2-5", List.of("4-4", "4-5", "2-6", "2-7"));
                MAP_CONNECTIONS.put("2-6", List.of("2-5", "2-8"));
                MAP_CONNECTIONS.put("2-7", List.of("2-5", "2-8"));
                MAP_CONNECTIONS.put("2-8", List.of("2-6", "2-7"));

                // 3-X Maps
                MAP_CONNECTIONS.put("3-1", List.of("3-2"));
                MAP_CONNECTIONS.put("3-2", List.of("3-1", "3-3", "3-4"));
                MAP_CONNECTIONS.put("3-3", List.of("3-2", "3-4", "2-4"));
                MAP_CONNECTIONS.put("3-4", List.of("3-2", "3-3", "4-3", "1-3"));
                MAP_CONNECTIONS.put("3-5", List.of("4-4", "4-5", "3-6", "3-7"));
                MAP_CONNECTIONS.put("3-6", List.of("3-5", "3-8"));
                MAP_CONNECTIONS.put("3-7", List.of("3-5", "3-8"));
                MAP_CONNECTIONS.put("3-8", List.of("3-7", "3-6"));

                // 4-X Maps
                MAP_CONNECTIONS.put("4-1", List.of("4-2", "4-3", "4-4", "1-4"));
                MAP_CONNECTIONS.put("4-2", List.of("4-1", "4-3", "4-4", "2-4"));
                MAP_CONNECTIONS.put("4-3", List.of("4-1", "4-2", "4-4", "3-4"));
                MAP_CONNECTIONS.put("4-4", List.of("4-1", "4-2", "4-3", "1-5", "2-5", "3-5"));
                MAP_CONNECTIONS.put("4-5", List.of("1-5", "2-5", "3-5"));
        }

        /**
         * Map Levels required to fast travel to specific maps.
         * 
         * The key is the map identifier (e.g., "1-1", "2-3").
         * The value is the minimum player level required to access that map.
         * 
         * FAQ: https://board-en.darkorbit.com/threads/levels-faq.843/
         */
        public static final Map<String, Integer> PVP_LEVELS = Map.ofEntries(
                        Map.entry("4-1", 8),
                        Map.entry("4-2", 8),
                        Map.entry("4-3", 8),
                        Map.entry("4-4", 9),
                        Map.entry("4-5", 12));

        public static final Map<String, Integer> MMO_LEVELS = Map.ofEntries(
                        Map.entry("1-1", 1),
                        Map.entry("1-2", 1),
                        Map.entry("1-3", 2),
                        Map.entry("1-4", 3),
                        Map.entry("2-3", 5),
                        Map.entry("2-4", 5),
                        Map.entry("3-3", 5),
                        Map.entry("3-4", 5),
                        Map.entry("1-5", 10),
                        Map.entry("1-6", 11),
                        Map.entry("1-7", 11),
                        Map.entry("1-8", 12),
                        Map.entry("2-2", 13),
                        Map.entry("3-2", 13),
                        Map.entry("2-5", 13),
                        Map.entry("3-5", 13),
                        Map.entry("2-6", 14),
                        Map.entry("2-7", 14),
                        Map.entry("3-6", 14),
                        Map.entry("3-7", 14),
                        Map.entry("2-1", 15),
                        Map.entry("3-1", 15));

        public static final Map<String, Integer> EIC_LEVELS = Map.ofEntries(
                        Map.entry("2-1", 1),
                        Map.entry("2-2", 1),
                        Map.entry("2-3", 2),
                        Map.entry("2-4", 3),
                        Map.entry("3-3", 5),
                        Map.entry("3-4", 5),
                        Map.entry("1-3", 5),
                        Map.entry("1-4", 5),
                        Map.entry("2-5", 10),
                        Map.entry("2-6", 11),
                        Map.entry("2-7", 11),
                        Map.entry("2-8", 12),
                        Map.entry("3-2", 13),
                        Map.entry("1-2", 13),
                        Map.entry("3-5", 13),
                        Map.entry("1-5", 13),
                        Map.entry("3-6", 14),
                        Map.entry("3-7", 14),
                        Map.entry("1-6", 14),
                        Map.entry("1-7", 14),
                        Map.entry("3-1", 15),
                        Map.entry("1-1", 15));

        public static final Map<String, Integer> VRU_LEVELS = Map.ofEntries(
                        Map.entry("3-1", 1),
                        Map.entry("3-2", 1),
                        Map.entry("3-3", 2),
                        Map.entry("3-4", 3),
                        Map.entry("1-3", 5),
                        Map.entry("1-4", 5),
                        Map.entry("2-3", 5),
                        Map.entry("2-4", 5),
                        Map.entry("3-5", 10),
                        Map.entry("3-6", 11),
                        Map.entry("3-7", 11),
                        Map.entry("3-8", 12),
                        Map.entry("1-2", 13),
                        Map.entry("2-2", 13),
                        Map.entry("1-5", 13),
                        Map.entry("2-5", 13),
                        Map.entry("1-6", 14),
                        Map.entry("1-7", 14),
                        Map.entry("2-6", 14),
                        Map.entry("2-7", 14),
                        Map.entry("1-1", 15),
                        Map.entry("2-1", 15));

        /**
         * Map of faction to their respective level requirements.
         */
        protected static final EnumMap<EntityInfo.Faction, Map<String, Integer>> FACTION_LEVELS = new EnumMap<>(
                        EntityInfo.Faction.class);

        static {
                FACTION_LEVELS.put(EntityInfo.Faction.MMO, MMO_LEVELS);
                FACTION_LEVELS.put(EntityInfo.Faction.EIC, EIC_LEVELS);
                FACTION_LEVELS.put(EntityInfo.Faction.VRU, VRU_LEVELS);
        }

        /**
         * List of restricted modules where fast travel is not allowed.
         */
        public static final List<String> RESTRICTED_MODULES = List.of("SentinelModule", "FollowModule", "Follow");

}
