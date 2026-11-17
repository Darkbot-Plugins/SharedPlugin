package dev.shared.fallwingas;

import java.time.LocalDateTime;

import dev.shared.do_gamer.utils.ServerTimeHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.RepairAPI;

@Feature(
        name = "Hourly Profile Switcher",
        description = "Switches to a second profile each hour and returns to the first after a death."
)
public class HourlyProfileSwitcher
        implements Behavior, Task, Configurable<HourlyProfileSwitcherConfig> {

    private final ConfigAPI configApi;
    private final BackpageAPI backpageApi;
    private final RepairAPI repairApi;

    private HourlyProfileSwitcherConfig config;
    private LocalDateTime lastProcessedHour;
    private boolean usingSecondProfile;
    private int deathsAtSwitch;

    public HourlyProfileSwitcher(PluginAPI api) {
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.backpageApi = api.requireAPI(BackpageAPI.class);
        this.repairApi = api.requireAPI(RepairAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<HourlyProfileSwitcherConfig> setting) {
        this.config = setting.getValue();
    }@Override
public void onTickTask() {
    // La comprobación del horario se realiza en los métodos Behavior.
}

    @Override
    public void onBackgroundTick() {
        if (this.config != null
                && this.config.timeSource == HourlyProfileSwitcherConfig.TimeSource.SERVER) {
            ServerTimeHelper.fetchServerOffset(this.backpageApi);
        }
    }

    @Override
    public void onTickBehavior() {
        this.checkSchedule();
    }

    @Override
    public void onStoppedBehavior() {
        this.checkSchedule();
    }

    private void checkSchedule() {
        if (this.config == null || !this.config.enabled
                || this.config.firstProfile == null
                || this.config.secondProfile == null) {
            return;
        }

        LocalDateTime now;
        if (this.config.timeSource == HourlyProfileSwitcherConfig.TimeSource.SERVER) {
            if (!ServerTimeHelper.offsetUpdated()) {
                return;
            }
            now = ServerTimeHelper.currentDateTime();
        } else {
            now = LocalDateTime.now();
        }

        // If config2 is active, return to config1 after a new death.
        if (this.usingSecondProfile
                && this.repairApi.getDeathAmount() > this.deathsAtSwitch) {
            this.configApi.setConfigProfile(this.config.firstProfile);
            this.usingSecondProfile = false;
            return;
        }

        // Switch once per hour, when the chosen minute has arrived.
        if (now.getMinute() < this.config.minute) {
            return;
        }

        LocalDateTime thisHour = now.withMinute(0).withSecond(0).withNano(0);
        if (thisHour.equals(this.lastProcessedHour)) {
            return;
        }

        this.lastProcessedHour = thisHour;

        // If already destroyed when the scheduled minute arrives, stay on config1.
        if (this.repairApi.isDestroyed()) {
            return;
        }

        this.deathsAtSwitch = this.repairApi.getDeathAmount();
        this.usingSecondProfile = true;
        this.configApi.setConfigProfile(this.config.secondProfile);
    }
}
