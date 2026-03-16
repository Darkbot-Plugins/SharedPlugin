package dev.shared.orbithelper.module.daily_quest.handlers.quest_executions;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.handlers.ShipSwitchHandler;

import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;

import static dev.shared.orbithelper.module.daily_quest.Constants.*;

import eu.darkbot.shared.modules.LootModule;
import eu.darkbot.api.game.entities.Box;

import java.util.Comparator;

/**
 * Handles COLLECT and SALVAGE quest execution.
 *
 * COLLECT ores:
 * - Prometium / Endurium / Terbium → mine raw ore boxes (ore_0/1/2)
 * - Prometid / Duranium / Promerium → collect FROM_SHIP boxes in x-6/x-7
 * - SALVAGE requirement → collect FROM_SHIP boxes on current map
 *
 * Ship switch (shipForCollect configured):
 * - First time: switch ship, then collect
 * - After all collect/salvage done: switch back
 *
 * Cargo full (no ship switch):
 * - Quest deferred to end of queue
 */
public class CollectHandler {

    private final DailyQuestModule module;
    private final ShipSwitchHandler shipSwitch;

    private enum Phase {
        IDLE, SWITCHING_TO_COLLECT, COLLECTING, SWITCHING_BACK
    }

    private Phase phase = Phase.IDLE;

    private final LootModule lootModule;
    private boolean salvageNpcsConfigured = false;

    private String originalHangarId = null;
    private boolean shipSwitchDone = false;

    private static final long ORE_CHECK_MS = 150;
    private long lastOreCheck = 0;

    public CollectHandler(DailyQuestModule module, ShipSwitchHandler shipSwitch) {
        this.module = module;
        this.shipSwitch = shipSwitch;
        this.lootModule = new LootModule(module.api);
    }

    /**
     * Passively picks up FROM_SHIP boxes while doing KILL_NPC quests.
     * Called only when shipForCollect is not configured.
     * If cargo is full, does nothing (KILL_NPC continues normally).
     */
    public void collectPassiveFromShip() {
        if (isCargoFull())
            return;
        if (System.currentTimeMillis() - lastOreCheck < ORE_CHECK_MS)
            return;

        lastOreCheck = System.currentTimeMillis();

        Box nearest = module.entities.getBoxes().stream()
                .filter(b -> b.getTypeName().contains(FROM_SHIP_BOX_TYPE))
                .min(Comparator.comparingDouble(b -> module.hero.distanceTo(b)))
                .orElse(null);

        if (nearest != null && module.hero.distanceTo(nearest) < 500) {
            nearest.tryCollect();
        }
    }

    // =========================================================================
    // Main tick
    // =========================================================================

    public void execute(ExecutionQuest quest) {
        if (shipSwitch.isSwitching()) {
            shipSwitch.tick();
            return;
        }

        switch (phase) {
            case IDLE:
                handleIdle();
                break;
            case SWITCHING_TO_COLLECT:
                break;
            case COLLECTING:
                handleCollecting(quest);
                break;
            case SWITCHING_BACK:
                break;
        }
    }

    /**
     * Called by ExecutionHandler when the quest progress reaches 0.
     * 
     * @return true if fully completed (safe to advance), false if still restoring
     *         ship.
     */
    public boolean handlePostQuest() {
        reset();
        return true;
    }

    public void reset() {
        phase = Phase.IDLE;
        shipSwitchDone = false;
        originalHangarId = null;
        if (salvageNpcsConfigured) {
            configureSalvageNpcs(false);
            salvageNpcsConfigured = false;
        }
    }

    // =========================================================================
    // Hooks
    // =========================================================================

    public boolean checkPreTravel() {
        if (phase != Phase.IDLE)
            return false;

        String shipForCollect = module.config.questTypes.collectOresSettings.shipForCollect;
        if (shipForCollect == null || shipForCollect.isEmpty())
            return false;

        if (originalHangarId == null) {
            originalHangarId = resolveCurrentHangarId();
            if (originalHangarId == null)
                return true;
        }

        if (!originalHangarId.equals(shipForCollect) && !shipSwitchDone) {
            if (!module.isShipSwitched) {
                module.isShipSwitched = true;
                module.restoreShipId = originalHangarId;
            }
            phase = Phase.SWITCHING_TO_COLLECT;
            shipSwitch.requestSwitch(shipForCollect, () -> {
                shipSwitchDone = true;
                phase = Phase.IDLE;
            });
            return true;
        }
        return false;
    }

    // =========================================================================
    // IDLE
    // =========================================================================

    private void handleIdle() {
        String shipForCollect = module.config.questTypes.collectOresSettings.shipForCollect;
        boolean needsSwitch = shipForCollect != null && !shipForCollect.isEmpty();

        if (!needsSwitch && isCargoFull()) {
            module.questManager.deferCurrentQuestToEnd();
            return;
        }

        if (needsSwitch && !shipSwitchDone) {
            if (originalHangarId == null) {
                originalHangarId = resolveCurrentHangarId();
            }
            if (shipForCollect.equals(originalHangarId)) {
                shipSwitchDone = true;
            }
        }

        phase = Phase.COLLECTING;
    }

    // =========================================================================
    // COLLECTING
    // =========================================================================

    private void handleCollecting(ExecutionQuest quest) {
        if (isCargoFull()) {
            String shipForCollect = module.config.questTypes.collectOresSettings.shipForCollect;
            if (shipForCollect == null || shipForCollect.isEmpty()) {
                module.questManager.deferCurrentQuestToEnd();
            }
            return;
        }

        boolean fromShip = isFromShipOre(quest.targetOre) || "SALVAGE".equals(quest.type);

        if (fromShip) {
            handleFromShipCollection(quest);
        } else {
            if (System.currentTimeMillis() - lastOreCheck >= ORE_CHECK_MS) {
                lastOreCheck = System.currentTimeMillis();
                collectRawOre(quest);
            }
        }
    }

    private void handleFromShipCollection(ExecutionQuest quest) {
        Box nearest = findNearestFromShipBox();
        if (nearest != null) {
            nearest.tryCollect();
            module.movement.moveTo(nearest);
            return;
        }
        if ("SALVAGE".equals(quest.type) && module.config.questTypes.collectOresSettings.killNpcsForSalvage) {
            killNpcsForSalvage();
        } else if (!module.movement.isMoving()) {
            module.movement.moveRandom();
        }
    }

    // =========================================================================
    // SALVAGE NPC Killing
    // =========================================================================

    /**
     * Configures SALVAGE_NPCS in the loot config and delegates to LootModule.
     * Excludes Blighted and Plagued variants.
     */
    private void killNpcsForSalvage() {
        if (!salvageNpcsConfigured) {
            configureSalvageNpcs(true);
            salvageNpcsConfigured = true;
        }
        lootModule.onTickModule();
    }

    /**
     * Enables or disables SALVAGE_NPCS in the global loot NPC config.
     */
    private void configureSalvageNpcs(boolean enable) {
        java.util.Map<String, eu.darkbot.api.config.types.NpcInfo> infos = getNpcInfos();
        if (infos == null)
            return;

        for (java.util.Map.Entry<String, eu.darkbot.api.config.types.NpcInfo> entry : infos.entrySet()) {
            eu.darkbot.api.config.types.NpcInfo info = entry.getValue();
            String rawName = info.getName() != null ? info.getName() : entry.getKey();
            if (rawName.contains("Blighted") || rawName.contains("Plagued"))
                continue;

            int priority = getSalvagePriority(rawName);
            boolean isSalvageNpc = priority != Integer.MAX_VALUE;
            info.setShouldKill(enable && isSalvageNpc);
            info.setPriority(enable && isSalvageNpc ? -100 + priority : 0);
        }
    }

    /**
     * Returns the SALVAGE_NPCS priority index for the given NPC name, or MAX_VALUE
     * if not found.
     */
    private int getSalvagePriority(String npcName) {
        String cleanName = cleanNpcName(npcName);
        for (int i = 0; i < SALVAGE_NPCS.size(); i++) {
            if (cleanName.equals(cleanNpcName(SALVAGE_NPCS.get(i))))
                return i;
        }
        return Integer.MAX_VALUE;
    }

    private String cleanNpcName(String name) {
        if (name == null)
            return "";
        return name.replaceAll("[^a-zA-Z0-9 ]", "").trim();
    }

    private java.util.Map<String, eu.darkbot.api.config.types.NpcInfo> getNpcInfos() {
        if (module.getNpcInfos() == null || module.getNpcInfos().getValue() == null)
            return null;
        return module.getNpcInfos().getValue();
    }

    // =========================================================================
    // Collection strategies
    // =========================================================================

    private void collectRawOre(ExecutionQuest quest) {
        String boxType = ORE_BOX_TYPES.get(quest.targetOre);
        if (boxType == null)
            return;

        Box nearest = module.entities.getBoxes().stream()
                .filter(b -> b.getTypeName().equals(boxType))
                .min(Comparator.comparingDouble(b -> module.hero.distanceTo(b)))
                .orElse(null);

        if (nearest != null) {
            nearest.tryCollect();
            module.movement.moveTo(nearest);
        } else {
            if (!module.movement.isMoving())
                module.movement.moveRandom();
        }
    }

    private Box findNearestFromShipBox() {
        return module.entities.getBoxes().stream()
                .filter(b -> b.getTypeName().contains(FROM_SHIP_BOX_TYPE))
                .min(Comparator.comparingDouble(b -> module.hero.distanceTo(b)))
                .orElse(null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isFromShipOre(String ore) {
        return ore != null && FROM_SHIP_ORES.contains(ore);
    }

    private boolean isCargoFull() {
        int max = module.stats.getMaxCargo();
        return max > 0 && module.stats.getCargo() >= max;
    }

    private String resolveCurrentHangarId() {
        return module.shipSwitchHandler.resolveActiveHangarId();
    }
}