package dev.shared.halizeur.death_rate_switcher;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.RepairAPI;

import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

@Feature(name = "Death Rate Profile Switcher", description =
        "Switches to a safer config profile when deaths/hour spike (PvP hunting), and reverts after a quiet hour.",
        enabledByDefault = true)
public class DeathRateProfileSwitcher implements Behavior, Configurable<DeathRateProfileSwitcher.Config> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Duration WINDOW = Duration.ofMinutes(60);

    private final ConfigAPI configAPI;
    private final RepairAPI repair;

    private Config config = new Config();

    private final Deque<Instant> recentDeaths = new ArrayDeque<>();
    private boolean wasDestroyed = false;

    private Instant revertAt = null;

    public DeathRateProfileSwitcher(ConfigAPI configAPI, RepairAPI repair) {
        this.configAPI = configAPI;
        this.repair = repair;
    }

    @Configuration("death_rate_profile_switcher.config")
    public static class Config {
        @Dropdown(options = ProfileConfigOptions.class)
        public String FROM_PROFILE = "";

        @Dropdown(options = ProfileConfigOptions.class)
        public String TO_PROFILE = "";

        @Number(min = 1, max = 100, step = 1)
        public int DEATHS_PER_HOUR_THRESHOLD = 20;
    }

    public static class ProfileConfigOptions implements Dropdown.Options<String> {
        private final ConfigAPI configAPI;

        public ProfileConfigOptions(ConfigAPI configAPI) {
            this.configAPI = configAPI;
        }

        @Override
        public Collection<String> options() {
            return configAPI.getConfigProfiles();
        }

        @Override
        public String getText(String option) {
            return option == null || option.isEmpty() ? "(none)" : option;
        }
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        process();
    }

    @Override
    public void onStoppedBehavior() {
        process();
    }

    private void process() {
        if (config.FROM_PROFILE.isEmpty() || config.TO_PROFILE.isEmpty()) return;

        trackDeathEdge();
        pruneOldDeaths();

        String current = configAPI.getCurrentProfile();

        if (revertAt == null) {
            if (config.FROM_PROFILE.equals(current) && recentDeaths.size() >= config.DEATHS_PER_HOUR_THRESHOLD) {
                log("Deaths/hour reached " + recentDeaths.size() + " (threshold " + config.DEATHS_PER_HOUR_THRESHOLD +
                        ") on '" + config.FROM_PROFILE + "'. Switching to '" + config.TO_PROFILE + "' for 60 minutes.");
                configAPI.setConfigProfile(config.TO_PROFILE);
                revertAt = Instant.now().plus(WINDOW);
                recentDeaths.clear();
            }
