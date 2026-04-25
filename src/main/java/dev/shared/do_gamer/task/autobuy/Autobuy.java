package dev.shared.do_gamer.task.autobuy;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

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
public final class Autobuy implements Task, Configurable<AutobuyConfig> {

    private enum State {
        IDLE,
        REQUEST_INVENTORY,
        FETCH_LOG_FILE,
        FETCH_BOOSTERS,
        FETCH_SPECIALS,
        FETCH_AMMO,
        PREPARE_QUEUE,
        PURCHASING
    }

    @SuppressWarnings("deprecation")
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final long SHOP_RETRY_DELAY_MS = 30_000L;

    private static final String BOOSTER_CATEGORY = "booster";
    private static final String SPECIAL_CATEGORY = "special";
    private static final String AMMO_CATEGORY = "ammo";

    private AutobuyConfig config;
    private final StatsAPI stats;
    private final BackpageManager backpageManager;
    private final Map<String, CategoryState> categories = new HashMap<>();
    private Timer delay = Timer.getRandom(2_000L, 5_000L);
    private boolean skipDelay = false;

    private State state = State.IDLE;
    private final Queue<PurchaseTask> purchaseQueue = new LinkedList<>();

    private int logFileCount = 0;

    public Autobuy(PluginAPI api) {
        this.stats = api.requireAPI(StatsAPI.class);
        this.backpageManager = api.requireInstance(BackpageManager.class);
        this.categories.put(BOOSTER_CATEGORY,
                new CategoryState(() -> this.config != null ? this.config.booster : null));
        this.categories.put(SPECIAL_CATEGORY,
                new CategoryState(() -> this.config != null ? this.config.special : null));
        this.categories.put(AMMO_CATEGORY,
                new CategoryState(() -> this.config != null ? this.config.ammo : null));
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
            case FETCH_LOG_FILE:
                this.tickFetchLogFile();
                break;
            case FETCH_BOOSTERS:
                this.tickFetchBoosters();
                break;
            case FETCH_SPECIALS:
                this.tickFetchSpecials();
                break;
            case FETCH_AMMO:
                this.tickFetchAmmo();
                break;
            case PREPARE_QUEUE:
                this.tickPrepareQueue();
                break;
            case PURCHASING:
                this.tickPurchasing();
                break;
        }

        // Activate delay to prevent rapid actions, unless skipped
        if (!this.skipDelay && this.state != State.IDLE && this.delay.isInactive()) {
            this.delay.activate();
        }
        this.skipDelay = false;
    }

    // -------------------------------------------------------------------------
    // State tick methods
    // -------------------------------------------------------------------------

    /**
     * Waits until the check interval elapses, then starts the cycle.
     */
    private void tickIdle() {
        long currentTime = System.currentTimeMillis();

        CategoryState boosterState = this.categories.get(BOOSTER_CATEGORY);
        CategoryState specialState = this.categories.get(SPECIAL_CATEGORY);
        CategoryState ammoState = this.categories.get(AMMO_CATEGORY);

        boolean boosterDue = boosterState.shouldFetch(currentTime);
        boolean specialDue = specialState.shouldFetch(currentTime);
        boolean ammoDue = ammoState.shouldFetch(currentTime);

        if (!boosterDue && !specialDue && !ammoDue) {
            return;
        }

        this.categories.values().forEach(CategoryState::reset);
        boosterState.pending = !boosterDue;
        specialState.pending = !specialDue;
        ammoState.pending = !ammoDue;
        this.state = (specialDue || ammoDue) ? State.REQUEST_INVENTORY : State.FETCH_BOOSTERS;
        this.skipDelay = true;
    }

    /**
     * Triggers an inventory refresh if any non-logfile special item with a min
     * condition is enabled.
     */
    private void tickRequestInventory() {
        if (!this.config.special.isUpdateHangar() && !this.config.ammo.isUpdateHangar()) {
            this.skipDelay = true;
            this.state = State.FETCH_LOG_FILE;
            return;
        }
        this.backpageManager.legacyHangarManager.updateHangarData(500);
        this.state = State.FETCH_LOG_FILE;
    }

    /**
     * Fetches the log file count from the pilot profile skill tree page.
     * Skipped if log file purchasing is not configured.
     */
    private void tickFetchLogFile() {
        if (this.config.special.logFile.amount == 0) {
            // If log file is not enabled, skip fetching its count
            this.skipDelay = true;
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
            if (count < 0) {
                System.out.println(
                        "Autobuy: Could not read the Log Disks count from the profile response. Skipping this cycle.");
                this.handleError();
                return;
            }
            this.logFileCount = count;
            this.state = State.FETCH_BOOSTERS;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Could not load the Log Disks count: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Fetches the booster shop page HTML; skips if no boosters are enabled or timer
     * not expired.
     */
    private void tickFetchBoosters() {
        CategoryState boosterState = this.categories.get(BOOSTER_CATEGORY);
        if (!boosterState.isEnabled() || boosterState.pending) {
            this.skipDelay = true;
            boosterState.html = null;
            this.state = State.FETCH_SPECIALS;
            return;
        }
        try {
            boosterState.html = this.fetchShopPage("internalDockBooster");
            boosterState.markFetched();
            this.state = State.FETCH_SPECIALS;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Could not load the boosters shop page: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Fetches the specials shop page HTML; skips if no specials are enabled or
     * timer not expired.
     */
    private void tickFetchSpecials() {
        CategoryState specialState = this.categories.get(SPECIAL_CATEGORY);
        if (!specialState.isEnabled() || specialState.pending || !this.hasPendingSpecialPurchases()) {
            this.skipDelay = true;
            specialState.html = null;
            this.state = State.FETCH_AMMO;
            return;
        }
        try {
            specialState.html = this.fetchShopPage("internalDockSpecials");
            specialState.markFetched();
            this.state = State.FETCH_AMMO;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Could not load the specials shop page: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Fetches the ammo shop page HTML; skips if no ammo is enabled, timer not
     * expired, or no ammo item currently requires purchase.
     */
    private void tickFetchAmmo() {
        CategoryState ammoState = this.categories.get(AMMO_CATEGORY);
        if (!ammoState.isEnabled() || ammoState.pending || !this.hasPendingAmmoPurchases()) {
            this.skipDelay = true;
            ammoState.html = null;
            this.state = State.PREPARE_QUEUE;
            return;
        }
        try {
            ammoState.html = this.fetchShopPage("internalDockAmmo");
            ammoState.markFetched();
            this.state = State.PREPARE_QUEUE;
        } catch (IOException e) {
            System.out.println(String.format("Autobuy: Could not load the ammo shop page: %s", e.getMessage()));
            this.handleError();
        }
    }

    /**
     * Parses fetched HTML and fills the purchase queue with batched tasks.
     */
    private void tickPrepareQueue() {
        this.purchaseQueue.clear();
        try {
            this.categories.forEach((key, categoryState) -> {
                if (categoryState.html == null) {
                    return;
                }
                JsonObject itemData = this.parseShopItemData(categoryState.html);
                if (itemData == null) {
                    return;
                }

                switch (key) {
                    case BOOSTER_CATEGORY:
                        this.enqueueBoosterItems(itemData);
                        break;
                    case SPECIAL_CATEGORY:
                        this.enqueueSpecialItems(itemData);
                        break;
                    case AMMO_CATEGORY:
                        this.enqueueAmmoItems(itemData);
                        break;
                    default:
                        break;
                }
            });
        } catch (Exception e) {
            System.out.println(String.format("Autobuy: Could not process the shop data: %s", e.getMessage()));
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
            long currentTime = System.currentTimeMillis();
            this.categories.values().forEach(categoryState -> {
                if (categoryState.fetched) {
                    categoryState.updateNextCheck(currentTime);
                }
            });
            this.state = State.IDLE;
            return;
        }

        try {
            this.backpageManager.postHttp("ajax/shop.php")
                    .setRawParam("action", "purchase")
                    .setRawParam("category", task.shopItem.category)
                    .setRawParam("itemId", task.shopItem.itemId)
                    .setRawParam("amount", task.batch)
                    .setRawParam("level", "")
                    .setRawParam("selectedName", "")
                    .getContent();
            int cost = this.calculateCost(task.shopItem.price, task.batch);
            System.out.println(String.format("Autobuy: Purchased %s item %s x%,d for %,d %s.",
                    task.category, task.shopItem.code, task.batch, cost, task.shopItem.currency));
            this.delay.activate(5_000L); // Extra delay after purchase
        } catch (Exception e) {
            System.out.println(String.format("Autobuy: Could not purchase %s item %s x%,d: %s",
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
                this.enqueuePurchase(shopItem, 1, BOOSTER_CATEGORY);
                System.out.println(String.format("Autobuy: Queued booster purchase for %s.", shopItem.code));
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

            int amount = this.resolveSpecialPurchaseAmount(shopItem.itemId);

            // Daily limit check (if applicable)
            JsonElement dailyLimit = shopItem.shopObj.get("dailyLimitRemaining");
            if (dailyLimit != null && !dailyLimit.isJsonNull()) {
                amount = Math.min(amount, dailyLimit.getAsInt());
            }

            if (amount > 0) {
                this.enqueuePurchase(shopItem, amount, SPECIAL_CATEGORY);
                System.out.println(String.format("Autobuy: Queued special purchase for %s x%,d.",
                        shopItem.code, amount));
            }
        }
    }

    /**
     * Queues ammo purchases based on configured amounts.
     */
    private void enqueueAmmoItems(JsonObject itemData) {
        for (Map.Entry<String, JsonElement> entry : itemData.entrySet()) {
            ShopItem shopItem = this.parseShopItem(entry);
            if (shopItem == null) {
                continue;
            }

            int amount = this.resolveAmmoPurchaseAmount(shopItem.itemId);
            if (amount > 0) {
                this.enqueuePurchase(shopItem, amount, AMMO_CATEGORY);
                System.out.println(String.format("Autobuy: Queued ammo purchase for %s x%,d.",
                        shopItem.code, amount));
            }
        }
    }

    /**
     * Returns how many units of a special item should be purchased.
     */
    private int resolveSpecialPurchaseAmount(String itemId) {
        int amount = this.config.special.getAmountOfItem(itemId);
        if (amount == 0) {
            return 0;
        }

        int minRequired = this.config.special.getMinConditionForItem(itemId);
        if (minRequired >= 0) {
            int current = AutobuyConfig.SpecialConfig.LOG_FILE.equals(itemId)
                    ? this.logFileCount
                    : this.getHangarQuantity(itemId);
            if (current > minRequired) {
                return 0;
            }
        }

        return amount;
    }

    /**
     * Returns how many units of an ammo item should be purchased.
     */
    private int resolveAmmoPurchaseAmount(String itemId) {
        int amount = this.config.ammo.getAmountOfItem(itemId);
        if (amount == 0) {
            return 0;
        }

        int minRequired = this.config.ammo.getMinConditionForItem(itemId);
        if (minRequired >= 0) {
            int current = this.getHangarQuantity(itemId);
            if (current > minRequired) {
                return 0;
            }
        }

        return amount;
    }

    /**
     * Checks if there are any special items that currently require purchase.
     */
    private boolean hasPendingSpecialPurchases() {
        for (String itemId : this.config.special.getItemIds()) {
            if (this.resolveSpecialPurchaseAmount(itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if there are any ammo items that currently require purchase.
     */
    private boolean hasPendingAmmoPurchases() {
        for (String itemId : this.config.ammo.getItemIds()) {
            if (this.resolveAmmoPurchaseAmount(itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the quantity of the given item in the hangar, or 0 if not found.
     */
    private int getHangarQuantity(String itemId) {
        int index = this.backpageManager.legacyHangarManager.getLootIds().indexOf(itemId);
        if (index == -1) {
            return 0;
        }
        return this.backpageManager.legacyHangarManager.getItems().stream()
                .filter(item -> item.getLoot() == index)
                .mapToInt(Item::getQuantity)
                .findFirst()
                .orElse(0);
    }

    /**
     * Validates funds and splits the total amount into max-batch sized tasks.
     */
    private void enqueuePurchase(ShopItem shopItem, int amount, String category) {
        if (!this.validateFunds(shopItem, amount))
            return;

        int remaining = amount;
        if (shopItem.maxAmount > 0 && amount > shopItem.maxAmount) {
            System.out.println(String.format("Autobuy: Splitting %s purchase for item %s: total x%,d, batch size x%,d.",
                    category, shopItem.code, amount, shopItem.maxAmount));
        }
        while (remaining > 0) {
            int batch = shopItem.maxAmount > 0 ? Math.min(remaining, shopItem.maxAmount) : remaining;
            this.purchaseQueue.add(new PurchaseTask(shopItem, batch, category));
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
        long retryTime = System.currentTimeMillis() + SHOP_RETRY_DELAY_MS;
        this.categories.values().forEach(categoryState -> {
            categoryState.nextCheck = retryTime;
            categoryState.html = null;
            categoryState.fetched = false;
            categoryState.pending = false;
        });
        this.purchaseQueue.clear();
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
     * Returns the log file count parsed from the span,
     * or -1 if the markup is absent or malformed.
     */
    private int extractLogFileCount(String html) {
        if (html == null) {
            return -1;
        }
        String marker = "<span id=\\\"logFileUpdated\\\">";
        int start = html.indexOf(marker);
        if (start < 0) {
            return -1;
        }
        int valueStart = start + marker.length();
        int end = html.indexOf("<\\/span>", valueStart);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(html.substring(valueStart, end).trim());
        } catch (NumberFormatException e) {
            return -1;
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
        String category = shopObj.get("category").getAsString();
        String code = shopObj.get("code").getAsString();
        double price = shopObj.get("price").getAsDouble();
        String currency = this.convertCurrencyName(shopObj.get("currency").getAsString());
        int maxAmount = shopObj.get("maxAmount").getAsInt();
        return new ShopItem(itemId, category, code, price, currency, maxAmount, shopObj);
    }

    /**
     * Converts currency names from the shop data to match the game naming.
     */
    private String convertCurrencyName(String currency) {
        if ("real".equals(currency)) {
            return "uridium";
        }
        if ("virtual".equals(currency)) {
            return "credits";
        }
        return currency;
    }

    /**
     * Calculates the total cost for purchasing the given amount at the given price,
     * rounding up to the nearest whole unit.
     */
    private int calculateCost(double price, int amount) {
        return (int) Math.ceil(price * amount);
    }

    /**
     * Checks whether the player has enough credits or uridium for the purchase.
     */
    private boolean validateFunds(ShopItem shopItem, int amount) {
        int cost = this.calculateCost(shopItem.price, amount);
        boolean hasEnough;

        switch (shopItem.currency) {
            case "uridium":
                hasEnough = this.stats.getTotalUridium() >= cost;
                break;
            case "credits":
                hasEnough = this.stats.getTotalCredits() >= cost;
                break;
            default:
                System.out.println(String.format("Autobuy: Unsupported currency '%s' for item %s. Skipping purchase.",
                        shopItem.currency, shopItem.code));
                return false;
        }

        if (!hasEnough) {
            System.out.println(String.format("Autobuy: Not enough funds to buy item %s x%,d. Required: %,d %s.",
                    shopItem.code, amount, cost, shopItem.currency));
        }
        return hasEnough;
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    private static class CategoryState {
        final Supplier<? extends AutobuyConfig.AbstractItemConfig> configSupplier;
        long nextCheck = 0;
        String html;
        boolean fetched = false;
        boolean pending = false;

        CategoryState(Supplier<? extends AutobuyConfig.AbstractItemConfig> configSupplier) {
            this.configSupplier = configSupplier;
        }

        boolean isEnabled() {
            AutobuyConfig.AbstractItemConfig categoryConfig = this.configSupplier.get();
            return categoryConfig != null && categoryConfig.anyEnabled();
        }

        int getCheckInterval() {
            AutobuyConfig.AbstractItemConfig categoryConfig = this.configSupplier.get();
            return categoryConfig != null ? categoryConfig.getCheckInterval() : 30;
        }

        boolean shouldFetch(long currentTime) {
            return this.isEnabled() && currentTime >= this.nextCheck;
        }

        void reset() {
            this.html = null;
            this.fetched = false;
            this.pending = false;
        }

        void markFetched() {
            this.fetched = true;
            this.pending = false;
        }

        void updateNextCheck(long currentTime) {
            this.nextCheck = currentTime + (long) this.getCheckInterval() * 60 * 1000L;
        }
    }

    private static class ShopItem {
        final String itemId;
        final String category;
        final String code;
        final double price;
        final String currency;
        final int maxAmount;
        final JsonObject shopObj;

        ShopItem(String itemId, String category, String code, double price,
                String currency, int maxAmount, JsonObject shopObj) {
            this.itemId = itemId;
            this.category = category;
            this.code = code;
            this.price = price;
            this.currency = currency;
            this.maxAmount = maxAmount;
            this.shopObj = shopObj;
        }
    }

    private static class PurchaseTask {
        final ShopItem shopItem;
        final int batch;
        final String category; // For logging purposes only, not same as shopItem.category

        PurchaseTask(ShopItem shopItem, int batch, String category) {
            this.shopItem = shopItem;
            this.batch = batch;
            this.category = category;
        }
    }

}
