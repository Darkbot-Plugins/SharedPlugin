package dev.shared.orbithelper.module.daily_quest;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Static data: NPC spawn maps, ore spawn maps, reward filter, and quest
 * scanning parameters.
 */
public final class Constants {

        private Constants() {
        }

        // -------------------------------------------------------------------------
        // Reward Filter
        // -------------------------------------------------------------------------

        /** Only quests containing this reward type are processed. */
        public static final String REWARD_TYPE = "resource_collectable_armory-token";

        // -------------------------------------------------------------------------
        // Quest Scan — GUI Coordinates
        // -------------------------------------------------------------------------

        /** Starting X coordinate for scanning (decrements right-to-left). */
        public static final int SCAN_START_X = 375;

        /** Amount subtracted from X on each scan step. */
        public static final int SCAN_STEP_X = 8;

        /** Fixed Y coordinate used for all scan clicks. */
        public static final int SCAN_Y = 9;

        /** Scanning stops when the calculated X falls below this threshold. */
        public static final int SCAN_MIN_X = 50;

        // -------------------------------------------------------------------------
        // Timing & Retry Constants
        // -------------------------------------------------------------------------

        /** Time to wait after the quest window opens before interacting (ms). */
        public static final long WINDOW_SETTLE_DELAY_MS = 2500;

        public static final int MAX_SCAN_RETRIES = 5;

        public static final int MAX_SEQUENTIAL_WAIT_RETRIES = 20;

        public static final long MAP_CHANGE_GRACE_PERIOD_MS = 5000;

        // -------------------------------------------------------------------------
        // PvP Map
        // -------------------------------------------------------------------------

        /** Cross-faction PvP map. */
        public static final String PVP_MAP = "4-5";

        // -------------------------------------------------------------------------
        // NPC Name Constants
        // -------------------------------------------------------------------------

        public static final String NPC_KRISTALLIN = "Kristallin";
        public static final String NPC_KRISTALLON = "Kristallon";
        public static final String NPC_PROTEGIT = "Protegit";
        public static final String NPC_SPINEL = "Spinel";

        // -------------------------------------------------------------------------
        // Ore Name Constants
        // -------------------------------------------------------------------------

        public static final String ORE_PROMETIUM = "Prometium";
        public static final String ORE_ENDURIUM = "Endurium";
        public static final String ORE_TERBIUM = "Terbium";
        public static final String ORE_PROMETID = "Prometid";
        public static final String ORE_DURANIUM = "Duranium";
        public static final String ORE_PROMERIUM = "Promerium";

        // -------------------------------------------------------------------------
        // NPC Data
        // -------------------------------------------------------------------------

        /** All base NPC names used for description parsing. */
        public static final List<String> BASE_NPCS = Collections.unmodifiableList(Arrays.asList(
                        "Sibelonit", "Sibelon", "StreuneR", "Streuner", "Lordakia", "Saimon",
                        "Mordon", "Devolarium", "Lordakium",
                        NPC_KRISTALLIN, NPC_KRISTALLON, NPC_PROTEGIT, NPC_SPINEL));

        /** NPC name prefixes (Uber, Boss, etc.). */
        public static final List<String> NPC_PREFIXES = Collections.unmodifiableList(Arrays.asList(
                        "Uber", "Boss", "Plagued", "Blighted", "Recruit", "Aider", "Emperor"));

        /** NPC name suffixes. */
        public static final List<String> NPC_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
                        "Spore"));

        /** Spawn maps keyed by full NPC name. Order: Uber → Boss → Special → Normal. */
        public static final Map<String, List<String>> NPC_SPAWNS;
        static {
                Map<String, List<String>> map = new LinkedHashMap<>();

                // Uber variants
                map.put("UberSibelonit", Arrays.asList("4-5"));
                map.put("UberSibelon", Arrays.asList("4-5"));
                map.put("UberStreuner", Arrays.asList("4-5"));
                map.put("UberLordakia", Arrays.asList("4-5"));
                map.put("UberSaimon", Arrays.asList("4-5"));
                map.put("UberMordon", Arrays.asList("4-5"));
                map.put("UberDevolarium", Arrays.asList("4-5"));
                map.put("UberLordakium", Arrays.asList("4-5"));
                map.put("UberKristallin", Arrays.asList("4-5"));
                map.put("UberKristallon", Arrays.asList("4-5"));

                // Boss variants
                map.put("Boss Sibelonit", Arrays.asList("x-5", "4-5"));
                map.put("Boss Sibelon", Arrays.asList("x-4", "4-5"));
                map.put("Boss StreuneR", Arrays.asList("x-8", "4-5"));
                map.put("Boss Streuner", Arrays.asList("x-2", "4-5"));
                map.put("Boss Lordakia", Arrays.asList("x-2", "4-5"));
                map.put("Boss Saimon", Arrays.asList("x-3", "x-4", "4-5"));
                map.put("Boss Mordon", Arrays.asList("x-3", "4-5"));
                map.put("Boss Devolarium", Arrays.asList("x-3", "4-5"));
                map.put("Boss Lordakium", Arrays.asList("x-5", "4-5"));
                map.put("Boss Kristallin", Arrays.asList("x-6", "x-7", "4-5"));
                map.put("Boss Kristallon", Arrays.asList("x-7", "4-5"));

                // Special variants
                map.put("Recruit Streuner", Arrays.asList("x-1", "x-2"));
                map.put("Aider Streuner", Arrays.asList("x-1", "x-2"));

                // Normal variants
                map.put("Sibelonit", Arrays.asList("x-5"));
                map.put("Sibelon", Arrays.asList("x-4"));
                map.put("StreuneR", Arrays.asList("x-8"));
                map.put("Streuner", Arrays.asList("x-1", "x-2"));
                map.put("Lordakia", Arrays.asList("x-2", "x-3", "x-4", "x-5"));
                map.put("Saimon", Arrays.asList("x-3", "x-4"));
                map.put("Mordon", Arrays.asList("x-3", "x-4"));
                map.put("Devolarium", Arrays.asList("x-3"));
                map.put("Lordakium", Arrays.asList("x-5"));
                map.put(NPC_KRISTALLIN, Arrays.asList("x-6", "x-7"));
                map.put(NPC_KRISTALLON, Arrays.asList("x-6", "x-7"));
                map.put(NPC_PROTEGIT, Arrays.asList("x-6"));
                map.put(NPC_SPINEL, Arrays.asList("x-6", "x-7"));

                NPC_SPAWNS = Collections.unmodifiableMap(map);
        }

        // -------------------------------------------------------------------------
        // Ore Data
        // -------------------------------------------------------------------------

        /** Spawn maps keyed by ore name. */
        public static final Map<String, List<String>> ORE_SPAWNS;
        static {
                Map<String, List<String>> map = new LinkedHashMap<>();
                // Raw ore (mine from space)

                map.put(ORE_PROMETIUM, Arrays.asList("x-1", "x-2"));
                map.put(ORE_ENDURIUM, Arrays.asList("x-1", "x-2"));
                map.put(ORE_TERBIUM, Arrays.asList("x-3"));

                // FROM_SHIP ores (collect from destroyed ship boxes in x-6/x-7)
                map.put(ORE_PROMETID, Arrays.asList("x-6", "x-7"));
                map.put(ORE_DURANIUM, Arrays.asList("x-6", "x-7"));
                map.put(ORE_PROMERIUM, Arrays.asList("x-6", "x-7"));

                ORE_SPAWNS = Collections.unmodifiableMap(map);
        }

        // -------------------------------------------------------------------------
        // Ore Sell Names — all ores that can appear in SELL_ORE quests
        // -------------------------------------------------------------------------

        /** All ore names that may appear in SELL_ORE quest descriptions. */
        public static final List<String> ORE_SELL_NAMES = Collections.unmodifiableList(Arrays.asList(
                        ORE_PROMETIUM, ORE_ENDURIUM, ORE_TERBIUM, ORE_PROMETID, ORE_DURANIUM, ORE_PROMERIUM));

        // -------------------------------------------------------------------------
        // Ore Box Type Names
        // -------------------------------------------------------------------------

        /** Maps ore name (from quest description) to in-game box type name. */
        public static final Map<String, String> ORE_BOX_TYPES;
        static {
                Map<String, String> map = new LinkedHashMap<>();
                map.put(ORE_PROMETIUM, "ore_0");
                map.put(ORE_ENDURIUM, "ore_1");
                map.put(ORE_TERBIUM, "ore_2");
                ORE_BOX_TYPES = Collections.unmodifiableMap(map);
        }

        /** Ores collected via FROM_SHIP boxes dropped by destroyed ships. */
        public static final Set<String> FROM_SHIP_ORES = Collections.unmodifiableSet(
                        new LinkedHashSet<>(Arrays.asList(ORE_PROMETID, ORE_DURANIUM, ORE_PROMERIUM)));

        public static final String FROM_SHIP_BOX_TYPE = "FROM_SHIP";

        public static final List<String> FROM_SHIP_MAPS = Collections.unmodifiableList(
                        Arrays.asList("x-6", "x-7"));

        /**
         * Priority-ordered NPC list for SALVAGE quests. Kill these to generate
         * FROM_SHIP boxes.
         */
        public static final List<String> SALVAGE_NPCS = Collections.unmodifiableList(Arrays.asList(
                        "Boss " + NPC_KRISTALLON,
                        "Boss " + NPC_KRISTALLIN,
                        NPC_KRISTALLON,
                        NPC_KRISTALLIN,
                        NPC_PROTEGIT,
                        NPC_SPINEL));

        // -------------------------------------------------------------------------
        // Map Name Regex
        // -------------------------------------------------------------------------

        /** Used to extract "x-y" formatted map names from quest descriptions. */
        public static final Pattern MAP_NAME_PATTERN = Pattern.compile("(\\d+-\\d+)");
}