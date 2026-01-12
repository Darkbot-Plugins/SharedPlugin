package dev.shared.berti.types.enums;

import eu.darkbot.api.managers.GameResourcesAPI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum RestartOptions {
    RESTARTING("server_restarting"),
    RESTART_MIN("server_restart_n_minutes"),
    RESTART_SEC("server_restart_n_seconds"),
    SHUTDOWN_MIN("server_shutdown_n_minutes"),
    SHUTDOWN_SEC("server_shutdown_n_seconds");

    private static final Pattern SPECIAL_REGEX;
    private final String key;
    private Pattern pattern;
    private boolean hasTime;
    private int time = 0;

    private static String escapeRegex(String str) {
        return SPECIAL_REGEX.matcher(str).replaceAll("\\\\$0");
    }

    private RestartOptions(String key) {
        this.key = key;
    }

    public boolean matches(String log, GameResourcesAPI resManager) {
        if (this.pattern == null) {
            if (resManager == null) {
                return false;
            }
            String translation = resManager.findTranslation(this.key).orElse(null);
            if (translation == null || translation.isEmpty()) {
                return false;
            }
            this.hasTime = translation.contains("%!");
            this.pattern = Pattern.compile(RestartOptions.escapeRegex(translation).replace("%!", "\\{(?<time>[0-9]+)}"));
        }
        Matcher m = this.pattern.matcher(log);
        boolean matched = m.matches();
        if (this.hasTime && matched) {
            this.time = Integer.parseInt(m.group("time"));
        }
        return matched;
    }

    public int getTime() {
        return this.time;
    }

    public String toString() {
        return this.name() + (String)(this.hasTime ? " " + this.time : "");
    }

    static {
        SPECIAL_REGEX = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");
    }
}
