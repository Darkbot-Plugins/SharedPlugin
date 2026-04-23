package dev.shared.do_gamer.task.autobuy;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.entities.Item;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.shared.do_gamer.task.autobuy.config.AutobuyConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.util.Timer;

@Feature(name = "Autobuy", description = "Automatically buys boosters and special items from the shop at a configured interval")
public class Autobuy implements Task, Configurable<AutobuyConfig> {

    private enum State {
        IDLE,
        REQUEST_INVENTORY,
        UPDATE_INVENTORY,
        FETCH_LOG_FILE,
        FETCH_BOOSTERS,
        FETCH_SPECIALS,
        PREPARE_QUEUE,
        PURCHASING
    }

    @SuppressWarnings("deprecation")
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final long SHOP_RETRY_DELAY_MS = 30_000L;

    private AutobuyConfig config;
    private final StatsAPI stats;
    private final BackpageManager backpageManager;
    private long nextShopCheck = 0;
    private Timer delay = Timer.getRandom(2_000L, 5_000L);

    private State state = State.IDLE;
    private String boostersHtml;
    private String specialsHtml;
    private final Queue<PurchaseTask> purchaseQueue = new LinkedList<>();

    private final Map<String, Integer> resource = new HashMap<>(Map.of(
            AutobuyConfig.SpecialConfig.DSE_KEY_ACCESS, 0,
            AutobuyConfig.SpecialConfig.LOG_FILE, 0,
            AutobuyConfig.SpecialConfig.PIRATE_KEY_GREEN, 0));

    public Autobuy(PluginAPI api) {
        this.stats = api.requireAPI(StatsAPI.class);
        this.backpageManager = api.requireInstance(BackpageManager.class);
    }

    @Override
    public void setConfig(ConfigSetting<AutobuyConfig> object) {
        this.config = object.getValue();
    }

    @Override
    public void onTickTask() {
        // All logic runs on the background tick; foreground tick is unused
    }

    @Override
    public void onBackgroundTick() {
        if (this.config == null || this.backpageManager == null) {
            return;
        }

        // Wait for delay before proceeding with next action
        if (this.state != State.IDLE && this.delay.isActive()) {
            return;
        }

        switch (this.state) {
            case IDLE:
                this.tickIdle();
                break;
            case REQUEST_INVENTORY:
                this.tickRequestInventory();
                break;
            case UPDATE_INVENTORY:
                this.tickUpdateInventory();
                break;
            case FETCH_LOG_FILE:
                this.tickFetchLogFile();
                break;
            case FETCH_BOOSTERS:
                this.tickFetchBoosters();
                break;
            case FETCH_SPECIALS:
                this.tickFetchSpecials();
                break;
            case PREPARE_QUEUE:
                this.tickPrepareQueue();
                break;
            case PURCHASING:
                this.tickPurchasing();
                break;
        }

        // Activate delay after each state transition to prevent rapid actions
        if (this.state != State.IDLE && this.delay.isInactive()) {
            this.delay.activate();
        }
    }

    // -------------------------------------------------------------------------
    // State tick methods
    // -------------------------------------------------------------------------

    /**
     * Waits until the check interval elapses, then starts the cycle.
     */
    private void tickIdle() {
        if (System.currentTimeMillis() >= this.nextShopCheck) {
            this.state = State.REQUEST_INVENTORY;
        }
    }

    /**
     * Triggers an inventory refresh; the built-in delay gives data time to arrive
     * before reading.
     */
    private void tickRequestInventory() {
        this.backpageManager.legacyHangarManager.updateHangarData(500);
        this.state = State.UPDATE_INVENTORY;
    }

    /**
     * Reads current hangar quantities for tracked special items.
     * LOG_FILE is skipped here; its count is fetched separately in FETCH_LOG_FILE.
     */
    private void tickUpdateInventory() {
        for (String key : this.resource.keySet()) {
            if (AutobuyConfig.SpecialConfig.LOG_FILE.equals(key)) {
                continue;
            }
            int index = this.backpageManager.legacyHangarManager.getLootIds().indexOf(key);
            if (index != -1) {
                int quantity = this.backpageManager.legacyHangarManager.getItems().stream()
                        .filter(item -> item.getLoot() == index)
                        .mapToInt(Item::getQuantity)
                        .findFirst()
                        .orElse(0);
                this.resource.put(key, quantity);
            }
        }
        this.state = State.FETCH_LOG_FILE;
    }

    /**
     * Fetches the log file count from the pilot profile skill tree page.
     * Skipped if log file purchasing is not configured.
     */
    private void tickFetchLogFile() {
        if (this.config.special.logFile.amount == 0) {
            this.state = State.FETCH_BOOSTERS;
            return;
        }
        try {
            String html = this.backpageManager.postHttp("ajax/pilotprofil.php")
                    .setRawParam("command", "getInternalProfilPage")
                    .setRawParam("type", "showSkilltree")
                    .setRawParam("imgUrl", "")
                    .getContent();
            int count = this.extractLogFileCount(html);
            this.resource.put(AutobuyConfig.SpecialConfig.LOG_FILE, count);
            this.state = State.FETCH_BOOSTERS;
            System.out.println(String.format("Autobuy: Current log file count: %d", count));
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Failed to fetch log file count: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Fetches the booster shop page HTML; skips if no boosters are enabled.
     */
    private void tickFetchBoosters() {
        if (!this.config.booster.anyEnabled()) {
            this.boostersHtml = null;
            this.state = State.FETCH_SPECIALS;
            return;
        }
        try {
            this.boostersHtml = this.fetchShopPage("internalDockBooster");
            this.state = State.FETCH_SPECIALS;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Failed to fetch boosters page: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Fetches the specials shop page HTML; skips if no specials are enabled.
     */
    private void tickFetchSpecials() {
        if (!this.config.special.anyEnabled()) {
            this.specialsHtml = null;
            this.state = State.PREPARE_QUEUE;
            return;
        }
        try {
            this.specialsHtml = this.fetchShopPage("internalDockSpecials");
            this.state = State.PREPARE_QUEUE;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Failed to fetch specials page: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Parses fetched HTML and fills the purchase queue with batched tasks.
     */
    private void tickPrepareQueue() {
        this.purchaseQueue.clear();
        try {
            if (this.boostersHtml != null) {
                JsonObject itemData = this.parseShopItemData(this.boostersHtml);
                if (itemData != null) {
                    this.enqueueBoosterItems(itemData);
                }
            }
            if (this.specialsHtml != null) {
                JsonObject itemData = this.parseShopItemData(this.specialsHtml);
                if (itemData != null) {
                    this.enqueueSpecialItems(itemData);
                }
            }
        } catch (Exception e) {
            System.out.println(String.format("Autobuy: Failed to parse shop data: %s", e.getMessage()));
            this.handleError();
            return;
        }
        this.state = State.PURCHASING;
    }

    /**
     * Executes one purchase task per tick; returns to IDLE when the queue is empty.
     */
    private void tickPurchasing() {
        PurchaseTask task = this.purchaseQueue.poll();
        if (task == null) {
            this.nextShopCheck = System.currentTimeMillis() + (long) this.config.checkInterval * 60 * 1000L;
            this.state = State.IDLE;
            return;
        }

        try {
            this.backpageManager.postHttp("ajax/shop.php")
                    .setRawParam("action", "purchase")
                    .setRawParam("category", task.category)
                    .setRawParam("itemId", task.shopItem.itemId)
                    .setRawParam("amount", task.batch)
                    .setRawParam("level", "")
                    .setRawParam("selectedName", "")
                    .getContent();
            System.out.println(String.format("Autobuy: Bought %s item %s x%d",
                    task.category, task.shopItem.code, task.batch));
            this.delay.activate(5_000L); // Extra delay after purchase
        } catch (Exception e) {
            System.out.println(String.format("Autobuy: Failed to buy %s item %s x%d: %s",
                    task.category, task.shopItem.code, task.batch, e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Queue builders
    // -------------------------------------------------------------------------

    /**
     * Queues booster purchases for all enabled boosters that are not currently
     * active.
     */
    private void enqueueBoosterItems(JsonObject itemData) {
        for (Map.Entry<String, JsonElement> entry : itemData.entrySet()) {
            ShopItem shopItem = this.parseShopItem(entry);
            if (shopItem == null || !this.config.booster.isEnabled(shopItem.code)) {
                continue;
            }

            boolean hasBooster = shopItem.shopObj.get("userHasBoosterPackage").getAsBoolean();
            if (!hasBooster) {
                System.out.println(String.format("Autobuy: Booster %s not active, queuing purchase", shopItem.code));
                this.enqueuePurchase(shopItem, "booster", 1);
            }
        }
    }

    /**
     * Queues special item purchases based on configured amounts and conditions.
     */
    private void enqueueSpecialItems(JsonObject itemData) {
        for (Map.Entry<String, JsonElement> entry : itemData.entrySet()) {
            ShopItem shopItem = this.parseShopItem(entry);
            if (shopItem == null) {
                continue;
            }

            int amount = this.resolveSpecialPurchaseAmount(shopItem);
            if (amount > 0) {
                System.out.println(String.format("Autobuy: Special item %s enabled, queuing purchase x%d",
                        shopItem.code, amount));
                this.enqueuePurchase(shopItem, "special", amount);
            }
        }
    }

    /**
     * Returns how many units of a special item should be purchased, accounting for
     * inventory and daily limits.
     */
    private int resolveSpecialPurchaseAmount(ShopItem shopItem) {
        int amount = this.config.special.getAmountOfItem(shopItem.itemId);
        if (amount == 0) {
            return 0;
        }

        if (this.resource.containsKey(shopItem.itemId)) {
            int current = this.resource.get(shopItem.itemId);
            int minRequired = this.config.special.getMinConditionForItem(shopItem.itemId);
            if (minRequired >= 0 && current > minRequired) {
                return 0;
            }
        }

        JsonElement dailyLimit = shopItem.shopObj.get("dailyLimitRemaining");
        if (dailyLimit != null && !dailyLimit.isJsonNull()) {
            amount = Math.min(amount, dailyLimit.getAsInt());
        }

        return amount;
    }

    /**
     * Validates funds and splits the total amount into max-batch sized tasks.
     */
    private void enqueuePurchase(ShopItem shopItem, String category, int amount) {
        if (!this.validateFunds(shopItem, amount))
            return;

        int remaining = amount;
        if (shopItem.maxAmount > 0 && amount > shopItem.maxAmount) {
            System.out.println(String.format("Autobuy: Splitting purchase of %s x%d into batches of %d",
                    shopItem.code, amount, shopItem.maxAmount));
        }
        while (remaining > 0) {
            int batch = shopItem.maxAmount > 0 ? Math.min(remaining, shopItem.maxAmount) : remaining;
            this.purchaseQueue.add(new PurchaseTask(shopItem, category, batch));
            remaining -= batch;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resets all transient state and schedules a retry after the error delay.
     */
    private void handleError() {
        this.nextShopCheck = System.currentTimeMillis() + SHOP_RETRY_DELAY_MS;
        this.purchaseQueue.clear();
        this.boostersHtml = null;
        this.specialsHtml = null;
        this.state = State.IDLE;
    }

    /**
     * Performs a GET request for the given internal dock template page.
     */
    private String fetchShopPage(String tpl) throws IOException {
        return this.backpageManager.getHttp("indexInternal.es")
                .setRawParam("action", "internalDock")
                .setRawParam("tpl", tpl)
                .getContent();
    }

    /**
     * Extracts the itemData object from the raw shop page HTML.
     */
    private JsonObject parseShopItemData(String html) {
        String json = this.extractShopParametersJson(html);
        if (json == null)
            return null;

        @SuppressWarnings("deprecation")
        JsonObject parsed = JSON_PARSER.parse(json).getAsJsonObject();
        JsonElement itemDataElement = parsed.get("itemData");
        if (itemDataElement == null || itemDataElement.isJsonNull() || !itemDataElement.isJsonObject()) {
            return null;
        }
        return itemDataElement.getAsJsonObject();
    }

    /**
     * Extracts the log file count from HTML code.
     */
    private int extractLogFileCount(String html) {
        if (html == null) {
            return 0;
        }
        String marker = "<span id=\\\"logFileUpdated\\\">";
        int start = html.indexOf(marker);
        if (start < 0) {
            return 0;
        }
        int valueStart = start + marker.length();
        int end = html.indexOf("<\\/span>", valueStart);
        if (end < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(html.substring(valueStart, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Finds and returns the outermost JSON object following "Shop.Parameters" in
     * the HTML.
     */
    private String extractShopParametersJson(String html) {
        if (html == null)
            return null;

        int start = html.indexOf("Shop.Parameters");
        if (start < 0)
            return null;

        int braceStart = html.indexOf("{", start);
        if (braceStart < 0)
            return null;

        int depth = 0;
        for (int i = braceStart; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{')
                depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0)
                    return html.substring(braceStart, i + 1);
            }
        }
        return null;
    }

    /**
     * Parses a single shop entry; returns null for real-money items.
     */
    private ShopItem parseShopItem(Map.Entry<String, JsonElement> entry) {
        JsonObject shopObj = entry.getValue().getAsJsonObject();
        JsonElement paymentElement = shopObj.get("isPaymentItem");
        if (paymentElement != null && !paymentElement.isJsonNull() && paymentElement.getAsBoolean()) {
            return null;
        }
        String itemId = entry.getKey();
        String code = shopObj.get("code").getAsString();
        double price = shopObj.get("price").getAsDouble();
        String currency = shopObj.get("currency").getAsString();
        int maxAmount = shopObj.get("maxAmount").getAsInt();
        return new ShopItem(itemId, code, price, currency, maxAmount, shopObj);
    }

    /**
     * Checks whether the player has enough credits or uridium for the purchase.
     */
    private boolean validateFunds(ShopItem shopItem, int amount) {
        double required = shopItem.price * amount;
        boolean hasEnough;
        String currencyName;

        if ("real".equals(shopItem.currency)) {
            hasEnough = this.stats.getTotalUridium() >= required;
            currencyName = "uridium";
        } else if ("virtual".equals(shopItem.currency)) {
            hasEnough = this.stats.getTotalCredits() >= required;
            currencyName = "credits";
        } else {
            System.out.println(String.format("Autobuy: Unknown currency '%s' for item %s, skipping",
                    shopItem.currency, shopItem.code));
            return false;
        }

        if (!hasEnough) {
            System.out.println(String.format("Autobuy: Not enough %s for %s. Required: %.2f",
                    currencyName, shopItem.code, required));
        }
        return hasEnough;
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    private static class ShopItem {
        final String itemId;
        final String code;
        final double price;
        final String currency;
        final int maxAmount;
        final JsonObject shopObj;

        ShopItem(String itemId, String code, double price, String currency, int maxAmount, JsonObject shopObj) {
            this.itemId = itemId;
            this.code = code;
            this.price = price;
            this.currency = currency;
            this.maxAmount = maxAmount;
            this.shopObj = shopObj;
        }
    }

    private static class PurchaseTask {
        final ShopItem shopItem;
        final String category;
        final int batch;

        PurchaseTask(ShopItem shopItem, String category, int batch) {
            this.shopItem = shopItem;
            this.category = category;
            this.batch = batch;
        }
    }

}
