package dev.shared.orbithelper.module.daily_quest.handlers;

import java.util.List;

import com.github.manolo8.darkbot.backpage.HangarManager;
import com.github.manolo8.darkbot.backpage.hangar.Hangar;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.managers.GameScreenAPI;

/**
 * Handles ship switching via disconnect/reconnect cycle.
 *
 * Phases:
 * 1. MOVE_TO_SAFE_SPOT — move to nearest portal or station
 * 2. DISCONNECTING — show logout GUI, stop bot
 * 3. WAIT_DISCONNECT — wait for lost_connection screen
 * 4. CHANGE_HANGAR — call hangarManager.changeHangar(targetId) +
 * handleRefresh()
 * 5. WAIT_RECONNECT — wait until hero.isValid() and map.getId() > 0
 * 6. VERIFY — confirm active hangar matches target
 */
public class ShipSwitchHandler {

    private static final long RECONNECT_TIMEOUT_MS = 60_000L;
    private static final long VERIFY_TIMEOUT_MS = 30_000L;
    private static final int MAX_TRIES = 3;

    private enum Phase {
        IDLE, MOVE_TO_SAFE_SPOT, DISCONNECTING, WAIT_DISCONNECT, CHANGE_HANGAR, WAIT_RECONNECT, VERIFY
    }

    private final DailyQuestModule module;
    private final HangarManager hangarManager;
    private final Gui logoutGui;
    private final Gui lostConnectionGui;

    private Phase phase = Phase.IDLE;
    private long phaseEnteredAt = 0;
    private boolean stopBotMode = false;

    private String targetHangarId = null;
    private Runnable onDoneCallback = null;
    private int tries = 0;
    private boolean hangarChanged = false;

    // ---- Cached hangar ID to avoid blocking HTTP on every tick ----
    private String cachedActiveHangarId = null;
    private long cachedHangarIdTime = 0;
    private static final long HANGAR_CACHE_TTL_MS = 10_000L;

    public ShipSwitchHandler(DailyQuestModule module) {
        this.module = module;
        this.hangarManager = module.main.backpage.hangarManager;

        GameScreenAPI screen = module.api.requireAPI(GameScreenAPI.class);
        this.logoutGui = screen.getGui("logout");
        this.lostConnectionGui = screen.getGui("lost_connection");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public boolean isSwitching() {
        return phase != Phase.IDLE;
    }

    public void requestSwitch(String targetHangarId, Runnable onDone) {
        if (isSwitching()) {
            return;
        }

        String currentActive = resolveActiveHangarId();

        if (targetHangarId != null && targetHangarId.equals(currentActive)) {
            if (onDone != null)
                onDone.run();
            return;
        }

        this.targetHangarId = targetHangarId;
        this.onDoneCallback = onDone;
        this.tries = 0;
        this.hangarChanged = false;
        enterPhase(Phase.MOVE_TO_SAFE_SPOT);
    }

    /**
     * Moves to a safe spot, shows the logout GUI, and stops the bot.
     * Unlike requestSwitch(), does NOT reconnect or change hangar after disconnect.
     * Safe to call multiple times — ignored if already active.
     */
    public void requestStopBot() {
        if (isSwitching()) {
            return;
        }
        this.targetHangarId = null;
        this.onDoneCallback = null;
        this.tries = 0;
        this.hangarChanged = false;
        this.stopBotMode = true;
        enterPhase(Phase.MOVE_TO_SAFE_SPOT);
    }

    /**
     * Returns the cached active hangar ID. Non-blocking.
     */
    public String resolveActiveHangarId() {
        long now = System.currentTimeMillis();
        if (cachedActiveHangarId != null && (now - cachedHangarIdTime) < HANGAR_CACHE_TTL_MS) {
            return cachedActiveHangarId;
        }
        if (cachedActiveHangarId == null) {
            cachedActiveHangarId = fetchActiveHangarIdBlocking();
            cachedHangarIdTime = now;
        }
        return cachedActiveHangarId;
    }

    /**
     * Forces an immediate synchronous refresh of the cached hangar ID.
     */
    public String refreshAndResolveActiveHangarId() {
        cachedActiveHangarId = fetchActiveHangarIdBlocking();
        cachedHangarIdTime = System.currentTimeMillis();
        return cachedActiveHangarId;
    }

    /**
     * Invalidates the cache so the next resolveActiveHangarId() call fetches fresh
     * data.
     */
    public void invalidateHangarCache() {
        cachedActiveHangarId = null;
        cachedHangarIdTime = 0;
    }

    /**
     * Blocking HTTP call to fetch the active hangar ID.
     */
    private String fetchActiveHangarIdBlocking() {
        try {
            hangarManager.updateHangarList();
            List<? extends Hangar> hangars = hangarManager.getHangarList().getData().getRet().getHangars();
            if (hangars != null && !hangars.isEmpty()) {
                String active = hangars.stream()
                        .filter(Hangar::isActive)
                        .map(h -> String.valueOf(h.getHangarId()))
                        .findFirst().orElse(null);
                if (active != null) {
                    return active;
                }
            }
        } catch (Exception e) {
            // ignored
        }
        // Fallback: legacyHangarManager
        try {
            return module.backpageManager.legacyHangarManager.getActiveHangar();
        } catch (Exception e) {
            // ignored
            return null;
        }
    }

    // =========================================================================
    // Tick — called from DailyQuestModule.onTickTask() even when bot is stopped
    // =========================================================================
    public void tick() {
        switch (phase) {
            case MOVE_TO_SAFE_SPOT:
                tickMoveToSafeSpot();
                break;
            case DISCONNECTING:
                tickDisconnecting();
                break;
            case WAIT_DISCONNECT:
                tickWaitDisconnect();
                break;
            case CHANGE_HANGAR:
                tickChangeHangar();
                break;
            case WAIT_RECONNECT:
                tickWaitReconnect();
                break;
            case VERIFY:
                tickVerify();
                break;
            default:
                break;
        }
    }

    // =========================================================================
    // Phase: MOVE_TO_SAFE_SPOT — go to nearest portal or base station first
    // =========================================================================

    private static final double SAFE_SPOT_DISTANCE = 200;
    private static final long SAFE_SPOT_TIMEOUT_MS = 30_000L;

    private long safeSpotArrivedAt = 0;

    private void tickMoveToSafeSpot() {
        eu.darkbot.api.game.entities.Entity safeSpot = findNearestSafeSpot();
        long elapsed = System.currentTimeMillis() - phaseEnteredAt;

        if (safeSpot == null || elapsed > SAFE_SPOT_TIMEOUT_MS) {
            tickWaitAndDisconnect();
            return;
        }

        double requiredDistance = safeSpot instanceof eu.darkbot.api.game.entities.Station ? 800 : SAFE_SPOT_DISTANCE;
        if (module.hero.distanceTo(safeSpot) < requiredDistance) {
            tickWaitAndDisconnect();
        } else {
            safeSpotArrivedAt = 0;
            module.movement.moveTo(safeSpot);
        }
    }

    private void tickWaitAndDisconnect() {
        if (module.hero.isMoving()) {
            module.movement.stop(false);
            safeSpotArrivedAt = 0;
            return;
        }
        if (safeSpotArrivedAt == 0) {
            safeSpotArrivedAt = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - safeSpotArrivedAt < 2000L)
            return;
        safeSpotArrivedAt = 0;
        enterPhase(Phase.DISCONNECTING);
    }

    private eu.darkbot.api.game.entities.Entity findNearestSafeSpot() {
        eu.darkbot.api.game.entities.Portal nearestPortal = module.entities.getPortals().stream()
                .filter(p -> p.getTargetMap().map(m -> !m.isGG()).orElse(false))
                .min(java.util.Comparator.comparingDouble(p -> module.hero.distanceTo(p)))
                .orElse(null);

        eu.darkbot.api.game.entities.Station nearestStation = module.entities.getStations().stream()
                .filter(s -> s instanceof eu.darkbot.api.game.entities.Station.Refinery
                        || s instanceof eu.darkbot.api.game.entities.Station.Repair)
                .min(java.util.Comparator.comparingDouble(s -> module.hero.distanceTo(s)))
                .orElse(null);

        if (nearestPortal == null)
            return nearestStation;
        if (nearestStation == null)
            return nearestPortal;
        return module.hero.distanceTo(nearestPortal) < module.hero.distanceTo(nearestStation)
                ? nearestPortal
                : nearestStation;
    }

    // =========================================================================
    // Phase: DISCONNECTING
    // =========================================================================

    private void tickDisconnecting() {
        if (module.bot.isRunning() && module.hero.isMoving()) {
            module.movement.stop(false);
            return;
        }

        if (logoutGui != null && !logoutGui.isVisible()) {
            logoutGui.setVisible(true);
        }

        if (module.bot.isRunning()) {
            module.bot.setRunning(false);
        }

        if (stopBotMode) {
            // Stop bot mode: do not reconnect, just reset to IDLE
            stopBotMode = false;
            phase = Phase.IDLE;
            return;
        }

        enterPhase(Phase.WAIT_DISCONNECT);
    }

    // =========================================================================
    // Phase: WAIT_DISCONNECT — wait for lost_connection screen
    // =========================================================================

    private void tickWaitDisconnect() {
        boolean disconnected = lostConnectionGui != null && lostConnectionGui.isVisible();
        long elapsed = System.currentTimeMillis() - phaseEnteredAt;

        if (disconnected) {
            enterPhase(Phase.CHANGE_HANGAR);
            return;
        }

        if (module.bot.isRunning() && elapsed > 5_000L) {
            if (logoutGui != null)
                logoutGui.setVisible(true);
            module.bot.setRunning(false);
            return;
        }

        if (elapsed > 30_000L) {
            enterPhase(Phase.CHANGE_HANGAR);
        }
    }

    // =========================================================================
    // Phase: CHANGE_HANGAR — call hangarManager.changeHangar(), then refresh
    // Only runs once per phase entry (guard prevents re-entry within same phase)
    // =========================================================================

    private boolean changeHangarExecuted = false;

    private void tickChangeHangar() {
        if (changeHangarExecuted) {
            return;
        }
        changeHangarExecuted = true;
        tries++;

        try {
            @SuppressWarnings("deprecation")
            boolean ok = hangarManager.changeHangar(targetHangarId);
            if (ok) {
                hangarChanged = true;
            }
        } catch (Exception e) {
            // ignored
        }

        module.bot.handleRefresh();
        enterPhase(Phase.WAIT_RECONNECT);
    }

    // =========================================================================
    // Phase: WAIT_RECONNECT
    // =========================================================================

    private void tickWaitReconnect() {
        boolean heroValid = module.hero.isValid();
        boolean mapValid = module.hero.getMap() != null && module.hero.getMap().getId() > 0;
        long elapsed = System.currentTimeMillis() - phaseEnteredAt;

        if (heroValid && mapValid) {
            module.bot.setRunning(true);

            if (hangarChanged) {
                enterPhase(Phase.VERIFY);
            } else if (tries < MAX_TRIES) {
                enterPhase(Phase.DISCONNECTING);
            } else {
                finish();
            }
            return;
        }

        if (elapsed > RECONNECT_TIMEOUT_MS) {
            module.bot.setRunning(true);
            finish();
        }
    }

    // =========================================================================
    // Phase: VERIFY — confirm active hangar via hangarManager
    // =========================================================================

    private void tickVerify() {
        long elapsed = System.currentTimeMillis() - phaseEnteredAt;

        try {
            hangarManager.updateHangarList();
            List<? extends Hangar> hangars = hangarManager.getHangarList().getData().getRet().getHangars();

            String activeId = hangars.stream()
                    .filter(Hangar::isActive)
                    .map(h -> String.valueOf(h.getHangarId()))
                    .findFirst().orElse(null);

            if (targetHangarId.equals(activeId)) {
                finish();
                return;
            }
        } catch (Exception e) {
            // ignored
        }

        if (elapsed > VERIFY_TIMEOUT_MS) {
            finish();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void enterPhase(Phase next) {
        if (next == Phase.CHANGE_HANGAR) {
            changeHangarExecuted = false;
        }
        phase = next;
        phaseEnteredAt = System.currentTimeMillis();
    }

    private void finish() {
        stopBotMode = false;
        phase = Phase.IDLE;
        if (targetHangarId != null) {
            cachedActiveHangarId = targetHangarId;
            cachedHangarIdTime = System.currentTimeMillis();
        }
        Runnable cb = onDoneCallback;
        onDoneCallback = null;
        targetHangarId = null;
        if (cb != null)
            cb.run();
    }
}