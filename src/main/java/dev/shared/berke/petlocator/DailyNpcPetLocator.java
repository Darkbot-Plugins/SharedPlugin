package dev.shared.berke.petlocator;

import dev.shared.berke.dailytaskapi.DailyTaskAPI;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.selectors.GearSelector;
import eu.darkbot.api.extensions.selectors.PetGearSupplier;
import eu.darkbot.api.extensions.selectors.PrioritizedSupplier;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.PetAPI;

import java.util.Collection;

/** Selects the quest NPC in P.E.T. Enemy Locator while DailyTaskAPI hunts. */
@Feature(
        name = "DailyTaskAPI PET Locator",
        description = "Uses Enemy Locator for the active daily NPC",
        enabledByDefault = true)
public final class DailyNpcPetLocator implements GearSelector, PetGearSupplier {
    private final BotAPI bot;
    private final PetAPI pet;

    public DailyNpcPetLocator(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
    }

    @Override
    public PetGearSupplier getGearSupplier() {
        return this;
    }

    @Override
    public PrioritizedSupplier.Priority getPriority() {
        return activeDaily() == null
                ? PrioritizedSupplier.Priority.LOWEST
                : PrioritizedSupplier.Priority.HIGHEST;
    }

    @Override
    public PetGear get() {
        if (activeDaily() != null && pet.hasGear(PetGear.ENEMY_LOCATOR)) {
            return PetGear.ENEMY_LOCATOR;
        }
        PetGear current = pet.getGear();
        return current == null ? PetGear.PASSIVE : current;
    }

    @Override
    public Boolean enablePet() {
        return activeDaily() == null ? null : Boolean.TRUE;
    }

    @Override
    public PetAPI.LocatorPick getNpcLocatorPick(
            Collection<? extends PetAPI.LocatorPick> available) {
        DailyTaskAPI daily = activeDaily();
        if (daily == null) return PetGearSupplier.super.getNpcLocatorPick(available);
        return available.stream()
                .filter(pick -> matches(daily, pick))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Integer getNpcPickPriority(PetAPI.LocatorPick pick) {
        DailyTaskAPI daily = activeDaily();
        if (daily == null) return PetGearSupplier.super.getNpcPickPriority(pick);
        return matches(daily, pick) ? 0 : null;
    }

    private DailyTaskAPI activeDaily() {
        if (!bot.isRunning()) return null;
        Module module = bot.getNonTemporalModule();
        if (!(module instanceof DailyTaskAPI)) module = bot.getModule();
        if (module instanceof DailyTaskAPI) {
            DailyTaskAPI daily = (DailyTaskAPI) module;
            if (daily.hasNpcLocatorTarget()) return daily;
        }
        return null;
    }

    private boolean matches(DailyTaskAPI daily, PetAPI.LocatorPick pick) {
        return pick != null && daily.matchesNpcLocatorTarget(pick.getName());
    }
}
