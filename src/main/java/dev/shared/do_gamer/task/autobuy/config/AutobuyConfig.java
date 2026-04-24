package dev.shared.do_gamer.task.autobuy.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration("autobuy")
public class AutobuyConfig {

    @Option("do_gamer.autobuy.checkInterval")
    @Number(min = 5, max = 1440, step = 5)
    public int checkInterval = 30;

    @Option("do_gamer.autobuy.booster")
    public BoostersConfig booster = new BoostersConfig();

    @Option("do_gamer.autobuy.special")
    public SpecialConfig special = new SpecialConfig();

    /**
     * Subclass for Booster configuration
     */
    public static class BoostersConfig {
        public static final String CD_B01 = "CD-B01";
        public static final String CD_B02 = "CD-B02";
        public static final String DMG_B01 = "DMG-B01";
        public static final String DMG_B02 = "DMG-B02";
        public static final String DMG_H01 = "DMG-H01";
        public static final String HP_B01 = "HP-B01";
        public static final String HP_B02 = "HP-B02";
        public static final String SHD_B01 = "SHD-B01";
        public static final String SHD_B02 = "SHD-B02";

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

        public boolean anyEnabled() {
            return this.cdb01 || this.cdb02 || this.dmgb01 || this.dmgb02 || this.dmgh01
                    || this.hpb01 || this.hpb02 || this.shdb01 || this.shdb02;
        }

        public boolean isEnabled(String code) {
            switch (code) {
                case CD_B01:
                    return this.cdb01;
                case CD_B02:
                    return this.cdb02;
                case DMG_B01:
                    return this.dmgb01;
                case DMG_B02:
                    return this.dmgb02;
                case DMG_H01:
                    return this.dmgh01;
                case HP_B01:
                    return this.hpb01;
                case HP_B02:
                    return this.hpb02;
                case SHD_B01:
                    return this.shdb01;
                case SHD_B02:
                    return this.shdb02;
                default:
                    return false;
            }
        }
    }

    /**
     * Subclass for Special configuration
     */
    public static class SpecialConfig {
        public static final String LUMINAFLUX_ALLOY = "resource_collectable_luminaflux-alloy";
        public static final String DSE_KEY_ACCESS = "resource_key_access-dse";
        public static final String DSE_KEY_GREEN = "resource_echo-key-green";
        public static final String DSE_KEY_BLUE = "resource_echo-key-blue";
        public static final String DSE_KEY_PURPLE = "resource_echo-key-purple";
        public static final String LOG_FILE = "resource_logfile";
        public static final String PIRATE_KEY_GREEN = "resource_booty-key";

        @Option("do_gamer.autobuy.special.luminafluxAlloy")
        @Number(max = 1_000, step = 1)
        public int luminafluxAlloy = 0;

        @Option("do_gamer.autobuy.special.dseKeyAccess")
        public PurchaseConfig dseKeyAccess = new PurchaseConfig();

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
        public PurchaseConfig logFile = new PurchaseConfig();

        @Option("do_gamer.autobuy.special.pirateKeyGreen")
        public PurchaseConfig pirateKeyGreen = new PurchaseConfig();

        public boolean anyEnabled() {
            return this.luminafluxAlloy > 0 || this.dseKeyAccess.amount > 0 || this.dseKeyGreen > 0
                    || this.dseKeyBlue > 0 || this.dseKeyPurple > 0 || this.logFile.amount > 0
                    || this.pirateKeyGreen.amount > 0;
        }

        public boolean isEnabled(String itemId) {
            return this.getAmountOfItem(itemId) > 0;
        }

        public int getAmountOfItem(String itemId) {
            switch (itemId) {
                case LUMINAFLUX_ALLOY:
                    return this.luminafluxAlloy;
                case DSE_KEY_ACCESS:
                    return this.dseKeyAccess.amount;
                case DSE_KEY_GREEN:
                    return this.dseKeyGreen;
                case DSE_KEY_BLUE:
                    return this.dseKeyBlue;
                case DSE_KEY_PURPLE:
                    return this.dseKeyPurple;
                case LOG_FILE:
                    return this.logFile.amount;
                case PIRATE_KEY_GREEN:
                    return this.pirateKeyGreen.amount;
                default:
                    return 0;
            }
        }

        public int getMinConditionForItem(String itemId) {
            switch (itemId) {
                case DSE_KEY_ACCESS:
                    return this.dseKeyAccess.min;
                case LOG_FILE:
                    return this.logFile.min;
                case PIRATE_KEY_GREEN:
                    return this.pirateKeyGreen.min;
                default:
                    return -1; // No condition for this item
            }
        }
    }

    /**
     * Subclass for purchase configuration
     */
    public static class PurchaseConfig {
        @Option("do_gamer.autobuy.purchase.amount")
        @Number(max = 10_000_000, step = 1)
        public int amount = 0;

        @Option("do_gamer.autobuy.purchase.min")
        @Number(max = 100_000, step = 1, min = -1)
        public int min = 0;
    }
}
