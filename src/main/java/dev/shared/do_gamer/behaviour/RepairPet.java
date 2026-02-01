package dev.shared.do_gamer.behaviour;

import dev.shared.do_gamer.config.RepairPetConfig;
import dev.shared.do_gamer.utils.PetGearHelper;
import dev.shared.utils.TemporalModuleDetector;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.util.Timer;

@Feature(name = "Repair PET", description = "Repairs your PET when its health drops below a certain threshold.")
public class RepairPet implements Behavior, Configurable<RepairPetConfig> {
    private final BotAPI bot;
    private final AttackAPI attacker;
    private final PetGearHelper petGearHelper;

    private RepairPetConfig config;
    private boolean repairing = false;
    private final Timer delay = Timer.get();
    private static final long DELAY_MS = 3_000L;
    private static final double MIN_PERCENT = 0.05;
    private static final double MAX_PERCENT = 0.95;
    private static final double COMPLETION_THRESHOLD = 0.99;

    public RepairPet(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
        this.petGearHelper = new PetGearHelper(api);
    }

    @Override
    public void setConfig(ConfigSetting<RepairPetConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        if (this.config == null || !this.config.enabled) {
            return;
        }

        if (this.repairing) {
            this.repair();
            return;
        }

        this.monitorPetHealth();
    }

    /**
     * Monitors the PET's health and initiates repair if below threshold.
     */
    private void monitorPetHealth() {
        if (!this.canRepair()) {
            return;
        }

        if (this.petGearHelper.getHealth().hpPercent() < this.normalizeTriggerThreshold()) {
            this.repairing = true;
        }
    }

    private boolean isActive() {
        return this.petGearHelper.isEnabled() && this.petGearHelper.isActive();
    }

    private boolean isAttacking() {
        return this.attacker.hasTarget() && this.attacker.isAttacking();
    }

    private boolean canRepair() {
        if (!this.isActive()) {
            return false; // Do not repair if PET is inactive
        }

        if (!this.petGearHelper.canUse(PetGear.REPAIR)) {
            return false; // Cannot use repair gear
        }

        if (this.isAttacking() || TemporalModuleDetector.using(this.bot).isTemporal()) {
            this.delay.activate(DELAY_MS);
            return false; // Do not repair while attacking or using temporal module
        }

        if (this.delay.isActive()) {
            return false; // Wait for a delay to prevent trying repair when the target changes
        }

        this.delay.disarm();
        return true;
    }

    private double normalizeTriggerThreshold() {
        double value = this.config.hp;
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
    }

    private void repair() {
        if (!this.canRepair()) {
            return;
        }

        if (!this.petGearHelper.tryUse(PetGear.REPAIR)) {
            this.repairing = false;
            return;
        }

        if (this.petGearHelper.getHealth().hpPercent() >= COMPLETION_THRESHOLD) {
            this.repairing = false; // Repair complete
        }
    }
}
