package dev.shared.halizeur.log_overlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import eu.darkbot.api.managers.GameResourcesAPI;
import eu.darkbot.api.managers.GameResourcesAPI.TranslationMatcher;

/**
 * Renders the latest in-game DarkOrbit log messages as an overlay on the
 * canvas (anchored to the top, no background, auto-fade).
 *
 * Source: {@link GameLogAPI.LogMessageEvent} emitted by DarkBot for each
 * new system message. Each line disappears after DISPLAY_MS ms so the
 * canvas does not get cluttered.
 *
 * Filter: per category, the overlay matches messages against translation
 * patterns from {@link GameResourcesAPI} (the official Bigpoint flashres
 * keys), so the filter works on every game locale without per-language
 * keyword maintenance.
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

    private enum Cat {
        GAINS, BOOSTERS, ERRORS, COMBAT
    }

    /** flashres keys whose translation describes a generic loot / reward
     *  message — collection, ore refining, level up, quest reward, etc. */
    private static final String[] KEYS_GAINS = {
            "oksammel",       // "%! collected"
            "boxfarming",     // "Salvaging %!"
            "farmresult",     // "You collected:"
            "auto_ore_update",
            "special_item_found",
            "lvlup_msg",
            "quest_finish_reward_s",
            "quest_finish_reward_p"
    };

    /** flashres keys for booster activation / banking-multiplier drops. */
    private static final String[] KEYS_BOOSTERS = {
            "booster_found",
            "banking_doubler",
            "banking_tripler",
            "banking_quadruplicator",
            "banking_multiplier_active"
    };

    /** flashres keys for action refusals: cargo full, no ammo, ability
     *  on cooldown, ammo purchase failed, ship missing weapons, etc. */
    private static final String[] KEYS_ERRORS = {
            "boxtoobig",
            "boxdisabled",
            "resourcedisabled",
            "outofammo",
            "rohstoffailed",
            "loadfull",
            "emptybat",
            "ammobuy_fail_uri",
            "ammobuy_fail_cre",
            "ammobuy_fail_space",
            "ammobuy_fail_offer",
            "smartbomb_failed_mines",
            "smartbomb_failed_xenomit",
            "instashield_failed_mines",
            "instashield_failed_xenomit",
            "no_lasers_on_board",
            "msg_laser_not_equipped",
            "msg_rocketlauncher_not_equipped",
            "loot_theft"
    };

    /** flashres key for the "X destroyed" combat message. */
    private static final String[] KEYS_COMBAT = {
            "destroyed"
    };

    /** Currency names. DarkOrbit keeps these mostly untranslated across
     *  locales, so a static list works as a substring filter against any
     *  raw log message. Lower-cased at init time for case-insensitive
     *  comparison. */
    private static final String[] CURRENCY_NAMES = {
            "uridium", "uri",
            "credit", "crédit",
            "honor", "honour", "honneur",
            "experience", "expérience", "exp", " xp"
    };

    /** flashres keys whose translation is the localized name of an ore
     *  / resource. Resolved at init via {@link GameResourcesAPI#findTranslation}. */
    private static final String[] RESOURCE_KEYS = {
            "ore_prometium", "ore_endurium", "ore_terbium",
            "ore_prometid", "ore_duranium", "ore_promerium",
            "ore_seprom", "ore_xenomit", "ore_palladium", "ore_osmium"
    };

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Cat, List<TranslationMatcher>> matchers = new EnumMap<>(Cat.class);
    private final List<String> currencyNeedles = new ArrayList<>();
    private final List<String> resourceNeedles = new ArrayList<>();
    private LogOverlayConfig config;

    public LogOverlay(PluginAPI api) {
        api.requireAPI(EventBrokerAPI.class).registerListener(this);

        GameResourcesAPI res = api.requireAPI(GameResourcesAPI.class);
        buildMatchers(res, Cat.GAINS, KEYS_GAINS);
        buildMatchers(res, Cat.BOOSTERS, KEYS_BOOSTERS);
        buildMatchers(res, Cat.ERRORS, KEYS_ERRORS);
        buildMatchers(res, Cat.COMBAT, KEYS_COMBAT);

        for (String name : CURRENCY_NAMES) {
            this.currencyNeedles.add(name);
        }
        for (String key : RESOURCE_KEYS) {
            res.findTranslation(key).ifPresent(t -> {
                String lower = t.toLowerCase();
                if (!this.resourceNeedles.contains(lower)) {
                    this.resourceNeedles.add(lower);
                }
            });
        }
    }

    /** Pre-builds {@link TranslationMatcher} instances for every key in a
     *  category. Keys whose translation is missing in the active locale
     *  return {@link java.util.Optional#empty()} and are skipped silently. */
    private void buildMatchers(GameResourcesAPI res, Cat cat, String[] keys) {
        List<TranslationMatcher> list = new ArrayList<>();
        for (String key : keys) {
            res.getTranslationMatcher(key).ifPresent(list::add);
        }
        this.matchers.put(cat, list);
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
     * Filter: a message is kept when at least one checked category
     * matches. Most categories use TranslationMatcher (built from the
     * official Bigpoint translations); Currencies and Resources fall
     * back to substring matching against localized names because the
     * actual values appear inside the {@code %!} placeholder of the
     * generic gain templates and are easier to detect this way.
     */
    private boolean isAllowed(String msg) {
        LogOverlayConfig.Categories c = this.config.categories;
        if (c == null) return false;

        if (c.gains    && anyMatcherFinds(Cat.GAINS, msg))    return true;
        if (c.boosters && anyMatcherFinds(Cat.BOOSTERS, msg)) return true;
        if (c.errors   && anyMatcherFinds(Cat.ERRORS, msg))   return true;
        if (c.combat   && anyMatcherFinds(Cat.COMBAT, msg))   return true;

        if (c.currencies || c.resources) {
            String lower = msg.toLowerCase();
            if (c.currencies && containsAny(lower, this.currencyNeedles)) return true;
            if (c.resources  && containsAny(lower, this.resourceNeedles)) return true;
        }
        return false;
    }

    private boolean anyMatcherFinds(Cat cat, String msg) {
        List<TranslationMatcher> list = this.matchers.get(cat);
        if (list == null) return false;
        for (TranslationMatcher m : list) {
            if (m.find(msg)) return true;
        }
        return false;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
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
