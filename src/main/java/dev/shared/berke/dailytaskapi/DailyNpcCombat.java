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
    private String targetDescription;

    DailyNpcCombat(PluginAPI api) {
        super(api);
    }

    void tick(String description) {
        targetDescription = description;
        ConfigSetting<Boolean> petEnabled = api.requireAPI(ConfigAPI.class).requireConfig("pet.enabled");
        if (!Boolean.TRUE.equals(petEnabled.getValue())) petEnabled.setValue(true);
        super.onTickModule();
    }

    void stopCombat() {
        attack.stopAttack();
        attack.setTarget(null);
        targetDescription = null;
        if (pet.isEnabled()) pet.setEnabled(false);
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
            ConfigSetting<Boolean> keepPoint = api.requireAPI(ConfigAPI.class)
                    .requireConfig("general.roaming.keep");
            if (Boolean.TRUE.equals(keepPoint.getValue())) keepPoint.setValue(false);
            movement.moveRandom();
        }
        return selected != null;
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
