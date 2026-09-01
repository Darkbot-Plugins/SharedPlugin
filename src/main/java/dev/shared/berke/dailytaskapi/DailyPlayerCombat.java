package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;

import java.util.Comparator;

/** Executes opt-in player-combat daily objectives with conservative filters. */
final class DailyPlayerCombat {
    private final EntitiesAPI entities;
    private final GroupAPI group;
    private final MovementAPI movement;
    private final AttackAPI attack;
    private final HeroAPI hero;

    private long searchStartedAt;
    private long nextRoamAt;
    private int deaths;
    private boolean previouslyAlive = true;
    private String status = "waiting";

    DailyPlayerCombat(PluginAPI api) {
        entities = api.requireAPI(EntitiesAPI.class);
        group = api.requireAPI(GroupAPI.class);
        movement = api.requireAPI(MovementAPI.class);
        attack = api.requireAPI(AttackAPI.class);
        hero = api.requireAPI(HeroAPI.class);
    }

    void startQuest() {
        searchStartedAt = 0L;
        nextRoamAt = 0L;
        deaths = 0;
        previouslyAlive = hero.getHealth().hpPercent() > 0d;
        status = "searching for an eligible player";
    }

    void tick(String objectiveDescription, DailyTaskConfig config) {
        long now = System.currentTimeMillis();
        observeHeroState();
        if (hero.getHealth().hpPercent() <= 0d) {
            stopAttackOnly();
            status = "waiting for ship recovery";
            return;
        }

        Player target = attack.getTargetAs(Player.class);
        if (!isEligible(target, objectiveDescription, config)) {
            stopAttackOnly();
            target = entities.getPlayers().stream()
                    .filter(player -> isEligible(player, objectiveDescription, config))
                    .min(playerComparator(config))
                    .orElse(null);
            attack.setTarget(target);
        }

        if (target == null) {
            if (searchStartedAt == 0L) searchStartedAt = now;
            if (!movement.isMoving() || now >= nextRoamAt) {
                movement.moveRandom();
                nextRoamAt = now + 2_500L;
            }
            status = "searching for an eligible enemy player";
            return;
        }

        searchStartedAt = 0L;
        double distance = hero.distanceTo(target);
        if (distance > config.playerAttackRadius) {
            hero.setRoamMode();
            movement.moveTo(target);
            status = "approaching player at " + (int) Math.round(distance);
            return;
        }

        movement.stop(false);
        attack.tryLockAndAttack();
        status = "attacking eligible enemy player";
    }

    boolean exceededSafetyLimits(DailyTaskConfig config) {
        boolean deathLimit = config.maxPlayerDeaths > 0 && deaths >= config.maxPlayerDeaths;
        boolean searchLimit = searchStartedAt > 0L &&
                System.currentTimeMillis() - searchStartedAt >= config.playerSearchTimeoutSeconds * 1_000L;
        return deathLimit || searchLimit;
    }

    void observeHeroState() {
        observeDeath();
    }

    String failureReason(DailyTaskConfig config) {
        if (config.maxPlayerDeaths > 0 && deaths >= config.maxPlayerDeaths) {
            return "player-combat death limit reached (" + deaths + ")";
        }
        return "no eligible player found before the search timeout";
    }

    String getStatus() {
        return status + " | deaths: " + deaths;
    }

    void stopCombat() {
        stopAttackOnly();
        searchStartedAt = 0L;
    }

    private void stopAttackOnly() {
        if (attack.getTargetAs(Player.class) != null) {
            attack.stopAttack();
            attack.setTarget(null);
        }
    }

    private void observeDeath() {
        boolean alive = hero.getHealth().hpPercent() > 0d;
        if (!alive && previouslyAlive) deaths++;
        previouslyAlive = alive;
    }

    private Comparator<Player> playerComparator(DailyTaskConfig config) {
        Comparator<Player> distance = Comparator.comparingDouble(hero::distanceTo);
        if (!config.prioritizePlayersAttackingMe) return distance;
        return Comparator.comparing((Player player) -> !player.isAttacking(hero)).thenComparing(distance);
    }

    private boolean isEligible(Player player, String objectiveDescription, DailyTaskConfig config) {
        if (player == null || !player.isValid() || !player.isSelectable() ||
                player.isInvisible() || player.isBlacklisted() || player.getId() == hero.getId()) {
            return false;
        }
        EntityInfo info = player.getEntityInfo();
        if (info == null) return false;
        if (!matchesRequiredFaction(info, objectiveDescription)) return false;
        if (config.avoidAlliedAndGroupPlayers && isProtected(player, info)) return false;
        return !config.enemyPlayersOnly || info.isEnemy() ||
                info.getClanDiplomacy() == EntityInfo.Diplomacy.WAR;
    }

    private boolean matchesRequiredFaction(EntityInfo info, String description) {
        String normalized = DailyTaskPlanner.normalize(description);
        if (normalized.contains("mmo")) return info.getFaction() == EntityInfo.Faction.MMO;
        if (normalized.contains("eic")) return info.getFaction() == EntityInfo.Faction.EIC;
        if (normalized.contains("vru")) return info.getFaction() == EntityInfo.Faction.VRU;
        return true;
    }

    private boolean isProtected(Player player, EntityInfo info) {
        EntityInfo.Diplomacy diplomacy = info.getClanDiplomacy();
        return group.getMember(player) != null || diplomacy == EntityInfo.Diplomacy.ALLIED ||
                diplomacy == EntityInfo.Diplomacy.NOT_ATTACK_PACT;
    }
}
