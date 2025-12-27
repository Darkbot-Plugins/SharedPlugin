package dev.shared.do_gamer.utils;

import eu.darkbot.api.managers.EntitiesAPI;

/**
 * Utility helpers for detecting captcha/verification boxes on the map.
 */
public final class CaptchaBoxDetector {

    private CaptchaBoxDetector() {
    }

    /**
     * Returns true if any known captcha boxes are currently present.
     */
    public static boolean hasCaptchaBoxes(EntitiesAPI entities) {
        if (entities == null) {
            return false;
        }
        return entities.getBoxes().stream().anyMatch(box -> {
            String boxName = box.getTypeName();
            return (boxName.equals("POISON_PUSAT_BOX_BLACK") || boxName.equals("BONUS_BOX_RED"));
        });
    }

}

    