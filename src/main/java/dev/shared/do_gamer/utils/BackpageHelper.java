package dev.shared.do_gamer.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.HangarManager;
import com.github.manolo8.darkbot.backpage.hangar.HangarResponse;
import com.github.manolo8.darkbot.backpage.hangar.Ret;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.util.IOUtils;
import eu.darkbot.util.http.Http;

public final class BackpageHelper {
    private final BackpageManager instance;
    private static final String INTERNAL_DOCK = "internalDock";
    private static final String INTERNAL_START = "internalStart";
    private static final String INDEX_INTERNAL_ES = "indexInternal.es";
    private static final String SHOP_PATH = "ajax/shop.php";
    private static final String INVENTORY_PATH = "flashAPI/inventory.php";
    private static final String ACTION = "action";
    private long lastHangarDataUpdate = 0;
    private long nextHangarChange = 0;

    public BackpageHelper(PluginAPI api) {
        this.instance = api.requireInstance(BackpageManager.class);
    }

    public BackpageManager getInstance() {
        return this.instance;
    }

    public HangarManager getHangarManager() {
        return this.getInstance().hangarManager;
    }

    /**
     * Refreshes the hangar list and the active hangar data if older than the
     * given expiry time.
     */
    public void updateHangarData(long expiryTime) {
        if (System.currentTimeMillis() <= this.lastHangarDataUpdate + expiryTime) {
            return;
        }
        try {
            // updateCurrentHangar only fetches the hangar list when it is null
            this.getHangarManager().updateHangarList();
            this.getHangarManager().updateCurrentHangar();
            this.lastHangarDataUpdate = System.currentTimeMillis();
        } catch (Exception e) {
            System.out.println("BackpageHelper: Could not update hangar data: " + e.getMessage());
        }
    }

    /**
     * Returns the "ret" payload of the active hangar, or null if not loaded yet.
     */
    public Ret getCurrentHangarRet() {
        HangarResponse hangar = this.getHangarManager().getCurrentHangar();
        if (hangar == null || hangar.getData() == null) {
            return null;
        }
        return hangar.getData().getRet();
    }

    /**
     * Returns the ID of the active hangar, or 0 if the hangar list is not loaded.
     */
    public int getActiveHangarId() {
        return this.getHangarManager().getCurrentHangarId();
    }

    /**
     * Requests to activate the given hangar. The ship must be in a base or
     * disconnected.
     */
    public boolean changeHangar(int hangarId) {
        int activeHangarId = this.getActiveHangarId();
        if (this.nextHangarChange > System.currentTimeMillis() || activeHangarId == 0 || !this.isValid()) {
            return false;
        }

        JsonObject hangarObj = new JsonObject();
        hangarObj.addProperty("hi", activeHangarId);
        hangarObj.addProperty("hangarId", hangarId);
        JsonObject paramObj = new JsonObject();
        paramObj.add("params", hangarObj);

        String params = Base64.getEncoder()
                .encodeToString(paramObj.toString().getBytes(StandardCharsets.UTF_8));
        try {
            return this.postHttp(INVENTORY_PATH)
                    .addSupplier(() -> this.nextHangarChange = System.currentTimeMillis() + 12_000)
                    .setRawParam(ACTION, "activateShip")
                    .setParam("params", params)
                    .consumeInputStream(in -> IOUtils.read(Base64.getDecoder().wrap(in)))
                    .contains("\"isError\":0");
        } catch (IOException e) {
            System.out.println("BackpageHelper: Could not change hangar: " + e.getMessage());
            this.nextHangarChange = System.currentTimeMillis() + 5_000;
            return false;
        }
    }

    /**
     * Checks if the BackpageManager instance is valid and has a valid SID status.
     */
    public boolean isValid() {
        return this.getInstance().isInstanceValid() && this.getInstance().getSidStatus().contains("OK");
    }

    /**
     * Parses the given JSON string into a JsonObject.
     */
    public JsonObject parseJson(String json) {
        return this.parseJson(json, null);
    }

    /**
     * Parses the given JSON string into a JsonObject and optionally extracts a
     * member by name.
     */
    public JsonObject parseJson(String json, String memberName) {
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        if (memberName == null) {
            return parsed;
        }

        JsonElement itemDataElement = parsed.get(memberName);
        if (itemDataElement == null || itemDataElement.isJsonNull() || !itemDataElement.isJsonObject()) {
            return null;
        }
        return itemDataElement.getAsJsonObject();
    }

    /**
     * Extracts the itemData object from the raw shop page HTML.
     */
    public JsonObject parseShopItemData(String html) {
        String json = this.extractShopParametersJson(html);
        if (json == null) {
            return null;
        }
        return this.parseJson(json, "itemData");
    }

    /**
     * Finds and returns the outermost JSON object following "Shop.Parameters" in
     * the HTML.
     */
    private String extractShopParametersJson(String html) {
        if (html == null) {
            return null;
        }

        int start = html.indexOf("Shop.Parameters");
        if (start < 0) {
            return null;
        }

        int braceStart = html.indexOf("{", start);
        if (braceStart < 0) {
            return null;
        }

        int depth = 0;
        for (int i = braceStart; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return html.substring(braceStart, i + 1);
                }
            }
        }
        return null;
    }

    public Http getHttp(String path) {
        return this.getInstance().getHttp(path);
    }

    public Http postHttp(String path) {
        return this.getInstance().postHttp(path);
    }

    /**
     * Performs a GET request for the shop page.
     */
    public String fetchShopPage(String page) throws IOException {
        return this.getHttp(INDEX_INTERNAL_ES)
                .setRawParam(ACTION, INTERNAL_DOCK)
                .setRawParam("tpl", INTERNAL_DOCK + page)
                .setHeader("Referer", this.referer(INTERNAL_START))
                .getContent();
    }

    /**
     * Performs a POST request to purchase an item from the shop.
     */
    public void purchaseShopItem(String page, String category, String itemId, int amount) throws IOException {
        this.postHttp(SHOP_PATH)
                .setRawParam(ACTION, "purchase")
                .setRawParam("category", category)
                .setRawParam("itemId", itemId)
                .setRawParam("amount", amount)
                .setRawParam("level", "")
                .setRawParam("selectedName", "")
                .setHeader("Referer", this.referer(INTERNAL_DOCK, INTERNAL_DOCK + page))
                .getContent();
    }

    /**
     * Constructs a referer URL.
     */
    public String referer(String action, String tpl) {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getInstance().getInstanceURI())
                .append(INDEX_INTERNAL_ES)
                .append("?action=")
                .append(action);
        if (tpl != null) {
            builder.append("&tpl=").append(tpl);
        }
        return builder.toString();
    }

    /**
     * Overloaded method to construct a referer URL without a tpl parameter.
     */
    public String referer(String action) {
        return this.referer(action, null);
    }
}
