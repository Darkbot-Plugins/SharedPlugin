package dev.shared.canda.remote.control;

public class RemoteCommand {
    private final String requestId;
    private final CommandAction action;
    private final String parameter;

    public RemoteCommand(String requestId, CommandAction action, String parameter) {
        this.requestId = requestId == null ? "" : requestId.trim();
        this.action = action == null ? CommandAction.NOOP : action;
        this.parameter = parameter == null ? "" : parameter.trim();
    }

    public String getRequestId() {
        return requestId;
    }

    public CommandAction getAction() {
        return action;
    }

    public String getParameter() {
        return parameter;
    }
}
