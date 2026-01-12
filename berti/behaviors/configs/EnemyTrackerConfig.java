package dev.shared.berti.behaviors.configs;

import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Configuration;

@Configuration(value="config.enemytracker")
public class EnemyTrackerConfig {
    @Option("config.enemytracker.configdiscordwebhook")
    public String configdiscordwebhook;
    @Option("config.enemytracker.surrounding")
    public boolean surrounding;
}
