package dev.shared.kopoklesz.config;

import eu.darkbot.api.config.annotations.Option; // checkbox 
import eu.darkbot.api.config.annotations.Percentage; // percent
import eu.darkbot.api.config.annotations.Number; // number

public class AutoRefinConfig {
    @Option("general.enabled")
    public boolean enabled = true;

    @Option("kopoklesz.auto_refin.trigger") // cargo trigger percent
    @Percentage
    public double triggerPercent = 0.75;

    @Option("kopoklesz.auto_refin.xenomit_reserve") // xenomit reserve
    @Number(min = 0, max = 10_000_000, step = 1000)
    public int xenomitReserve = 0;

    @Option("kopoklesz.auto_refin.ores") // allowed ores
    public AllowOres ores = new AllowOres();

    public static class AllowOres {
        @Option("kopoklesz.auto_refin.ores.prometid")
        public boolean prometid = true;

        @Option("kopoklesz.auto_refin.ores.duranium")
        public boolean duranium = true;

        @Option("kopoklesz.auto_refin.ores.promerium")
        public boolean promerium = true;
    }
}
