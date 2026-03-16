package dev.shared.orbithelper.module.daily_quest.handlers;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import eu.darkbot.api.config.types.SafetyInfo;
import eu.darkbot.api.config.legacy.Config;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.GameMap;

import java.util.Collection;

public class SafetyHandler {
    private final DailyQuestModule module;
    private boolean fleeing = false;

    public SafetyHandler(DailyQuestModule module) {
        this.module = module;
    }

    public boolean handleSafety() {
        if (module.getRepairHpRange() == null || module.getRepairMode() == null)
            return false;

        double hp = module.hero.getHealth().hpPercent();
        double minHp = module.getRepairHpRange().getValue().getMin();
        double maxHp = module.getRepairHpRange().getValue().getMax();

        if (!fleeing && hp >= minHp)
            return false;

        if (fleeing && hp >= maxHp) {
            fleeing = false;
            return false;
        }

        if (!fleeing) {
            fleeing = true;
        }

        module.hero.setMode(module.getRepairMode().getValue());
        moveToNearestSafety();
        return true;
    }

    private void moveToNearestSafety() {
        try {
            Config legacy = module.configApi.getLegacy();
            GameMap currentMap = module.starSystem.getCurrentMap();
            Collection<? extends SafetyInfo> safeties = legacy.getSafeties(currentMap);

            if (safeties == null || safeties.isEmpty()) return;

            SafetyInfo nearest = safeties.stream()
                    .filter(s -> s.getRunMode() != SafetyInfo.RunMode.NEVER
                            && s.getRunMode() != SafetyInfo.RunMode.ENEMY_FLEE_ONLY)
                    .min(java.util.Comparator.comparingDouble(
                            s -> module.movement.getClosestDistance(s.getX(), s.getY())))
                    .orElse(null);

            if (nearest == null) return;

            nearest.getEntity().ifPresentOrElse(
                    entity -> module.movement.moveTo(entity),
                    () -> module.movement.moveTo(nearest.getX(), nearest.getY()));

            double dist = module.movement.getClosestDistance(nearest.getX(), nearest.getY());
            if (nearest.getType() == SafetyInfo.Type.PORTAL && dist < 300) {
                nearest.getEntity().ifPresent(entity -> {
                    if (entity instanceof Portal) {
                        module.movement.jumpPortal((Portal) entity);
                    }
                });
            }
        } catch (Exception ignored) { // safety system unavailable
        }
    }
}