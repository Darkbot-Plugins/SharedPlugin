package dev.shared.do_gamer.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.shared.config.ProfileNames;

public class SpaceballConfig {
    public static class ModeOptions implements Dropdown.Options<String> {
        public static final String AUTO = "Auto";
        public static final String ATTACK = "Attack";
        public static final String FOLLOW = "Follow";

        @Override
        public List<String> options() {
            return Arrays.asList(AUTO, ATTACK, FOLLOW);
        }
    }

    public static class ProfileOptions extends ProfileNames {
        public ProfileOptions(ConfigAPI api) {
            super(api);
        }

        @Override
        public Collection<String> options() {
            // Start with an explicit null option so the dropdown allows no-selection.
            List<String> list = new ArrayList<>();
            list.add(null);
            Collection<String> parent = super.options();
            if (parent != null) {
                list.addAll(parent);
            }
            return list;
        }
    }

    @Option("do_gamer.spaceball.config.mode")
    @Dropdown(options = ModeOptions.class)
    public String mode = ModeOptions.AUTO;

    @Option("do_gamer.spaceball.config.pet_assist")
    public boolean petAssist = true;

    @Option("do_gamer.spaceball.config.time")
    public TimeConfig time = new TimeConfig();

    @Option("do_gamer.spaceball.config.other")
    public OtherConfig other = new OtherConfig();

    public static class TimeConfig {
        @Option("do_gamer.spaceball.config.time.start_hour")
        @Number(min = 0, max = 23, step = 1)
        public int startHour = 19;

        @Option("do_gamer.spaceball.config.time.stop_hour")
        @Number(min = 0, max = 23, step = 1)
        public int stopHour = 22;
    }

    public static class OtherConfig {
        @Option("do_gamer.spaceball.config.other.target_delay")
        @Number(min = 1, max = 60)
        public int targetDelay = 20;

        @Option("do_gamer.spaceball.config.other.bot_profile")
        @Dropdown(options = ProfileOptions.class)
        public String botProfile = null;
    }

}