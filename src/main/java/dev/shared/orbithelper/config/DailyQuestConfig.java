package dev.shared.orbithelper.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Option;

@Configuration("orbithelper.daily_quest")
public class DailyQuestConfig {

    @Option("orbithelper.daily_quest.accept_quest")
    public boolean acceptQuest = true;

    @Option("orbithelper.daily_quest.fallback_config")
    @Dropdown(options = FallbackConfigOptions.class)
    public String fallbackConfig = FallbackConfigOptions.STOP_BOT;

    @Option("orbithelper.daily_quest.switch_ship")
    @Dropdown(options = ShipList.class)
    public String shipForQuest = null;

    @Option("orbithelper.daily_quest.quest_types")
    public QuestTypesSettings questTypes = new QuestTypesSettings();

    public static class QuestTypesSettings {
        @Option("orbithelper.daily_quest.kill_npc_settings")
        public KillNpcSettings killNpcSettings = new KillNpcSettings();

        @Option("orbithelper.daily_quest.collect_ores_settings")
        public CollectOresSettings collectOresSettings = new CollectOresSettings();

        @Option("orbithelper.daily_quest.sell_ores_settings")
        public SellOresSettings sellOresSettings = new SellOresSettings();

        @Option("orbithelper.daily_quest.kill_players_settings")
        public KillPlayersSettings killPlayersSettings = new KillPlayersSettings();

    }

    public static class KillNpcSettings {
        @Option("orbithelper.daily_quest.kill_npc_settings.kill_npc")
        public boolean killNpc = true;

        @Option("orbithelper.daily_quest.kill_npc_settings.use_enemy_locator")
        public boolean useEnemyLocator = true;

        @Option("orbithelper.daily_quest.kill_npc_settings.ignore_ownership")
        public boolean ignoreOwnership = false;

        @Option("orbithelper.daily_quest.kill_npc_settings.ignore_pvp_maps")
        public boolean ignorePvpMaps = false;
    }

    public static class CollectOresSettings {
        @Option("orbithelper.daily_quest.collect_ores_settings.collect_ores")
        public boolean collectOres = true;

        @Option("orbithelper.daily_quest.collect_ores_settings.switch_ship")
        @Dropdown(options = ShipList.class)
        public String shipForCollect = null;

        @Option("orbithelper.daily_quest.collect_ores_settings.kill_npcs_for_salvage")
        public boolean killNpcsForSalvage = true;
    }

    public static class SellOresSettings {
        @Option("orbithelper.daily_quest.sell_ores_settings.sell_ores")
        public boolean sellOres = true;

        @Option("orbithelper.daily_quest.sell_ores_settings.collect_method")
        @Dropdown
        public CollectionMethod collectionMethod = CollectionMethod.CARGO;

        @Option("orbithelper.daily_quest.sell_ores_settings.selling_method")
        @Dropdown
        public SellingMethod sellingMethod = SellingMethod.BASE;

        @Option("orbithelper.daily_quest.sell_ores_settings.switch_ship")
        @Dropdown(options = ShipList.class)
        public String shipForSell = null;

    }

    public static class KillPlayersSettings {
        @Option("orbithelper.daily_quest.kill_players_settings.kill_players")
        public boolean killPlayers = true;

        @Option("orbithelper.daily_quest.kill_players_settings.primary_ammo")
        @Dropdown
        public PrimaryAmmoMethod primaryAmmo = PrimaryAmmoMethod.UCB_100;

        @Option("orbithelper.daily_quest.kill_players_settings.use_rsb")
        public boolean useRsb = false;
    }

    public static class FallbackConfigOptions implements Dropdown.Options<String> {

        // Special sentinel values
        public static final String STOP_BOT = "STOP_BOT";
        public static final String LAST_USED = "LAST_USED";
        public static final String CONFIG = "config";
        private static final String JSON_EXT = ".json";

        @Override
        public java.util.List<String> options() {
            java.util.List<String> opts = new ArrayList<>();
            opts.add(STOP_BOT);
            opts.add(LAST_USED);
            opts.add(CONFIG);

            // configs/*.json, excluding *_old.json
            File configsDir = new File("configs");
            if (configsDir.isDirectory()) {
                File[] files = configsDir.listFiles(f -> f.isFile()
                        && f.getName().endsWith(JSON_EXT)
                        && !f.getName().endsWith("_old" + JSON_EXT));
                if (files != null) {
                    Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File f : files) {
                        String name = f.getName().replace(JSON_EXT, "");
                        if (!opts.contains(name))
                            opts.add(name);
                    }
                }
            }
            return opts;
        }

        @Override
        public String getText(String option) {
            if (STOP_BOT.equals(option))
                return "Stop Bot";
            if (LAST_USED.equals(option))
                return "Last Used Config";
            if (CONFIG.equals(option))
                return CONFIG;
            return option;
        }

        /**
         * Resolves which config profile to switch to when the module completes.
         * Returns null if the bot should stop instead of switching.
         *
         * @param currentProfile the profile that is currently active (to exclude from
         *                       LAST_USED)
         */
        public static String resolve(String fallbackConfig, String currentProfile) {
            if (fallbackConfig == null || STOP_BOT.equals(fallbackConfig))
                return null;

            if (LAST_USED.equals(fallbackConfig)) {
                return findLastModifiedConfig(currentProfile);
            }

            return fallbackConfig;
        }

        /**
         * Finds the most recently modified config file, excluding the currently active
         * profile.
         * Checks both config.json (root) and configs/*.json (excluding *_old.json).
         */
        private static String findLastModifiedConfig(String currentProfile) {
            java.util.List<File> candidates = new ArrayList<>();

            // Root config.json → profile name "config"
            File rootConfig = new File(CONFIG + JSON_EXT);
            if (rootConfig.exists() && !CONFIG.equals(currentProfile))
                candidates.add(rootConfig);

            addConfigFileCandidates(new File("configs"), currentProfile, candidates);

            if (candidates.isEmpty())
                return null;

            // Pick most recently modified
            File newest = candidates.get(0);
            for (File f : candidates) {
                if (f.lastModified() > newest.lastModified())
                    newest = f;
            }

            return newest.getName().replace(JSON_EXT, "");
        }

        private static void addConfigFileCandidates(File configsDir, String currentProfile, java.util.List<File> candidates) {
            if (!configsDir.isDirectory())
                return;
            File[] files = configsDir.listFiles(f -> f.isFile()
                    && f.getName().endsWith(JSON_EXT)
                    && !f.getName().endsWith("_old" + JSON_EXT));
            if (files == null)
                return;
            for (File f : files) {
                String name = f.getName().replace(JSON_EXT, "");
                if (!name.equals(currentProfile))
                    candidates.add(f);
            }
        }
    }

    public static class ShipList implements Dropdown.Options<String> {
        public static final Map<String, String> ships = new LinkedHashMap<>();

        @Override
        public List<String> options() {
            return ships.keySet().stream().collect(Collectors.toList());
        }

        @Override
        public String getText(String option) {
            String name = ships.getOrDefault(option, null);
            if (name == null || name.equals("")) {
                return "--Current Ship--";
            }
            String formatted = name.replaceFirst("ship_", "").replace("-plus", " plus");
            return formatted.isEmpty() ? formatted : formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
        }

        public static String getShipName(String hangarId) {
            return ships.getOrDefault(hangarId, null);
        }
    }

    @Configuration("orbithelper.daily_quest.sell_ores_settings.collect_method.list")
    public enum CollectionMethod {
        CARGO,
        SKYLAB
    }

    @Configuration("orbithelper.daily_quest.sell_ores_settings.selling_method.list")
    public enum SellingMethod {
        BASE,
        PET_TRADING,
        HM7
    }

    @Configuration("orbithelper.daily_quest.kill_players_settings.primary_ammo_method.list")
    public enum PrimaryAmmoMethod {
        LCB_10,
        MCB_25,
        MCB_50,
        UCB_100,
        A_BL
    }

}