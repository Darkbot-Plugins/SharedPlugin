package dev.shared.fallwingas;

import java.util.Arrays;
import java.util.Collection;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.shared.config.ProfileNames;

@Configuration("fallwingas.hourly_profile_switcher")
public class HourlyProfileSwitcherConfig {

    @Option("general.enabled")
    public boolean enabled = false;

    @Option("fallwingas.hourly_profile_switcher.first_profile")
    @Dropdown(options = ProfileOptions.class)
    public String firstProfile;

    @Option("fallwingas.hourly_profile_switcher.second_profile")
    @Dropdown(options = ProfileOptions.class)
    public String secondProfile;

    @Option("fallwingas.hourly_profile_switcher.minute")
    @Number(min = 0, max = 59, step = 1)
    public int minute = 0;

    @Option("fallwingas.hourly_profile_switcher.time_source")
    @Dropdown(options = TimeSourceOptions.class)
    public TimeSource timeSource = TimeSource.LOCAL;

    public enum TimeSource {
        LOCAL,
        SERVER
    }

    public static class ProfileOptions extends ProfileNames {
        public ProfileOptions(ConfigAPI api) {
            super(api);
        }
    }

    public static class TimeSourceOptions implements Dropdown.Options<TimeSource> {
        @Override
        public Collection<TimeSource> options() {
            return Arrays.asList(TimeSource.values());
        }

        @Override
        public String getText(TimeSource option) {
            return option == null ? "" : option == TimeSource.SERVER ? "Server time" : "Local time";
        }
    }
}
