package dev.shared.utils;

import java.util.Set;

import eu.darkbot.api.managers.EntitiesAPI;

/**
 * Utility helpers for detecting captcha/verification boxes on the map.
 */
public final class CaptchaBoxDetector {

    private static final Set<String> KNOWN_CAPTCHA_BOX_TYPES = Set.of(
            "POISON_PUSAT_BOX_BLACK",
            "BONUS_BOX_RED");

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
            if (boxName == null) {
                return false;
            }
            return KNOWN_CAPTCHA_BOX_TYPES.contains(boxName);
        });
    }

}
