package dev.shared.witkor01.task.auction;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.utils.Base62;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.util.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Feature(name = "Auction", description = "Periodically fetches and parses the in-game internal auction page.")
public class Auction implements Task, Configurable<Auction.AuctionConfig> {

    private static final Path LOG_FILE = Paths.get("auction_debug.log");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern ROW_PATTERN = Pattern.compile(
            "<tr[^>]*class=\"[^\"]*auctionItemRow[^\"]*\"[^>]*itemkey=\"(item_hour_[^\"]+)\"[^>]*>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HIDDEN_INPUT_PATTERN = Pattern.compile(
            "<input[^>]*\\bid=\"([^\"]+)\"[^>]*\\bvalue=\"([^\"]*)\"[^>]*/?>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "<td[^>]*class=\"[^\"]*auction_item_name_col[^\"]*\"[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "<td[^>]*class=\"[^\"]*auction_item_type[^\"]*\"[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern SHOW_USER_PATTERN = Pattern.compile(
            "showUser=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final BackpageManager backpage;

    private AuctionConfig config = new AuctionConfig();
    private long nextFetch;
    private final List<AuctionItem> items = new ArrayList<>();

    private long lastTimeLeftSecs = -1;

    private final Set<String> biddedThisRound = new HashSet<>();

    public Auction(BackpageManager backpage) {
        this.backpage = backpage;
    }

    @Override
    public void setConfig(AuctionConfig config) {
        this.config = config;
        config.module = this;
    }

    public long getSecondsLeft() {
        int endMinute = (config != null) ? config.auctionEndMinute : 35;
        return computeSecondsUntilMinute(endMinute);
    }

    public static long computeSecondsUntilMinute(int endMinute) {
        long nowSec = System.currentTimeMillis() / 1000;
        long currentMinuteInHour = (nowSec % 3600) / 60;
        long currentSecInMinute  = nowSec % 60;
        long secsUntil;
        if (currentMinuteInHour < endMinute) {
            secsUntil = (endMinute - currentMinuteInHour) * 60 - currentSecInMinute;
        } else if (currentMinuteInHour == endMinute && currentSecInMinute == 0) {
            secsUntil = 0;
        } else {
            secsUntil = (60 - currentMinuteInHour + endMinute) * 60 - currentSecInMinute;
        }
        return secsUntil;
    }

    @Override
    public void onTickTask() {
        if (config == null || !config.enabled) return;
        long now = System.currentTimeMillis();
        long secsLeft = getSecondsLeft();

        boolean hasEnabledBids = config.itemConfigs.values().stream().anyMatch(c -> c.enabled);

        if (hasEnabledBids && secsLeft >= 0) {
            long target = config.bidBeforeSeconds;
            if (secsLeft <= target + 10 && secsLeft >= Math.max(0, target - 10)) {
                tryPlaceBids(secsLeft);
            }
        }

        if (now < nextFetch) return;

        boolean closeToBid = hasEnabledBids && secsLeft >= 0 && secsLeft <= config.bidBeforeSeconds + 60;
        long fetchInterval = closeToBid ? 20_000L : Math.max(1, config.fetchIntervalMinutes) * 60_000L;
        nextFetch = now + fetchInterval;

        try {
            log("=== Auction fetch started @ " + LocalDateTime.now().format(TS)
                    + " [secsLeft~" + secsLeft + ", interval=" + (fetchInterval / 1000) + "s] ===");
            String html = fetchAuctionPage();
            if (html == null || html.isEmpty()) { log("No HTML received."); return; }
            log("Received HTML, length=" + html.length());
            if (config.logRawHtml) { log("---- RAW HTML BEGIN ----"); log(html); log("---- RAW HTML END ----"); }

            items.clear();
            parse(html);

            log("Parsed " + items.size() + " auction item(s). secsLeft=" + getSecondsLeft());
            for (AuctionItem it : items) log("  - " + it);
            log("=== Auction fetch finished ===");
        } catch (Exception e) {
            log("Exception during auction fetch: " + e);
            e.printStackTrace();
        }
    }

    private void tryPlaceBids(long secsLeft) {
        for (AuctionItem item : items) {
            tryBidItem(item, secsLeft);
        }
    }

    private void tryBidItem(AuctionItem item, long secsLeft) {
        String lootId = item.lootId != null ? item.lootId : item.itemKey;
        if (biddedThisRound.contains(lootId)) return;

        ItemBidConfig bc = config.itemConfigs.get(lootId);
        if (bc == null || !bc.enabled) return;

        if (item.highestBidderId != 0 && item.highestBidderId == backpage.getUserId()) {
            log("SKIP '" + item.name + "': already top bidder");
            biddedThisRound.add(lootId);
            return;
        }

        long bidAmount = item.currentBid + bc.increment;
        if (bidAmount > bc.maxBid) {
            log("SKIP bid on '" + item.name + "': next bid " + bidAmount + " > maxBid " + bc.maxBid);
            biddedThisRound.add(lootId);
            return;
        }

        log("BID on '" + item.name + "' for " + bidAmount
                + " credits (currentBid=" + item.currentBid + ", secsLeft=" + secsLeft + ")");
        try {
            AuctionItems ai = buildAuctionItems(item);
            boolean ok = backpage.auctionManager.bidItem(ai, bidAmount);
            log("BID result for '" + item.name + "': " + (ok ? "SUCCESS" : "FAILED"));
            if (ok) biddedThisRound.add(lootId);
        } catch (Exception e) {
            log("BID exception for '" + item.name + "': " + e);
            e.printStackTrace();
        }
    }

    private AuctionItems buildAuctionItems(AuctionItem item) {
        AuctionItems ai = new AuctionItems();
        ai.setId(item.itemKey);
        ai.setLootId(item.lootId);
        ai.setName(item.name != null ? item.name : item.itemKey);
        ai.setCurrentBid(item.currentBid);
        if (item.itemKey != null && item.itemKey.contains("_day_"))       ai.setAuctionType(AuctionItems.Type.DAY);
        else if (item.itemKey != null && item.itemKey.contains("_week_")) ai.setAuctionType(AuctionItems.Type.WEEK);
        else                                                               ai.setAuctionType(AuctionItems.Type.HOUR);
        return ai;
    }

    private String fetchAuctionPage() throws IOException {
        HttpURLConnection conn = backpage.getHttp("indexInternal.es?action=internalAuction").getConnection();
        return IOUtils.read(conn.getInputStream());
    }

    private void parse(String html) {
        int endMinute = (config != null) ? config.auctionEndMinute : 35;
        long newSecs = computeSecondsUntilMinute(endMinute);
        String timeLeft = formatSeconds(newSecs);
        log("Countdown (minute=" + endMinute + "): " + newSecs + "s (" + timeLeft + ")");

        if (lastTimeLeftSecs >= 0 && newSecs > lastTimeLeftSecs + 300) {
            log("New auction round detected — clearing bid history");
            biddedThisRound.clear();
        }
        lastTimeLeftSecs = newSecs;

        Matcher rm = ROW_PATTERN.matcher(html);
        int idx = 0;
        while (rm.find()) {
            AuctionItem item = parseRow(rm.group(1), rm.group(2), timeLeft);
            log("Row #" + (idx++) + " key=" + item.itemKey + " name='" + item.name
                    + "' bid=" + item.currentBid + " topBidder=" + item.highestBidderId
                    + " myId=" + backpage.getUserId()
                    + (item.highestBidderId != 0 && item.highestBidderId == backpage.getUserId() ? " [MY BID]" : ""));
            items.add(item);
        }
    }

    private AuctionItem parseRow(String key, String row, String timeLeft) {
        AuctionItem item = new AuctionItem();
        item.itemKey  = key;
        item.timeLeft = timeLeft;

        Matcher nm = NAME_PATTERN.matcher(row);
        if (nm.find()) item.name = stripTags(nm.group(1));

        Matcher tm = TYPE_PATTERN.matcher(row);
        if (tm.find()) item.type = stripTags(tm.group(1));

        parseHiddenInputs(item, row);

        Matcher su = SHOW_USER_PATTERN.matcher(row);
        if (su.find()) {
            try {
                item.highestBidderId = Base62.decode(su.group(1));
            } catch (Exception ignored) { // Base62 decode failure — highestBidderId stays 0
            }
        }
        return item;
    }

    private void parseHiddenInputs(AuctionItem item, String row) {
        Matcher hm = HIDDEN_INPUT_PATTERN.matcher(row);
        while (hm.find()) {
            String id = hm.group(1);
            String val = hm.group(2);
            if      (id.endsWith("_bid"))        item.currentBid = parseLong(val);
            else if (id.endsWith("_buyPrice"))   item.buyPrice   = parseLong(val);
            else if (id.endsWith("_lootId"))     item.lootId     = val;
            else if (id.endsWith("_instantBuy")) item.instantBuy = "1".equals(val.trim());
        }
    }

    public static long parseSecondsLeft(String timeLeft) {
        if (timeLeft == null || timeLeft.isEmpty()) return -1;
        String[] p = timeLeft.split(":");
        try {
            if (p.length == 2) return Long.parseLong(p[0].trim()) * 60 + Long.parseLong(p[1].trim());
            if (p.length == 3) return Long.parseLong(p[0].trim()) * 3600 + Long.parseLong(p[1].trim()) * 60 + Long.parseLong(p[2].trim());
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static String formatSeconds(long secs) {
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ignored) {
            // non-numeric HTML field value — treat as 0
            return 0L;
        }
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    public List<AuctionItem> getItems() { return new ArrayList<>(items); }

    public boolean isMyBid(AuctionItem item) {
        long myId = backpage.getUserId();
        return myId != 0 && item.highestBidderId != 0 && item.highestBidderId == myId;
    }

    private static synchronized void log(String msg) {
        String line = "[" + LocalDateTime.now().format(TS) + "] " + msg + System.lineSeparator();
        try (BufferedWriter w = Files.newBufferedWriter(LOG_FILE, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
        } catch (IOException ignored) { // log write failure — non-critical, silently skip
        }
    }

    public static class AuctionItem {
        String itemKey;
        String name;
        String type;
        String lootId;
        String timeLeft;
        long currentBid;
        long buyPrice;
        long highestBidderId;
        boolean instantBuy;

        @Override
        public String toString() {
            return "AuctionItem{key=" + itemKey + ", name='" + name + "', type='" + type
                    + "', lootId=" + lootId + ", currentBid=" + currentBid
                    + ", buyPrice=" + buyPrice + ", instantBuy=" + instantBuy
                    + ", timeLeft='" + timeLeft + "'}";
        }
    }

    public static class ItemBidConfig {
        boolean enabled   = false;
        int     maxBid    = 500_000;
        int     increment = 20_000;
    }

    public static class AuctionConfig {
        @Option("Time until auction end")
        @Editor(CountdownEditor.class)
        public Object countdownDisplay = null;

        @Option("Enabled")
        public boolean enabled = true;

        @Option("Auction end minute (0-59)")
        @Number(min = 0, max = 59)
        public int auctionEndMinute = 35;

        @Option("Fetch interval (minutes)")
        @Number(min = 1, max = 60)
        public int fetchIntervalMinutes = 5;

        @Option("Seconds before end to bid")
        @Number(min = 0, max = 3600)
        public int bidBeforeSeconds = 10;

        @Option("Log raw HTML (debug)")
        public boolean logRawHtml = false;

        public java.util.Map<String, ItemBidConfig> itemConfigs = new java.util.HashMap<>();

        @Option("Available items")
        @Editor(AuctionItemsEditor.class)
        public Object itemsDisplay = null;

        public transient Auction module;
    }
}

