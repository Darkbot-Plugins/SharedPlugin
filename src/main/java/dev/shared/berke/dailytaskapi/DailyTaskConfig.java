package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;

@Configuration("dailytaskapi.config")
public final class DailyTaskConfig {
    public boolean autoOpenQuestWindow = true;

    @Number(min = 250, max = 20000, step = 250)
    public int questSwitchDelayMs = 500;

    @Number(min = 1, max = 10, step = 1)
    public int maxSelectionRetries = 1;

    @Number(min = 1, max = 5, step = 1)
    public int maxMenuScanPasses = 1;

    @Number(min = 300, max = 750, step = 25)
    public int attackRadius = 500;

    @Number(min = 0.10, max = 0.90, step = 0.05)
    public double minimumHpPercent = 0.30;

    @Number(min = 60, max = 900, step = 30)
    public int stagnationTimeoutSeconds = 300;

    public boolean stopWhenAllDailyTasksAreDone = true;
}
