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
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameScreenAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.StarSystemAPI;

@Feature(name = "Fix PET stuck", description = "Reloads the game when the PET window gets stuck")
public class FixPetStuck implements Behavior, Configurable<FixPetStuckConfig> {

    private static final String PET_GUI = "pet";
    private static final long POST_RELOAD_GRACE_MS = 60_000L;
    // Time to let the game register the closed PET window before reloading
    private static final long WINDOW_CLOSED_DELAY_MS = 2_000L;
    // Reload anyway if the PET window cannot be closed in this time
    private static final long WINDOW_CLOSE_TIMEOUT_MS = 10_000L;
    // No ticks for this long means the game was reloading
    private static final long RELOAD_TICK_GAP_MS = 5_000L;
    // Keep the PET disabled for a while after the game is loaded again
    private static final long RELOAD_SETTLE_MS = 5_000L;
    // Enable the PET again if the reload was never noticed
    private static final long RELOAD_TIMEOUT_MS = 120_000L;

    private enum State {
        MONITORING, // Watching the PET for being stuck
        WAITING, // PET is stuck, waiting for a safe moment to reload
        CLOSING_WINDOW, // PET disabled, closing its window before reloading
        RELOADING // Reload triggered, waiting for the game to load again
    }

    private final BotAPI bot;
    private final HeroAPI hero;
    private final AttackAPI attacker;
    private final EntitiesAPI entities;
    private final StarSystemAPI starSystem;
    private final GameScreenAPI gameScreen;
    private final PetGearHelper petGearHelper;

    private FixPetStuckConfig config;
    private State state = State.MONITORING;
    private long stateSince = 0L;
    private long stuckSince = -1L;
    private long windowClosedAt = -1L;
    private long reloadedAt = -1L;
    private long lastTickAt = -1L;
    private long graceSince = 0L;
    private boolean petDisabled = false;
    private boolean reloadSeen = false;

    public FixPetStuck(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.gameScreen = api.requireAPI(GameScreenAPI.class);
        this.petGearHelper = new PetGearHelper(api);
    }

    @Override
    public void setConfig(ConfigSetting<FixPetStuckConfig> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        boolean resumed = this.updateTickGap();
        if (this.config == null || !this.config.enabled) {
            this.cancel();
            return;
        }

        switch (this.state) {
            case WAITING:
                this.tickWaiting();
                break;
            case CLOSING_WINDOW:
                this.tickClosingWindow();
                break;
            case RELOADING:
                this.tickReloading(resumed);
                break;
            default:
                this.monitorPet();
                break;
        }
    }

    @Override
    public void onStoppedBehavior() {
        if (!this.bot.isRunning()) {
            this.cancel(); // Bot was stopped by the user
        } else if (this.state == State.RELOADING) {
            this.reloadSeen = true; // Bot is running but cannot tick, the game is loading
        }
    }

    /**
     * Updates the last tick time.
     *
     * @return true if there were no ticks for a while (the game was reloading)
     */
    private boolean updateTickGap() {
        long now = System.currentTimeMillis();
        boolean resumed = this.lastTickAt > 0 && (now - this.lastTickAt) >= RELOAD_TICK_GAP_MS;
        this.lastTickAt = now;
        return resumed;
    }

    /**
     * Monitors the PET status and starts the reload when it gets stuck.
     */
    private void monitorPet() {
        if (this.isInGracePeriod() || !this.petGearHelper.isEnabled()) {
            return; // Just reloaded or PET is disabled, nothing to monitor
        }

        if (!this.isInCombat() && !this.isCollecting()) {
            return; // Not in combat or collecting, no need to monitor PET
        }

        if (this.petGearHelper.isActive()) {
            this.stuckSince = -1L; // PET is functioning properly, reset the timer
            return;
        }

        if (this.stuckSince < 0) {
            this.stuckSince = System.currentTimeMillis();
        }

        if (this.elapsed(this.stuckSince) >= (this.config.stuckSeconds * 1_000L)) {
            this.setState(State.WAITING);
        }
    }

    /**
     * Waits for a safe moment, then disables the PET so the bot stops opening its window.
     */
    private void tickWaiting() {
        if (!this.petGearHelper.isEnabled() || this.petGearHelper.isActive()) {
            this.reset(); // PET was disabled or recovered on its own
            return;
        }

        if (this.shouldSkipReload()) {
            return;
        }

        this.petDisabled = true;
        this.petGearHelper.setEnabled(false);
        this.windowClosedAt = -1L;
        this.setState(State.CLOSING_WINDOW);
    }

    /**
     * Closes the PET window, as reloading with it open loads it empty again.
     */
    private void tickClosingWindow() {
        this.petGearHelper.setEnabled(false); // Modules enable the PET again on every tick
        if (this.petGearHelper.isActive()) {
            this.cancel(); // PET recovered on its own
            return;
        }

        boolean ready;
        if (this.closePetWindow()) {
            if (this.windowClosedAt < 0) {
                this.windowClosedAt = System.currentTimeMillis();
            }
            ready = this.elapsed(this.windowClosedAt) >= WINDOW_CLOSED_DELAY_MS;
        } else {
            this.windowClosedAt = -1L;
            ready = this.elapsed(this.stateSince) >= WINDOW_CLOSE_TIMEOUT_MS;
        }

        if (ready && !this.shouldSkipReload()) {
            this.triggerReload();
        }
    }

    /**
     * Keeps the PET disabled until the game is loaded again, then enables it.
     */
    private void tickReloading(boolean resumed) {
        this.petGearHelper.setEnabled(false); // Modules enable the PET again on every tick
        this.closePetWindow();

        if ((resumed || this.reloadSeen) && this.reloadedAt < 0) {
            this.reloadedAt = System.currentTimeMillis();
        }

        boolean settled = this.reloadedAt > 0 && this.elapsed(this.reloadedAt) >= RELOAD_SETTLE_MS;
        boolean timedOut = this.reloadedAt < 0 && this.elapsed(this.stateSince) >= RELOAD_TIMEOUT_MS;
        if (settled || timedOut) {
            this.cancel();
        }
    }

    /**
     * Closes the PET window if it is visible.
     *
     * @return true if the window is closed
     */
    private boolean closePetWindow() {
        Gui gui = this.gameScreen.getGui(PET_GUI);
        if (gui == null || !gui.isVisible()) {
            return true;
        }
        gui.setVisible(false);
        return false;
    }

    private void triggerReload() {
        System.out.println("PET was stuck, reloading the game.");
        this.bot.handleRefresh();
        this.reloadedAt = -1L;
        this.reloadSeen = false;
        this.setState(State.RELOADING);
    }

    /**
     * Stops the fix and enables the PET again if it was disabled by this feature.
     */
    private void cancel() {
        if (this.state == State.MONITORING && !this.petDisabled) {
            return;
        }
        if (this.petDisabled) {
            this.petGearHelper.setEnabled(true);
            this.petDisabled = false;
            this.graceSince = System.currentTimeMillis();
        }
        this.reset();
    }

    private void reset() {
        this.stuckSince = -1L;
        this.windowClosedAt = -1L;
        this.reloadedAt = -1L;
        this.reloadSeen = false;
        this.setState(State.MONITORING);
    }

    private void setState(State state) {
        this.state = state;
        this.stateSince = System.currentTimeMillis();
    }

    private boolean shouldSkipReload() {
        FixPetStuckConfig.ForceReloadConfig force = this.config.forceReload;
        return this.isReloadBlockedInGG(force.gg)
                || (!force.whileAttacking && this.isInCombat())
                || (!force.underAttack && this.isUnderAttack());
    }

    private boolean isReloadBlockedInGG(FixPetStuckConfig.GGMode mode) {
        if (!this.isGGMap()) {
            return false; // GG mode applies only in GG maps
        }
        if (mode == FixPetStuckConfig.GGMode.ALWAYS) {
            return false; // Reload even with NPCs
        }
        if (mode == FixPetStuckConfig.GGMode.NO_NPC) {
            return this.hasActiveNpc(); // Reload only when no NPCs left
        }
        return true; // NEVER: never reload in GG
    }

    private boolean isGGMap() {
        GameMap currentMap = this.starSystem.getCurrentMap();
        return currentMap != null && currentMap.isGG();
    }

    private boolean hasActiveNpc() {
        return !this.entities.getNpcs().isEmpty();
    }

    private boolean isInGracePeriod() {
        return this.graceSince > 0 && this.elapsed(this.graceSince) < POST_RELOAD_GRACE_MS;
    }

    private boolean isInCombat() {
        return this.attacker.hasTarget() && this.attacker.isAttacking();
    }

    private boolean isUnderAttack() {
        return this.entities.getNpcs().stream().anyMatch(npc -> npc.isAttacking(this.hero))
                || this.entities.getPlayers().stream().anyMatch(player -> player.isAttacking(this.hero));
    }

    private boolean isCollecting() {
        return this.hero.hasEffect(EntityEffect.BOX_COLLECTING) || this.hero.hasEffect(EntityEffect.BOOTY_COLLECTING);
    }

    private long elapsed(long since) {
        return System.currentTimeMillis() - since;
    }

}
