package dev.shared.canda.remote.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration("canda.remote_panel")
public class RemotePanelConfig {
    @Option("general.enabled")
    public boolean enabled = false;

    @Option("canda.remote_panel.telemetry_interval_seconds")
    @Number(min = 1, max = 120, step = 1)
    public int telemetryIntervalSeconds = 5;

    @Option("canda.remote_panel.command_poll_interval_millis")
    @Number(min = 250, max = 10000, step = 50)
    public int commandPollIntervalMillis = 1000;

    @Option("canda.remote_panel.telemetry_url")
    public String telemetryUrl = "";

    @Option("canda.remote_panel.command_url")
    public String commandUrl = "";

    @Option("canda.remote_panel.api_token")
    public String apiToken = "";
}
