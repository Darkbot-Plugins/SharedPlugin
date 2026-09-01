package dev.shared.berke.dailytaskapi;

import eu.darkbot.api.game.other.EntityInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves company-owned maps without relying on the ship's current map. */
final class DailyTaskCompanyMap {
    private static final Pattern COMPANY_MAP = Pattern.compile("^([1-3])-");

    private DailyTaskCompanyMap() {
    }

    static String prefix(EntityInfo.Faction faction, String currentMapName) {
        if (faction == EntityInfo.Faction.MMO) return "1";
        if (faction == EntityInfo.Faction.EIC) return "2";
        if (faction == EntityInfo.Faction.VRU) return "3";
        if (currentMapName == null) return null;
        Matcher matcher = COMPANY_MAP.matcher(currentMapName);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String homeBase(EntityInfo.Faction faction, String currentMapName) {
        return companyMap(faction, currentMapName, "1");
    }

    static String questBase(EntityInfo.Faction faction, String currentMapName) {
        return companyMap(faction, currentMapName, "8");
    }

    private static String companyMap(EntityInfo.Faction faction, String currentMapName, String suffix) {
        String prefix = prefix(faction, currentMapName);
        return prefix == null ? null : prefix + "-" + suffix;
    }
}
