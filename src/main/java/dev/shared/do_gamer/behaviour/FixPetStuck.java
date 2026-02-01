package dev.shared.do_gamer.behaviour;

import dev.shared.do_gamer.config.FixPetStuckConfig;
import dev.shared.do_gamer.utils.PetGearHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.StarSystemAPI;

@Feature(name = "Fix PET stuck", description = "Reloads the game when the PET window gets stuck")
public class FixPetStuck implements Behavior, Configurable<FixPetStuckConfig> {

    private final BotAPI bot;
    private final HeroAPI hero;
    private final AttackAPI attacker;
    private final EntitiesAPI entities;
    private final StarSystemAPI starSystem;
    private final PetGearHelper petGearHelper;

    private FixPetStuckConfig config;
    private long stuckSince = -1L;
    private long lastReloadAt = 0L;
    private boolean reload = false;
    private static final long POST_RELOAD_GRACE_MS = 60_000L;

    public FixPetStuck(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.petGearHelper = new PetGearHelper(api);
    }

    @Override
    public void setConfig(ConfigSetting<FixPetStuckConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        if (this.config == null || !this.config.enabled || this.isInGracePeriod()) {
            return;
        }

        if (this.reload) {
            this.triggerReload();
            return;
        }

        this.monitorPet();
    }

    /**
     * Monitors the PET status and triggers a reload if it gets stuck.
     */
    private void monitorPet() {
        if (!this.petGearHelper.isEnabled()) {
            return; // PET is disabled, nothing to monitor
        }

        if (!this.isInCombat() && !this.isCollecting()) {
            return; // Not in combat or collecting, no need to monitor PET
        }

        if (this.petGearHelper.isActive()) {
            // PET is functioning properly, reset the timer
            this.reset();
            return;
        }

        if (this.stuckSince < 0) {
            this.stuckSince = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - this.stuckSince;
        if (elapsed >= (this.config.stuckSeconds * 1_000L)) {
            this.reload = true;
        }
    }

    private boolean shouldSkipReload() {
        GameMap currentMap = this.starSystem.getCurrentMap();
        // Skip reload in GG maps with active NPCs
        if (currentMap != null && currentMap.isGG() && this.hasActiveNpc()) {
            return true;
        }

        // Skip reload if currently attacking
        return this.attacker.hasTarget() && this.attacker.isAttacking();
    }

    private boolean hasActiveNpc() {
        return this.entities != null && !this.entities.getNpcs().isEmpty();
    }

    private boolean isInGracePeriod() {
        return this.lastReloadAt > 0 && (System.currentTimeMillis() - this.lastReloadAt) < POST_RELOAD_GRACE_MS;
    }

    private boolean isInCombat() {
        return this.attacker.hasTarget() && this.attacker.isAttacking();
    }

    private boolean isCollecting() {
        return this.hero.hasEffect(EntityEffect.BOX_COLLECTING) || this.hero.hasEffect(EntityEffect.BOOTY_COLLECTING);
    }

    private void triggerReload() {
        if (this.shouldSkipReload()) {
            return;
        }

        System.out.println("PET was stuck, reloading the game.");
        this.bot.handleRefresh();
        this.lastReloadAt = System.currentTimeMillis();
        this.reset();
    }

    private void reset() {
        this.stuckSince = -1L;
        this.reload = false;
    }

}
