package dev.shared.fallwingas;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

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

    private static final Object PROFILE_STATE_LOCK = new Object();
    private static String stateFirstProfile;
    private static String stateSecondProfile;
    private static LocalDateTime lastProcessedHour;
    private static boolean usingSecondProfile;
    private static int deathsAtSwitch;

    private final ConfigAPI configApi;
    private final BackpageAPI backpageApi;
    private final RepairAPI repairApi;

    private HourlyProfileSwitcherConfig config;

    public HourlyProfileSwitcher(PluginAPI api) {
        this.configApi = api.requireAPI(ConfigAPI.class);
        this.backpageApi = api.requireAPI(BackpageAPI.class);
        this.repairApi = api.requireAPI(RepairAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<HourlyProfileSwitcherConfig> setting) {
        this.config = setting.getValue();
    }

    @Override
    public void onTickTask() {
        // Scheduling is handled by the behavior callbacks.
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
        HourlyProfileSwitcherConfig currentConfig = this.config;
        if (currentConfig == null) {
            return;
        }

        LocalDateTime now = currentConfig.timeSource == HourlyProfileSwitcherConfig.TimeSource.SERVER
                ? ServerTimeHelper.currentDateTime()
                : LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime thisHour = now.withMinute(0).withSecond(0).withNano(0);

        synchronized (PROFILE_STATE_LOCK) {
            String activeProfile = this.configApi.getCurrentProfile();
            this.initializeProfileState(currentConfig, activeProfile);

            // Use stored names/state so a profile-specific config change does not lose the fallback.
            if (usingSecondProfile
                    && Objects.equals(activeProfile, stateSecondProfile)
                    && this.repairApi.getDeathAmount() > deathsAtSwitch) {
                this.configApi.setConfigProfile(stateFirstProfile);
                usingSecondProfile = false;
                lastProcessedHour = thisHour;
                return;
            }

            if (!currentConfig.enabled
                    || currentConfig.firstProfile == null
                    || currentConfig.secondProfile == null
                    || usingSecondProfile
                    || !Objects.equals(activeProfile, currentConfig.firstProfile)) {
                return;
            }

            if (now.getMinute() < currentConfig.minute || thisHour.equals(lastProcessedHour)) {
                return;
            }

            int deathsBeforeSwitch = this.repairApi.getDeathAmount();
            if (this.repairApi.isDestroyed()
                    || this.repairApi.getDeathAmount() != deathsBeforeSwitch) {
                // Leave this hour pending so the scheduled switch can retry after the ship revives.
                return;
            }

            // Set state before requesting the profile change; DarkBot may apply it on the next tick.
            stateFirstProfile = currentConfig.firstProfile;
            stateSecondProfile = currentConfig.secondProfile;
            deathsAtSwitch = deathsBeforeSwitch;
            usingSecondProfile = true;
            lastProcessedHour = thisHour;
            this.configApi.setConfigProfile(stateSecondProfile);
        }
    }

    private void initializeProfileState(HourlyProfileSwitcherConfig currentConfig, String activeProfile) {
        String firstProfile = currentConfig.firstProfile;
        String secondProfile = currentConfig.secondProfile;
        if (firstProfile == null || secondProfile == null) {
            return;
        }

        if (!Objects.equals(firstProfile, stateFirstProfile)
                || !Objects.equals(secondProfile, stateSecondProfile)) {
            stateFirstProfile = firstProfile;
            stateSecondProfile = secondProfile;
            usingSecondProfile = Objects.equals(activeProfile, secondProfile);
            deathsAtSwitch = this.repairApi.getDeathAmount();
        } else if (!usingSecondProfile && Objects.equals(activeProfile, secondProfile)) {
            // Recover after the feature instance is recreated while the secondary profile is active.
            usingSecondProfile = true;
            deathsAtSwitch = this.repairApi.getDeathAmount();
        } else if (usingSecondProfile && Objects.equals(activeProfile, firstProfile)) {
            // The profile was changed manually; stop treating the old switch as active.
            usingSecondProfile = false;
        }
    }
}
