package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration("dailytaskapi.config")
public final class DailyTaskConfig {
    @Option("dailytaskapi.auto_open_quest_window")
    public boolean autoOpenQuestWindow = true;

    @Option("dailytaskapi.require_uridium_reward")
    public boolean requireUridiumReward = true;

    @Option("dailytaskapi.skip_tetrathrin_only")
    public boolean skipTetrathrinOnly = true;

    @Option("dailytaskapi.skip_protegit")
    public boolean skipProtegitQuests = true;

    @Option("dailytaskapi.accept_player_kills")
    public boolean acceptPlayerKillQuests = false;

    @Option("dailytaskapi.prefer_shorter")
    public boolean preferShorterQuests = true;

    @Option("dailytaskapi.server_delay")
    @Number(min = 250, max = 20000, step = 250)
    public int questSwitchDelayMs = 500;

    @Option("dailytaskapi.response_retries")
    @Number(min = 1, max = 10, step = 1)
    public int maxSelectionRetries = 4;

    @Option("dailytaskapi.npc_radius")
    @Number(min = 300, max = 750, step = 25)
    public int attackRadius = 590;

    @Option("dailytaskapi.player_radius")
    @Number(min = 300, max = 750, step = 25)
    public int playerAttackRadius = 590;

    @Option("dailytaskapi.enemy_players_only")
    public boolean enemyPlayersOnly = true;

    @Option("dailytaskapi.avoid_allies")
    public boolean avoidAlliedAndGroupPlayers = true;

    @Option("dailytaskapi.prioritize_attackers")
    public boolean prioritizePlayersAttackingMe = true;

    @Option("dailytaskapi.player_map")
    public String playerCombatMap = "";

    @Option("dailytaskapi.player_min_shield")
    @Number(min = 0.00, max = 0.90, step = 0.05)
    public double playerMinimumShieldPercent = 0.20;

    @Option("dailytaskapi.max_player_deaths")
    @Number(min = 0, max = 20, step = 1)
    public int maxPlayerDeaths = 2;

    @Option("dailytaskapi.player_search_timeout")
    @Number(min = 30, max = 1800, step = 30)
    public int playerSearchTimeoutSeconds = 300;

    @Option("dailytaskapi.minimum_hp")
    @Number(min = 0.10, max = 0.90, step = 0.05)
    public double minimumHpPercent = 0.30;

    @Option("dailytaskapi.stagnation_timeout")
    @Number(min = 60, max = 900, step = 30)
    public int stagnationTimeoutSeconds = 300;
}
