package dev.shared.do_gamer.utils;

import java.util.List;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.other.Health;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;

/**
 * Helper for safely managing PET gear usage.
 */
public class PetGearHelper {

    private final PetAPI pet;
    private final ConfigAPI configApi;

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
        this.configApi = api.requireAPI(ConfigAPI.class);
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
     * Sets the PET to passive mode.
     */
    public boolean setPassive() {
        return this.setPassive(false);
    }

    /**
     * Sets the PET to passive mode with usage confirmation.
     */
    public boolean setPassive(boolean confirm) {
        return this.tryUse(PetGear.PASSIVE) && (!confirm || this.isUsing(PetGear.PASSIVE));
    }

    /**
     * Sets the PET to guard mode.
     */
    public boolean setGuard() {
        return this.tryUse(PetGear.GUARD);
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
        return currentGear != null && currentGear != gear && RESTRICTED_GEARS.contains(currentGear);
    }

    /**
     * Checks if the PET is currently using the specified gear.
     */
    public boolean isUsing(PetGear gear) {
        PetGear currentGear = this.pet.getGear();
        return currentGear != null && currentGear == gear;
    }

    /**
     * Checks if the PET is enabled.
     */
    public boolean isEnabled() {
        // Check both config and PET status
        boolean configEnabled = this.configApi.getConfigValue("pet.enabled");
        return configEnabled && this.pet.isEnabled();
    }

    /**
     * Sets the PET enabled or disabled.
     */
    public void setEnabled(boolean enabled) {
        this.pet.setEnabled(enabled);
    }

    /**
     * Checks if the PET is active.
     */
    public boolean isActive() {
        return this.pet.isActive();
    }

    /**
     * Gets the PET's health.
     */
    public Health getHealth() {
        return this.pet.getHealth();
    }
}
