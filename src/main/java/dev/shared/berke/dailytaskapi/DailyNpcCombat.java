package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.shared.modules.LootModule;

import java.util.Comparator;

/**
 * DarkBot's standard NPC combat engine with target selection restricted to
 * the NPC required by the currently displayed daily quest.
 *
 * <p>The inherited implementation supplies the saved NPC radius, circle and
 * run-configuration behaviour, ammunition selection, target validation and
 * SafetyFinder logic used by NPC Kill and Collect. DailyTaskAPI remains the
 * owner of quest and map selection.</p>
 */
final class DailyNpcCombat extends LootModule {
    private final ConfigSetting<Boolean> petEnabledSetting;
    private final ConfigSetting<Boolean> keepRoamingPointSetting;
    private String targetDescription;
    private Boolean originalPetEnabledSetting;
    private Boolean originalKeepRoamingPointSetting;
    private Boolean originalPetRuntimeEnabled;

    DailyNpcCombat(PluginAPI api) {
        super(api);
        ConfigAPI config = api.requireAPI(ConfigAPI.class);
        petEnabledSetting = config.requireConfig("pet.enabled");
        keepRoamingPointSetting = config.requireConfig("general.roaming.keep");
    }

    void tick(String description) {
        targetDescription = description;
        rememberUserSettings();
        if (!Boolean.TRUE.equals(petEnabledSetting.getValue())) petEnabledSetting.setValue(true);
        super.onTickModule();
    }

    void stopCombat() {
        attack.stopAttack();
        attack.setTarget(null);
        targetDescription = null;
        restoreUserSettings();
    }

    void shutdownPet() {
        stopCombat();
        if (pet.isEnabled()) pet.setEnabled(false);
    }

    boolean isPetActive() {
        return pet.isActive();
    }

    @Override
    protected boolean checkMap() {
        // DailyTaskAPI resolves and navigates to the quest's required map.
        // Do not let the saved working-map setting override that choice.
        return true;
    }

    @Override
    protected boolean findTarget() {
        Npc current = attack.getTargetAs(Npc.class);
        if (isUsableTarget(current)) return true;

        if (current != null) {
            attack.stopAttack();
            attack.setTarget(null);
        }

        Npc selected = npcs.stream()
                .filter(this::isUsableTarget)
                .min(Comparator.comparingDouble(hero::distanceTo))
                .orElse(null);
        attack.setTarget(selected);
        // MovementAPI.moveRandom follows the saved preferred-zone route for
        // this map and falls back to a random map point when no route exists.
        if (selected == null && !movement.isMoving()) {
            rememberUserSettings();
            if (Boolean.TRUE.equals(keepRoamingPointSetting.getValue())) {
                keepRoamingPointSetting.setValue(false);
            }
            movement.moveRandom();
        }
        return selected != null;
    }

    private void rememberUserSettings() {
        if (originalPetEnabledSetting != null) return;
        originalPetEnabledSetting = petEnabledSetting.getValue();
        originalKeepRoamingPointSetting = keepRoamingPointSetting.getValue();
        originalPetRuntimeEnabled = pet.isEnabled();
    }

    private void restoreUserSettings() {
        if (originalPetEnabledSetting == null) return;
        petEnabledSetting.setValue(originalPetEnabledSetting);
        keepRoamingPointSetting.setValue(originalKeepRoamingPointSetting);
        pet.setEnabled(Boolean.TRUE.equals(originalPetRuntimeEnabled));
        originalPetEnabledSetting = null;
        originalKeepRoamingPointSetting = null;
        originalPetRuntimeEnabled = null;
    }

    private boolean isUsableTarget(Npc npc) {
        if (npc == null || targetDescription == null ||
                !npc.isValid() || !npc.isSelectable() || npc.isBlacklisted()) {
            return false;
        }
        String name = npc.getInfo() == null ? null : npc.getInfo().getName();
        if (name == null || movement.getClosestDistance(npc) >= 590d) return false;

        String target = DailyTaskPlanner.normalizeNpcName(targetDescription);
        String candidate = DailyTaskPlanner.normalizeNpcName(name);
        boolean wantsBoss = target.contains("boss");
        boolean wantsUber = target.contains("uber");
        return !candidate.isBlank() && target.contains(candidate) &&
                wantsBoss == candidate.startsWith("boss ") &&
                wantsUber == candidate.startsWith("uber");
    }
}
