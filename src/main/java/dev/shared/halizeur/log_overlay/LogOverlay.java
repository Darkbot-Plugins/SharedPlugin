package dev.shared.halizeur.log_overlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.events.EventHandler;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Draw;
import eu.darkbot.api.extensions.Drawable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.MapGraphics;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.GameLogAPI;

/**
 * Renders the latest in-game DarkOrbit log messages as an overlay on the
 * canvas (anchored to the top, no background, auto-fade).
 *
 * Source: {@link GameLogAPI.LogMessageEvent} emitted by DarkBot for each
 * new system message. Each line disappears after DISPLAY_MS ms so the
 * canvas does not get cluttered.
 */
@Feature(name = "Log Overlay",
         description = "Shows the latest in-game log messages as an overlay on the canvas",
         enabledByDefault = false)
@Draw(value = Draw.Stage.OVERLAY)
public class LogOverlay implements Behavior, Drawable, Listener, Configurable<LogOverlayConfig> {

    private static final int LINE_HEIGHT = 16;
    private static final int TOP_MARGIN = 30;
    private static final int MAX_LINES = 5;
    private static final long DISPLAY_MS = 5000L;

    /**
     * Case-insensitive keywords that mark a log message as "interesting":
     * resource / experience / honor gains, or error messages. Anything
     * else (combat, movement, generic info) is filtered out.
     */
    private static final String[] WHITELIST = {
            // Gains (FR)
            "gagn", "obtenu", "récup", "recup", "récompense", "recompense",
            "collect", "ramass",
            // Gains (EN)
            "gained", "received", "reward", "earned",
            // Currencies / resources
            "uridium", "credit", "crédit", "honor", "honneur",
            "experience", "expérience", "xp ",
            "prometium", "endurium", "terbium", "prometid", "duranium",
            "promerium", "seprom", "xenomit", "palladium",
            // Boosters / drops
            "drop", "booster",
            // Errors (FR)
            "impossible", "erreur", "échec", "echec", "refusé", "refuse",
            "plein", "indisponible", "non disponible", "interdit",
            // Errors (EN)
            "error", "failed", "refused", "denied", "unavailable", "full",
            "cannot", "can't"
    };

    private final Deque<Entry> entries = new ArrayDeque<>();
    private LogOverlayConfig config;

    public LogOverlay(PluginAPI api) {
        api.requireAPI(EventBrokerAPI.class).registerListener(this);
    }

    @Override
    public void setConfig(ConfigSetting<LogOverlayConfig> cfg) {
        this.config = cfg.getValue();
    }

    @Override
    public void onTickBehavior() {
        // Evict expired entries even when the canvas is not being redrawn.
        evictExpired(System.currentTimeMillis());
    }

    @EventHandler
    public void onLogMessage(GameLogAPI.LogMessageEvent event) {
        if (this.config == null || !this.config.enabled) return;
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;
        if (!isAllowed(msg)) return;

        long expiresAt = System.currentTimeMillis() + DISPLAY_MS;
        synchronized (this.entries) {
            this.entries.addLast(new Entry(msg, expiresAt));
            // If more than MAX_LINES, drop the oldest one so the rest scrolls up.
            while (this.entries.size() > MAX_LINES) {
                this.entries.removeFirst();
            }
        }
    }

    /**
     * Whitelist filter: only display the message if it matches a keyword
     * from {@link #WHITELIST} (case-insensitive).
     */
    private boolean isAllowed(String msg) {
        String lower = msg.toLowerCase();
        for (String kw : WHITELIST) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private void evictExpired(long now) {
        synchronized (this.entries) {
            Iterator<Entry> it = this.entries.iterator();
            while (it.hasNext()) {
                if (it.next().expiresAtMs <= now) {
                    it.remove();
                } else {
                    break; // entries are ordered chronologically
                }
            }
        }
    }

    @Override
    public void onDraw(MapGraphics mg) {
        if (this.config == null || !this.config.enabled) return;

        // Eviction is handled in onTickBehavior() which runs every tick.
        List<String> snapshot;
        synchronized (this.entries) {
            if (this.entries.isEmpty()) return;
            snapshot = new ArrayList<>(this.entries.size());
            for (Entry e : this.entries) snapshot.add(e.text);
        }

        int cx = mg.getWidthMiddle();
        int startY = TOP_MARGIN;

        mg.setColor("text_light");
        for (int i = 0; i < snapshot.size(); i++) {
            int y = startY + (i + 1) * LINE_HEIGHT;
            mg.drawString(cx, y, snapshot.get(i), MapGraphics.StringAlign.MID);
        }
    }

    private static final class Entry {
        final String text;
        final long expiresAtMs;

        Entry(String text, long expiresAtMs) {
            this.text = text;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
