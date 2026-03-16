package dev.shared.orbithelper.module.daily_quest.handlers.quest_executions;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;

import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.shared.modules.LootModule;

import java.util.Map;

public class KillNpcHandler {

    private final DailyQuestModule module;
    private final LootModule lootModule;

    // Tracks the last configured NPC to avoid redundant disableAllNpcs calls on
    // every tick
    private String lastConfiguredNpc = null;

    public KillNpcHandler(DailyQuestModule module) {
        this.module = module;
        this.lootModule = new LootModule(module.api);
    }

    // =========================================================================
    // Execution
    // =========================================================================

    public void execute(ExecutionQuest quest) {
        if (quest.targetNpc == null)
            return;

        // On quest switch, reset all NPC configs before enabling only the new target
        if (!quest.targetNpc.equals(lastConfiguredNpc)) {
            disableAllNpcs();
            lastConfiguredNpc = quest.targetNpc;
        }

        // Enable only the target NPC in the global loot config
        updateNpcConfig(quest.targetNpc, true);

        // Activate Pet Enemy Locator if configured
        if (module.getConfig().questTypes.killNpcSettings.useEnemyLocator) {
            module.forcePetEnabled(true);
            module.pet.setEnabled(true);
        }

        // Delegate attack and movement to DarkBot's built-in LootModule
        lootModule.onTickModule();

        if (module.getConfig().questTypes.killNpcSettings.useEnemyLocator) {
            setPetGear(PetGear.ENEMY_LOCATOR);
        }
    }

    /**
     * Disables the given NPC in the loot config and resets the tracking state.
     * Should be called when the current kill quest completes or is abandoned.
     */
    public void cleanup(String targetNpc) {
        if (targetNpc == null)
            return;
        updateNpcConfig(targetNpc, false);
        lastConfiguredNpc = null;
        // Restore pet.enabled config to its original value
        module.forcePetEnabled(false);
        module.pet.setEnabled(false);
    }

    // =========================================================================
    // NPC Config Helpers
    // =========================================================================

    /**
     * Disables all NPCs in the loot config.
     * Called once per quest switch to ensure only the new target is active.
     */
    private void disableAllNpcs() {
        Map<String, NpcInfo> infos = getNpcInfos();
        if (infos == null)
            return;

        for (NpcInfo info : infos.values()) {
            if (!info.getShouldKill())
                continue;

            info.setShouldKill(false);
            info.setPriority(0);
            setNpcFlags(info, false, false, false);
        }
    }

    /**
     * Enables or disables a specific NPC in the loot config by exact name match.
     * Strips non-alphanumeric characters before comparing (case-sensitive).
     */
    private void updateNpcConfig(String targetName, boolean enable) {
        Map<String, NpcInfo> infos = getNpcInfos();
        if (infos == null)
            return;

        String cleanTarget = cleanNpcName(targetName);

        for (Map.Entry<String, NpcInfo> entry : infos.entrySet()) {
            NpcInfo info = entry.getValue();
            String rawName = info.getName() != null ? info.getName() : entry.getKey();

            if (!cleanNpcName(rawName).equals(cleanTarget))
                continue;

            info.setShouldKill(enable);
            info.setPriority(enable ? -100 : 0);

            boolean useLocator = enable && module.getConfig().questTypes.killNpcSettings.useEnemyLocator;
            boolean ignoreOwner = enable && module.getConfig().questTypes.killNpcSettings.ignoreOwnership;
            setNpcFlags(info, useLocator, ignoreOwner, false);
        }
    }

    /**
     * Applies NpcFlag values to a given NpcInfo entry.
     *
     * @param petLocator      whether PET_LOCATOR should be enabled
     * @param ignoreOwnership whether IGNORE_OWNERSHIP and IGNORE_ATTACKED should be
     *                        enabled
     * @param passive         whether PASSIVE should be enabled (always false in
     *                        practice)
     */
    private void setNpcFlags(NpcInfo info, boolean petLocator, boolean ignoreOwnership, boolean passive) {
        for (NpcFlag flag : NpcFlag.values()) {
            switch (flag.name()) {
                case "PET_LOCATOR":
                    info.setExtraFlag(flag, petLocator);
                    break;
                case "PASSIVE":
                    info.setExtraFlag(flag, passive);
                    break;
                case "IGNORE_OWNERSHIP":
                case "IGNORE_ATTACKED":
                    info.setExtraFlag(flag, ignoreOwnership);
                    break;
                default:
                    break;
            }
        }
    }

    // =========================================================================
    // Pet Helper
    // =========================================================================

    private void setPetGear(PetGear gear) {
        if (!module.pet.isActive())
            return;
        if (!module.pet.hasGear(gear))
            return;
        if (module.pet.getGear() == gear)
            return;

        try {
            module.pet.setGear(gear);
        } catch (Exception ignored) { // gear not available or internal API error
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private Map<String, NpcInfo> getNpcInfos() {
        if (module.getNpcInfos() == null || module.getNpcInfos().getValue() == null)
            return null;
        return module.getNpcInfos().getValue();
    }

    /**
     * Strips non-alphanumeric characters (except spaces) and trims whitespace.
     * Case is preserved for case-sensitive matching.
     */
    private String cleanNpcName(String name) {
        if (name == null)
            return "";
        return name.replaceAll("[^a-zA-Z0-9 ]", "").trim();
    }
}