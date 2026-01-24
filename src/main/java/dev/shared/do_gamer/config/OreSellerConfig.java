package dev.shared.do_gamer.config;

import java.util.Arrays;
import java.util.List;

import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;

public class OreSellerConfig {
    @Option("general.enabled")
    public boolean enabled = true;

    @Option("do_gamer.ore_seller.mode")
    @Dropdown(options = SellModeOptions.class)
    public String mode = SellModeOptions.BASE;

    @Option("do_gamer.ore_seller.trigger")
    @Percentage
    public double triggerPercent = 0.9;

    @Option("do_gamer.ore_seller.cooldown_seconds")
    @Number(min = 0, max = 600, step = 5)
    public int cooldownSeconds = 30;

    @Option("do_gamer.ore_seller.base")
    public BaseConfig base = new BaseConfig();

    @Option("do_gamer.ore_seller.pet")
    public PetConfig pet = new PetConfig();

    @Option("do_gamer.ore_seller.drone")
    public DroneConfig drone = new DroneConfig();

    @Option("do_gamer.ore_seller.ores")
    public OresConfig ores = new OresConfig();

    public static class SellModeOptions implements Dropdown.Options<String> {
        public static final String BASE = "Base selling";
        public static final String PET = "PET trading";
        public static final String DRONE = "HM7 trade drone";

        @Override
        public List<String> options() {
            return Arrays.asList(BASE, PET, DRONE);
        }
    }

    public static class TradeMapOptions implements Dropdown.Options<String> {
        public static final String X1 = "X-1";
        public static final String X8 = "X-8";
        public static final String FIVE_TWO = "5-2";

        @Override
        public List<String> options() {
            return Arrays.asList(X1, X8, FIVE_TWO);
        }
    }

    public static class BaseConfig {
        @Option("do_gamer.ore_seller.base.map")
        @Dropdown(options = TradeMapOptions.class)
        public String map = TradeMapOptions.X1;

        @Option("do_gamer.ore_seller.max_wait")
        @Number(min = 10, max = 300, step = 5)
        public int maxWaitSeconds = 90;
    }

    public static class PetConfig {
        @Option("do_gamer.ore_seller.pet.keep_enabled")
        public boolean keepEnabled = true;

        @Option("do_gamer.ore_seller.activation_delay")
        @Number(min = 250, max = 2_000, step = 50)
        public int activationDelayMs = 750;

        @Option("do_gamer.ore_seller.max_wait")
        @Number(min = 5, max = 180, step = 5)
        public int maxWaitSeconds = 60;
    }

    public static class DroneConfig {
        @Option("do_gamer.ore_seller.activation_delay")
        @Number(min = 250, max = 2_000, step = 50)
        public int activationDelayMs = 750;

        @Option("do_gamer.ore_seller.max_wait")
        @Number(min = 5, max = 180, step = 5)
        public int maxWaitSeconds = 60;
    }

    public static class OresConfig {
        @Option("do_gamer.ore_seller.ores.prometium")
        public boolean prometium = true;

        @Option("do_gamer.ore_seller.ores.endurium")
        public boolean endurium = true;

        @Option("do_gamer.ore_seller.ores.terbium")
        public boolean terbium = true;

        @Option("do_gamer.ore_seller.ores.prometid")
        public boolean prometid = true;

        @Option("do_gamer.ore_seller.ores.duranium")
        public boolean duranium = true;

        @Option("do_gamer.ore_seller.ores.promerium")
        public boolean promerium = true;

        @Option("do_gamer.ore_seller.ores.palladium")
        public boolean palladium = false;
    }
}
