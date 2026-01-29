package dev.shared.do_gamer.utils;

import java.util.List;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;

/**
 * Helper for safely managing PET gear usage.
 */
public class PetGearHelper {

    private final PetAPI pet;

    // List of gears that restrict the use of other gears when active
    private static final List<PetGear> RESTRICTED_GEARS = List.of(
            PetGear.COMBO_REPAIR,
            PetGear.COMBO_GUARD,
            PetGear.HP_LINK,
            PetGear.REPAIR,
            PetGear.TRADER,
            PetGear.KAMIKAZE,
            PetGear.SACRIFICIAL,
            PetGear.HEAT_RELEASE);

    public PetGearHelper(PluginAPI api) {
        this.pet = api.requireAPI(PetAPI.class);
    }

    /**
     * Attempts to use the specified gear if possible.
     */
    public boolean tryUse(PetGear gear) {
        if (this.canUse(gear)) {
            try {
                this.pet.setGear(gear);
                return true;
            } catch (ItemNotEquippedException ignored) {
                // Ignore not equipped exception
            }
        }
        return false;
    }

    /**
     * Checks if the PET can use the specified gear.
     */
    public boolean canUse(PetGear gear) {
        return !this.isRestricted(gear) && this.pet.hasGear(gear) && !this.pet.hasCooldown(gear);
    }

    /**
     * Checks if the PET is currently using a gear that restricts the use of others.
     */
    private boolean isRestricted(PetGear gear) {
        PetGear currentGear = this.pet.getGear();
        return currentGear != gear && RESTRICTED_GEARS.contains(currentGear);
    }
}
