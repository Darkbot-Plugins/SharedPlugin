package dev.shared.canda.remote.control;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteCommandParser {
    private static final Pattern JSON_ACTION = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_PARAMETER = Pattern.compile("\"parameter\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern JSON_REQUEST_ID = Pattern.compile("\"requestId\"\\s*:\\s*\"([^\"]*)\"");

    private RemoteCommandParser() {
    }

    public static Optional<RemoteCommand> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }

        if (value.startsWith("{") && value.endsWith("}")) {
            return parseJsonLike(value);
        }

        // Fallback text format:
        // start
        // set_module:npc_killer
        String[] parts = value.split(":", 2);
        CommandAction action = CommandAction.from(parts[0]);
        String parameter = parts.length > 1 ? parts[1] : "";
        return Optional.of(new RemoteCommand("", action, parameter));
    }

    private static Optional<RemoteCommand> parseJsonLike(String raw) {
        String action = extract(JSON_ACTION, raw);
        if (action.isEmpty()) {
            return Optional.empty();
        }
        String parameter = extract(JSON_PARAMETER, raw);
        String requestId = extract(JSON_REQUEST_ID, raw);
        return Optional.of(new RemoteCommand(requestId, CommandAction.from(action), parameter));
    }

    private static String extract(Pattern pattern, String raw) {
        Matcher matcher = pattern.matcher(raw);
        return matcher.find() ? matcher.group(1) : "";
    }
}
