package dev.shared.canda.remote.control;

public enum CommandAction {
    START,
    STOP,
    SET_MODULE,
    SET_MAP,
    REFRESH,
    RESET_STATS,
    NOOP;

    public static CommandAction from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NOOP;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        try {
            return CommandAction.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NOOP;
        }
    }
}
