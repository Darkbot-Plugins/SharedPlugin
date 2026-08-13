package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;

@Configuration("dailytaskapi.config")
public final class DailyTaskConfig {
    public boolean autoOpenQuestWindow = true;

    public boolean requireUridiumReward = true;

    public boolean skipTetrathrinOnly = true;

    public boolean skipProtegitQuests = true;

    public boolean acceptPlayerKillQuests = false;

    public boolean preferShorterQuests = true;

    @Number(min = 250, max = 20000, step = 250)
    public int questSwitchDelayMs = 500;

    @Number(min = 1, max = 10, step = 1)
    public int maxSelectionRetries = 1;

    @Number(min = 300, max = 750, step = 25)
    public int attackRadius = 590;

    @Number(min = 300, max = 750, step = 25)
    public int playerAttackRadius = 590;

    public boolean enemyPlayersOnly = true;

    public boolean avoidAlliedAndGroupPlayers = true;

    public boolean prioritizePlayersAttackingMe = true;

    public String playerCombatMap = "";

    @Number(min = 0.00, max = 0.90, step = 0.05)
    public double playerMinimumShieldPercent = 0.20;

    @Number(min = 0, max = 20, step = 1)
    public int maxPlayerDeaths = 2;

    @Number(min = 30, max = 1800, step = 30)
    public int playerSearchTimeoutSeconds = 300;

    @Number(min = 0.10, max = 0.90, step = 0.05)
    public double minimumHpPercent = 0.30;

    @Number(min = 60, max = 900, step = 30)
    public int stagnationTimeoutSeconds = 300;

}
