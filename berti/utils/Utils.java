package dev.shared.berti.utils;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.HeroAPI;

public class Utils {
    public static Npc GetClosestNpc(HeroAPI hero, Npc current, Npc npc) {
        return current == null || hero.distanceTo((Locatable)current) > hero.distanceTo((Locatable)npc) ? npc : current;
    }

    public static Box GetClosestBox(Box current, Box box, PluginAPI pluginAPI) {
        HeroAPI hero = (HeroAPI)pluginAPI.requireAPI(HeroAPI.class);
        return current == null || hero.distanceTo((Locatable)current) > hero.distanceTo((Locatable)box) ? box : current;
    }
}
