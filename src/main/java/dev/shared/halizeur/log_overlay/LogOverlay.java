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
import eu.darkbot.api.managers.I18nAPI;

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
     * Default whitelist (English) used when the active DarkBot locale has
     * no {@code halizeur.log_overlay.whitelist} translation. Each comma-
     * separated entry is a case-insensitive substring; a log message is
     * displayed only if it contains at least one of them.
     *
     * Translators can override this per-locale by adding the same key in
     * their {@code strings_<locale>.properties} file.
     */
    private static final String DEFAULT_WHITELIST =
            "gained,received,reward,earned,"
            + "uridium,credit,honor,honour,experience,xp,"
            + "prometium,endurium,terbium,prometid,duranium,"
            + "promerium,seprom,xenomit,palladium,drop,booster,"
            + "error,failed,refused,denied,unavailable,full,"
            + "cannot,impossible";

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<String> whitelist;
    private LogOverlayConfig config;

    public LogOverlay(PluginAPI api) {
        api.requireAPI(EventBrokerAPI.class).registerListener(this);
        I18nAPI i18n = api.requireAPI(I18nAPI.class);
        this.whitelist = parseKeywords(i18n.getOrDefault(
                "halizeur.log_overlay.whitelist", DEFAULT_WHITELIST));
    }

    /**
     * Parses a comma-separated list of keywords, trimming whitespace and
     * lower-casing each entry. Empty entries are dropped.
     */
    private static List<String> parseKeywords(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
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
     * Whitelist filter: only display the message if it contains at least
     * one keyword from {@link #whitelist} (case-insensitive). The list
     * comes from the i18n key {@code halizeur.log_overlay.whitelist} and
     * thus adapts to the user's selected DarkBot locale.
     */
    private boolean isAllowed(String msg) {
        String lower = msg.toLowerCase();
        for (String kw : whitelist) {
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
