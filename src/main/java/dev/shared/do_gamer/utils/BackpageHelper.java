package dev.shared.do_gamer.utils;

import java.io.IOException;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.LegacyHangarManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.util.http.Http;

public final class BackpageHelper {
    private final BackpageManager instance;
    private static final String INTERNAL_DOCK = "internalDock";
    private static final String INTERNAL_START = "internalStart";
    private static final String INDEX_INTERNAL_ES = "indexInternal.es";
    private static final String SHOP_PATH = "ajax/shop.php";

    public BackpageHelper(PluginAPI api) {
        this.instance = api.requireInstance(BackpageManager.class);
    }

    public BackpageManager getInstance() {
        return this.instance;
    }

    @SuppressWarnings("deprecation")
    public LegacyHangarManager getLegacyHangarManager() {
        return this.getInstance().legacyHangarManager;
    }

    /**
     * Checks if the BackpageManager instance is valid and has a valid SID status.
     */
    public boolean isValid() {
        return this.getInstance() != null && this.getInstance().isInstanceValid()
                && this.getInstance().getSidStatus().contains("OK");
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
                .setRawParam("action", INTERNAL_DOCK)
                .setRawParam("tpl", INTERNAL_DOCK + page)
                .setHeader("Referer", this.referer(INTERNAL_START))
                .getContent();
    }

    /**
     * Performs a POST request to purchase an item from the shop.
     */
    public void purschaseShopItem(String page, String category, String itemId, int amount) throws IOException {
        this.postHttp(SHOP_PATH)
                .setRawParam("action", "purchase")
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
