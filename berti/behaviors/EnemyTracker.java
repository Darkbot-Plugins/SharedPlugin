package dev.shared.berti.behaviors;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import dev.shared.berti.behaviors.configs.EnemyTrackerConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Feature(name="Enemy Tracker", description="This behavior helps you track enemies in a dedicated discord channel via webhook")
public class EnemyTracker
implements Task,
Configurable<EnemyTrackerConfig> {
    protected long lastReportTime = 0L;
    private long resetTime;
    private EnemyTrackerConfig config;
    private final HeroAPI hero;
    private final AttackAPI attack;
    private final Collection<? extends Player> allShips;
    private final BackpageAPI backpageAPI;

    public EnemyTracker(PluginAPI api) {
        this.hero = (HeroAPI)api.requireAPI(HeroAPI.class);
        this.attack = (AttackAPI)api.requireAPI(AttackAPI.class);
        this.backpageAPI = (BackpageAPI)api.requireAPI(BackpageAPI.class);
        this.allShips = ((EntitiesAPI)api.requireAPI(EntitiesAPI.class)).getPlayers();
    }

    private void reportToDiscord(String webhook, String message) {
        if (this.resetTime != 0L && System.currentTimeMillis() / 1000L < this.resetTime) {
            return;
        }
        String jsonBrut = "{\"content\": \"" + message + "\"}";
        HttpURLConnection conn = null;
        try {
            conn = this.getHttpURLConnection(webhook);
            OutputStream os = conn.getOutputStream();
            os.write(jsonBrut.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            conn.getInputStream().close();
            conn.disconnect();
            this.resetTime = 0L;
        }
        catch (Exception ex) {
            try {
                if (conn != null) {
                    System.out.println("-----START-----");
                    Map<String, List<String>> headers = conn.getHeaderFields();
                    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        if (key == null || !key.contains("x-ratelimit") && !key.contains("Retry-After")) continue;
                        System.out.printf("DISCORD: %s : %s%n", key, entry.getValue());
                    }
                    System.out.println("-----END-----");
                    this.resetTime = Long.parseLong(headers.get("x-ratelimit-reset").get(0));
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            ex.printStackTrace();
        }
    }

    private HttpURLConnection getHttpURLConnection(String webhook) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(webhook).openConnection();
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        return conn;
    }

    public void setConfig(ConfigSetting<EnemyTrackerConfig> configSetting) {
        this.config = (EnemyTrackerConfig)configSetting.getValue();
    }

    public void onTickTask() {
        if (this.config.surrounding && (this.lastReportTime == 0L || System.currentTimeMillis() >= this.lastReportTime + 10000L)) {
            try {
                List<Ship> enemies = this.allShips.stream().filter(Entity::isValid).filter(x -> x.getEntityInfo().isEnemy() && (x.hasEffect(341) || !this.backpageAPI.getInstanceURI().getRawPath().contains("gbl1"))).collect(Collectors.toList());
                if (!enemies.isEmpty()) {
                    this.reportToDiscord(this.config.configdiscordwebhook, this.getEnemiesDiscordMessage(enemies));
                }
                this.lastReportTime = System.currentTimeMillis();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        } else if (this.lastReportTime == 0L || System.currentTimeMillis() >= this.lastReportTime + 10000L) {
            try {
                StringBuilder messageBuilder = new StringBuilder();
                if (this.attack.getTarget() instanceof Player) {
                    messageBuilder.append("Target: `[").append(this.attack.getTarget().getEntityInfo().getClanTag()).append("] ").append(this.attack.getTarget().getEntityInfo().getUsername()).append("` | Map: ").append(this.hero.getMap().getName());
                } else {
                    messageBuilder.append("Map: ").append(this.hero.getMap().getName()).append(" | Target is not a player ship");
                }
                this.reportToDiscord(this.config.configdiscordwebhook, messageBuilder.toString());
                this.lastReportTime = System.currentTimeMillis();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getEnemiesDiscordMessage(List<Ship> enemies) {
        StringBuilder messageBuilder = new StringBuilder();
        if (this.attack.getTarget() instanceof Player) {
            messageBuilder.append("Map: ").append(this.hero.getMap().getName()).append(" | Target: `[").append(this.attack.getTarget().getEntityInfo().getClanTag()).append("]").append(this.attack.getTarget().getEntityInfo().getUsername()).append("`");
        } else {
            messageBuilder.append("Map: ").append(this.hero.getMap().getName()).append(" | Target is not a player ship ");
        }
        if (!enemies.isEmpty() && enemies.size() >= 2) {
            messageBuilder.append(" | Other Enemies in sight:");
        } else if (enemies.size() == 1) {
            messageBuilder.append(" | Other Enemy in sight:");
        }
        for (Ship enemy : enemies) {
            if (Objects.equals(enemy.getEntityInfo().getUsername(), this.attack.getTarget().getEntityInfo().getUsername())) continue;
            messageBuilder.append(" | `[").append(enemy.getEntityInfo().getClanTag()).append("] ").append(enemy.getEntityInfo().getUsername()).append("`");
        }
        return messageBuilder.toString();
    }
}
