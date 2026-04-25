package dev.shared.do_gamer.task.autobuy.config;

import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import dev.shared.do_gamer.utils.ConfigHtmlInstructions;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Readonly;

@Configuration("autobuy")
public final class AutobuyConfig {

    @Option("do_gamer.autobuy.booster")
    public BoostersConfig booster = new BoostersConfig();

    @Option("do_gamer.autobuy.special")
    public SpecialConfig special = new SpecialConfig();

    @Option("do_gamer.autobuy.ammo")
    public AmmoConfig ammo = new AmmoConfig();

    /**
     * Subclass for Booster configuration
     */
    public static class BoostersConfig extends AbstractItemConfig {
        public static final String CD_B01 = "CD-B01";
        public static final String CD_B02 = "CD-B02";
        public static final String DMG_B01 = "DMG-B01";
        public static final String DMG_B02 = "DMG-B02";
        public static final String DMG_H01 = "DMG-H01";
        public static final String HP_B01 = "HP-B01";
        public static final String HP_B02 = "HP-B02";
        public static final String SHD_B01 = "SHD-B01";
        public static final String SHD_B02 = "SHD-B02";

        /**
         * Maps for checking if a booster is enabled.
         */
        private static final Map<String, Predicate<BoostersConfig>> ENABLED_GETTERS = Map.ofEntries(
                Map.entry(CD_B01, config -> config.cdb01),
                Map.entry(CD_B02, config -> config.cdb02),
                Map.entry(DMG_B01, config -> config.dmgb01),
                Map.entry(DMG_B02, config -> config.dmgb02),
                Map.entry(DMG_H01, config -> config.dmgh01),
                Map.entry(HP_B01, config -> config.hpb01),
                Map.entry(HP_B02, config -> config.hpb02),
                Map.entry(SHD_B01, config -> config.shdb01),
                Map.entry(SHD_B02, config -> config.shdb02));

        public BoostersConfig() {
            this.itemIds = new String[] {
                    CD_B01, CD_B02, DMG_B01, DMG_B02, DMG_H01,
                    HP_B01, HP_B02, SHD_B01, SHD_B02
            };
        }

        @Option("do_gamer.autobuy.checkInterval")
        @Number(min = 5, max = 1440, step = 5)
        public int checkInterval = 30;

        /**
         * Subclass for instructions of each section
         */
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return this.buildList(null,
                        "Checks boosters every X minutes.",
                        "Buys selected boosters if expired.");
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("do_gamer.autobuy.booster.CD_B01")
        public boolean cdb01 = false;

        @Option("do_gamer.autobuy.booster.CD_B02")
        public boolean cdb02 = false;

        @Option("do_gamer.autobuy.booster.DMG_B01")
        public boolean dmgb01 = false;

        @Option("do_gamer.autobuy.booster.DMG_B02")
        public boolean dmgb02 = false;

        @Option("do_gamer.autobuy.booster.DMG_H01")
        public boolean dmgh01 = false;

        @Option("do_gamer.autobuy.booster.HP_B01")
        public boolean hpb01 = false;

        @Option("do_gamer.autobuy.booster.HP_B02")
        public boolean hpb02 = false;

        @Option("do_gamer.autobuy.booster.SHD_B01")
        public boolean shdb01 = false;

        @Option("do_gamer.autobuy.booster.SHD_B02")
        public boolean shdb02 = false;

        public boolean isEnabled(String code) {
            return ENABLED_GETTERS.getOrDefault(code, config -> false).test(this);
        }
    }

    /**
     * Subclass for Special configuration
     */
    public static class SpecialConfig extends AbstractItemConfig {
        public static final String LUMINAFLUX_ALLOY = "resource_collectable_luminaflux-alloy";
        public static final String DSE_KEY_ACCESS = "resource_key_access-dse";
        public static final String DSE_KEY_GREEN = "resource_echo-key-green";
        public static final String DSE_KEY_BLUE = "resource_echo-key-blue";
        public static final String DSE_KEY_PURPLE = "resource_echo-key-purple";
        public static final String LOG_FILE = "resource_logfile";
        public static final String PIRATE_KEY_GREEN = "resource_booty-key";

        /**
         * Maps for getting the amount condition of items.
         */
        private static final Map<String, ToIntFunction<SpecialConfig>> AMOUNT_GETTERS = Map.ofEntries(
                Map.entry(LUMINAFLUX_ALLOY, config -> config.luminafluxAlloy),
                Map.entry(DSE_KEY_ACCESS, config -> config.dseKeyAccess.amount),
                Map.entry(DSE_KEY_GREEN, config -> config.dseKeyGreen),
                Map.entry(DSE_KEY_BLUE, config -> config.dseKeyBlue),
                Map.entry(DSE_KEY_PURPLE, config -> config.dseKeyPurple),
                Map.entry(LOG_FILE, config -> config.logFile.amount),
                Map.entry(PIRATE_KEY_GREEN, config -> config.pirateKeyGreen.amount));

        /**
         * Maps for getting the minimum condition of items.
         */
        private static final Map<String, ToIntFunction<SpecialConfig>> MIN_GETTERS = Map.ofEntries(
                Map.entry(DSE_KEY_ACCESS, config -> config.dseKeyAccess.min),
                Map.entry(LOG_FILE, config -> config.logFile.min),
                Map.entry(PIRATE_KEY_GREEN, config -> config.pirateKeyGreen.min));

        public SpecialConfig() {
            this.itemIds = new String[] {
                    LUMINAFLUX_ALLOY, DSE_KEY_ACCESS, DSE_KEY_GREEN, DSE_KEY_BLUE,
                    DSE_KEY_PURPLE, LOG_FILE, PIRATE_KEY_GREEN
            };
        }

        @Option("do_gamer.autobuy.checkInterval")
        @Number(min = 5, max = 1440, step = 5)
        public int checkInterval = 60;

        /**
         * Subclass for instructions of each section
         */
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return this.buildList(null,
                        "Checks special items every X minutes.",
                        "Buys special items when available.");
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("do_gamer.autobuy.special.luminafluxAlloy")
        @Number(max = 1_000, step = 1)
        public int luminafluxAlloy = 0;

        @Option("do_gamer.autobuy.special.dseKeyAccess")
        public PurchaseConfig dseKeyAccess = new PurchaseConfig(10);

        @Option("do_gamer.autobuy.special.dseKeyGreen")
        @Number(max = 5, step = 1)
        public int dseKeyGreen = 0;

        @Option("do_gamer.autobuy.special.dseKeyBlue")
        @Number(max = 2, step = 1)
        public int dseKeyBlue = 0;

        @Option("do_gamer.autobuy.special.dseKeyPurple")
        @Number(max = 1, step = 1)
        public int dseKeyPurple = 0;

        @Option("do_gamer.autobuy.special.logFile")
        public PurchaseConfig logFile = new PurchaseConfig(500);

        @Option("do_gamer.autobuy.special.pirateKeyGreen")
        public PurchaseConfig pirateKeyGreen = new PurchaseConfig(500);

        public boolean isEnabled(String itemId) {
            return this.getAmountOfItem(itemId) > 0;
        }

        public boolean isUpdateHangar() {
            return this.isEnabled(DSE_KEY_ACCESS) || this.isEnabled(PIRATE_KEY_GREEN);
        }

        public int getAmountOfItem(String itemId) {
            return AMOUNT_GETTERS.getOrDefault(itemId, config -> 0).applyAsInt(this);
        }

        public int getMinConditionForItem(String itemId) {
            return MIN_GETTERS.getOrDefault(itemId, config -> -1).applyAsInt(this);
        }
    }

    /**
     * Subclass for Ammo configuration
     */
    public static class AmmoConfig extends AbstractItemConfig {
        public static final String LCB_10 = "ammunition_laser_lcb-10";
        public static final String MCB_25 = "ammunition_laser_mcb-25";
        public static final String MCB_50 = "ammunition_laser_mcb-50";
        public static final String SAB_50 = "ammunition_laser_sab-50";
        public static final String RSB_75 = "ammunition_laser_rsb-75";
        public static final String JOB_100 = "ammunition_laser_job-100";
        public static final String PLT_2026 = "ammunition_rocket_plt-2026";
        public static final String PLT_2021 = "ammunition_rocket_plt-2021";
        public static final String EMP_01 = "ammunition_specialammo_emp-01";
        public static final String ECO_10 = "ammunition_rocketlauncher_eco-10";
        public static final String SLUG_THS_D01 = "ammunition_slug_ths-d01";
        public static final String SLUG_COS_D01 = "ammunition_slug_cos-d01";
        public static final String SLUG_ELS_D01 = "ammunition_slug_els-d01";

        /**
         * Maps for getting the amount condition of items.
         */
        private static final Map<String, ToIntFunction<AmmoConfig>> AMOUNT_GETTERS = Map.ofEntries(
                Map.entry(LCB_10, config -> config.lcb10.amount),
                Map.entry(MCB_25, config -> config.mcb25.amount),
                Map.entry(MCB_50, config -> config.mcb50.amount),
                Map.entry(SAB_50, config -> config.sab50.amount),
                Map.entry(RSB_75, config -> config.rsb75.amount),
                Map.entry(JOB_100, config -> config.job100.amount),
                Map.entry(PLT_2026, config -> config.plt2026.amount),
                Map.entry(PLT_2021, config -> config.plt2021.amount),
                Map.entry(EMP_01, config -> config.emp01.amount),
                Map.entry(ECO_10, config -> config.eco10.amount),
                Map.entry(SLUG_THS_D01, config -> config.slugThsD01.amount),
                Map.entry(SLUG_COS_D01, config -> config.slugCosD01.amount),
                Map.entry(SLUG_ELS_D01, config -> config.slugElsD01.amount));

        /**
         * Maps for getting the minimum condition of items.
         */
        private static final Map<String, ToIntFunction<AmmoConfig>> MIN_GETTERS = Map.ofEntries(
                Map.entry(LCB_10, config -> config.lcb10.min),
                Map.entry(MCB_25, config -> config.mcb25.min),
                Map.entry(MCB_50, config -> config.mcb50.min),
                Map.entry(SAB_50, config -> config.sab50.min),
                Map.entry(RSB_75, config -> config.rsb75.min),
                Map.entry(JOB_100, config -> config.job100.min),
                Map.entry(PLT_2026, config -> config.plt2026.min),
                Map.entry(PLT_2021, config -> config.plt2021.min),
                Map.entry(EMP_01, config -> config.emp01.min),
                Map.entry(ECO_10, config -> config.eco10.min),
                Map.entry(SLUG_THS_D01, config -> config.slugThsD01.min),
                Map.entry(SLUG_COS_D01, config -> config.slugCosD01.min),
                Map.entry(SLUG_ELS_D01, config -> config.slugElsD01.min));

        public AmmoConfig() {
            this.itemIds = new String[] {
                    LCB_10, MCB_25, MCB_50, SAB_50, RSB_75, JOB_100,
                    PLT_2026, PLT_2021, EMP_01, ECO_10, SLUG_THS_D01,
                    SLUG_COS_D01, SLUG_ELS_D01
            };
        }

        @Option("do_gamer.autobuy.checkInterval")
        @Number(min = 5, max = 1440, step = 5)
        public int checkInterval = 15;

        /**
         * Subclass for instructions of each section
         */
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return this.buildList(null,
                        "Checks ammo every X minutes.",
                        "Buys ammo when inventory is low.");
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("do_gamer.autobuy.ammo.lcb10")
        public PurchaseConfig lcb10 = new PurchaseConfig(100_000);

        @Option("do_gamer.autobuy.ammo.mcb25")
        public PurchaseConfig mcb25 = new PurchaseConfig(50_000);

        @Option("do_gamer.autobuy.ammo.mcb50")
        public PurchaseConfig mcb50 = new PurchaseConfig(50_000);

        @Option("do_gamer.autobuy.ammo.sab50")
        public PurchaseConfig sab50 = new PurchaseConfig(50_000);

        @Option("do_gamer.autobuy.ammo.rsb75")
        public PurchaseConfig rsb75 = new PurchaseConfig(50_000);

        @Option("do_gamer.autobuy.ammo.job100")
        public PurchaseConfig job100 = new PurchaseConfig(50_000);

        @Option("do_gamer.autobuy.ammo.plt2026")
        public PurchaseConfig plt2026 = new PurchaseConfig(1_000);

        @Option("do_gamer.autobuy.ammo.plt2021")
        public PurchaseConfig plt2021 = new PurchaseConfig(1_000);

        @Option("do_gamer.autobuy.ammo.emp01")
        public PurchaseConfig emp01 = new PurchaseConfig(500);

        @Option("do_gamer.autobuy.ammo.eco10")
        public PurchaseConfig eco10 = new PurchaseConfig(5_000);

        @Option("do_gamer.autobuy.ammo.slugThsD01")
        public PurchaseConfig slugThsD01 = new PurchaseConfig(1_000);

        @Option("do_gamer.autobuy.ammo.slugCosD01")
        public PurchaseConfig slugCosD01 = new PurchaseConfig(1_000);

        @Option("do_gamer.autobuy.ammo.slugElsD01")
        public PurchaseConfig slugElsD01 = new PurchaseConfig(1_000);

        public boolean isEnabled(String itemId) {
            return this.getAmountOfItem(itemId) > 0;
        }

        public boolean isUpdateHangar() {
            return this.anyEnabled();
        }

        public int getAmountOfItem(String itemId) {
            return AMOUNT_GETTERS.getOrDefault(itemId, config -> 0).applyAsInt(this);
        }

        public int getMinConditionForItem(String itemId) {
            return MIN_GETTERS.getOrDefault(itemId, config -> -1).applyAsInt(this);
        }
    }

    /**
     * Subclass for purchase configuration
     */
    public static class PurchaseConfig {
        PurchaseConfig(int min) {
            this.min = min;
        }

        @Option("do_gamer.autobuy.purchase.amount")
        @Number(max = 10_000_000, step = 100)
        public int amount = 0;

        @Option("do_gamer.autobuy.purchase.min")
        @Number(max = 500_000, step = 10)
        public int min = 0;
    }

    /**
     * Functional interface for checking if an item is enabled based on its ID.
     */
    @FunctionalInterface
    private interface EnabledChecker {
        boolean enabled(String id);
    }

    /**
     * Utility method to check if any of the given IDs are enabled.
     */
    private static boolean anyEnabled(EnabledChecker checker, String... ids) {
        for (String id : ids) {
            if (checker.enabled(id))
                return true;
        }
        return false;
    }

    /**
     * Abstract class for common configuration properties and methods.
     */
    public abstract static class AbstractItemConfig {
        protected String[] itemIds;

        protected AbstractItemConfig() {
        }

        public boolean anyEnabled() {
            return AutobuyConfig.anyEnabled(this::isEnabled, this.itemIds);
        }

        public boolean anyEnabled(String... ids) {
            return AutobuyConfig.anyEnabled(this::isEnabled, ids);
        }

        public abstract boolean isEnabled(String id);
    }
}
