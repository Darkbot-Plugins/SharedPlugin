package dev.shared.maxim.petfuel.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration("maxim.pet_fuel")
public class PetFuelConfig {

    @Option("general.enabled")
    public boolean enabled = true;

    @Option("maxim.pet_fuel.min_fuel")
    @Number(min = 0, max = 1_000_000, step = 500)
    public int minFuel = 5_000;

    @Option("maxim.pet_fuel.buy_amount")
    @Number(min = 100, max = 100_000, step = 100)
    public int buyAmount = 4_000;

    @Option("maxim.pet_fuel.min_uri_reserve")
    @Number(min = 0, max = 10_000_000, step = 500)
    public int minUridiumReserve = 2_000;

    @Option("maxim.pet_fuel.check_interval")
    @Number(min = 1, max = 1_440, step = 1)
    public int checkIntervalMinutes = 15;

    @Option("maxim.pet_fuel.retry_delay")
    @Number(min = 5, max = 600, step = 5)
    public int retryDelaySeconds = 60;

    @Option("maxim.pet_fuel.verbose")
    public boolean verboseLogging = true;
}
