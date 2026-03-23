package dev.shared.canda.remote;

import dev.shared.canda.remote.config.RemotePanelConfig;
import dev.shared.canda.remote.control.CommandAction;
import dev.shared.canda.remote.control.RemoteCommand;
import dev.shared.canda.remote.control.RemoteCommandParser;
import dev.shared.canda.remote.telemetry.RemoteSnapshot;
import dev.shared.canda.remote.transport.CommandReceiver;
import dev.shared.canda.remote.transport.HttpCommandReceiver;
import dev.shared.canda.remote.transport.HttpTelemetrySender;
import dev.shared.canda.remote.transport.TelemetrySender;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.InstructionProvider;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Health;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;

@Feature(name = "Remote Panel", description = "Telemetry and command bridge for web panel control")
public class RemotePanel implements Task, Configurable<RemotePanelConfig>, InstructionProvider {
    private final BotAPI bot;
    private final HeroAPI hero;
    private final ConfigAPI configApi;
    private final StarSystemAPI starSystem;
    private final StatsAPI stats;
    private final TelemetrySender telemetrySender;
    private final CommandReceiver commandReceiver;

    private RemotePanelConfig config;
    private long nextTelemetryAt = 0L;
    private long nextCommandPollAt = 0L;
    private String lastRequestId = "";

    public RemotePanel(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
        this.telemetrySender = new HttpTelemetrySender();
        this.commandReceiver = new HttpCommandReceiver();
    }

    @Override
    public String instructions() {
        return "Set telemetry_url + command_url, then enable the feature. "
                + "Bot public id is automatically derived from the pilot name.";
    }

    @Override
    public void onTickTask() {
        if (this.config == null || !this.config.enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        String botPublicId = this.getBotPublicId();
        if (now >= this.nextTelemetryAt) {
            this.telemetrySender.send(this.config, this.buildSnapshot(now, botPublicId));
            this.nextTelemetryAt = now + (this.config.telemetryIntervalSeconds * 1000L);
        }

        if (now >= this.nextCommandPollAt) {
            this.commandReceiver.poll(this.config, botPublicId)
                    .flatMap(RemoteCommandParser::parse)
                    .ifPresent(this::applyCommand);
            this.nextCommandPollAt = now + this.config.commandPollIntervalMillis;
        }
    }

    private RemoteSnapshot buildSnapshot(long tick, String botPublicId) {
        RemoteSnapshot snapshot = new RemoteSnapshot();
        snapshot.setTick(tick);
        snapshot.setBotPublicId(botPublicId);
        snapshot.setHeroId(this.hero.getId());
        snapshot.setUsername(this.hero.getEntityInfo() == null ? "" : this.hero.getEntityInfo().getUsername());
        snapshot.setBotRunning(this.bot.getModule() != null);
        snapshot.setModuleStatus(this.bot.getModule() == null ? "-" : this.bot.getModule().getStatus());
        snapshot.setModuleId(this.getCurrentModuleId());

        GameMap map = this.starSystem.getCurrentMap();
        snapshot.setMapId(map == null ? -1 : map.getId());
        snapshot.setMapName(map == null ? "-" : map.getShortName());

        Health health = this.hero.getHealth();
        snapshot.setHpPercent(health == null ? 0.0 : health.hpPercent());
        snapshot.setShieldPercent(health == null ? 0.0 : health.shieldPercent());
        return snapshot;
    }

    private void applyCommand(RemoteCommand command) {
        if (command == null || command.getAction() == CommandAction.NOOP) {
            return;
        }

        if (!command.getRequestId().isEmpty() && command.getRequestId().equals(this.lastRequestId)) {
            return;
        }
        this.lastRequestId = command.getRequestId();

        switch (command.getAction()) {
            case START:
                this.bot.setRunning(true);
                break;
            case STOP:
                this.bot.setRunning(false);
                break;
            case SET_MODULE:
                this.setCurrentModule(command.getParameter());
                break;
            case SET_MAP:
                this.setWorkingMap(command.getParameter());
                break;
            case REFRESH:
                this.bot.handleRefresh();
                break;
            case RESET_STATS:
                this.stats.resetStats();
                break;
            default:
                break;
        }
    }

    private void setCurrentModule(String moduleId) {
        if (moduleId == null || moduleId.trim().isEmpty()) {
            return;
        }
        this.configApi.requireConfig("general.current_module").setValue(moduleId.trim());
    }

    private void setWorkingMap(String mapValue) {
        if (mapValue == null || mapValue.trim().isEmpty()) {
            return;
        }
        try {
            int mapId = Integer.parseInt(mapValue.trim());
            this.configApi.requireConfig("general.working_map").setValue(mapId);
        } catch (NumberFormatException ignored) {
            // Invalid value should be ignored, backend must validate values.
        }
    }

    private String getCurrentModuleId() {
        Object moduleValue = this.configApi.requireConfig("general.current_module").getValue();
        return moduleValue == null ? "" : String.valueOf(moduleValue);
    }

    private String getBotPublicId() {
        if (this.hero.getEntityInfo() != null && this.hero.getEntityInfo().getUsername() != null) {
            String username = this.hero.getEntityInfo().getUsername().trim();
            if (!username.isEmpty()) return username;
        }
        return String.valueOf(this.hero.getId());
    }

    @Override
    public void setConfig(ConfigSetting<RemotePanelConfig> config) {
        this.config = config.getValue();
        if (this.config.commandPollIntervalMillis < 250) {
            this.config.commandPollIntervalMillis = 250;
        }
        if (this.config.telemetryIntervalSeconds < 1) {
            this.config.telemetryIntervalSeconds = 1;
        }
    }
}
