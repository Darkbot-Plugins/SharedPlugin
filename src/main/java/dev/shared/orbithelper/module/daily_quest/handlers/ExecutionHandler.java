package dev.shared.orbithelper.module.daily_quest.handlers;

import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.handlers.quest_executions.CollectHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.quest_executions.KillNpcHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.quest_executions.KillPlayersHandler;
import dev.shared.orbithelper.module.daily_quest.handlers.quest_executions.SellOreHandler;
import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;

public class ExecutionHandler {

    private static final String TYPE_KILL_NPC = "KILL_NPC";
    private static final String TYPE_COLLECT = "COLLECT";
    private static final String TYPE_SALVAGE = "SALVAGE";
    private static final String TYPE_SELL_ORE = "SELL_ORE";
    private static final String TYPE_KILL_PLAYERS = "KILL_PLAYERS";

    private final DailyQuestModule module;

    // -------------------------------------------------------------------------
    // Quest-type handlers
    // -------------------------------------------------------------------------

    private final KillNpcHandler killNpcHandler;
    private final CollectHandler collectHandler;
    private final SellOreHandler sellOreHandler;
    private final KillPlayersHandler killPlayersHandler;

    public ExecutionHandler(DailyQuestModule module) {
        this.module = module;
        this.killNpcHandler = new KillNpcHandler(module);
        this.collectHandler = new CollectHandler(module, module.shipSwitchHandler);
        this.sellOreHandler = new SellOreHandler(module, module.shipSwitchHandler);
        this.killPlayersHandler = new KillPlayersHandler(module);
    }

    // =========================================================================
    // Main Tick
    // =========================================================================

    public String getSellOrePhaseStatus() {
        return sellOreHandler.getSellPhaseStatus();
    }

    public void handleExecutingQuest() {
        module.repairApi.resetDeaths();
        ExecutionQuest currentQuest = module.questManager.getCurrentQuest();

        if (currentQuest == null) {
            module.questManager.setupNextQuest();
            return;
        }

        if (module.shipSwitchHandler.isSwitching()) {
            return;
        }

        // Quest is fully done — check if handlers need cleanup before advancing
        if (currentQuest.remaining <= 0) {
            if (isQuestFullyComplete(currentQuest)) {
                onQuestComplete(currentQuest);
            }
            return;
        }

        // Ensure the correct quest is selected in the UI before reading progress
        if (!module.questManager.ensureQuestSelected()) {
            if (!module.questManager.isMapTransitioning()) {
                handleQuestGone(currentQuest);
            }
            return;
        }

        // Sync live quest progress into the current quest
        module.questManager.updateCurrentQuestStatus();

        if (!currentQuest.active)
            return;

        if (checkPreTravel(currentQuest)) {
            return; // Controller yielded to pre-travel logic (like switching ships)
        }

        if (TYPE_SELL_ORE.equals(currentQuest.type)) {
            dispatch(currentQuest);
            return;
        }

        module.questManager.tryUpdateTargetMapToCurrent(currentQuest);

        if (!isOnTargetMap(currentQuest.map)) {
            travelToMap(currentQuest.map);
            return;
        }

        dispatch(currentQuest);
    }

    // =========================================================================
    // Dispatch
    // =========================================================================

    private void dispatch(ExecutionQuest quest) {
        switch (quest.type) {
            case TYPE_KILL_NPC:
                killNpcHandler.execute(quest);
                break;
            case TYPE_COLLECT:
            case TYPE_SALVAGE:
                collectHandler.execute(quest);
                break;
            case TYPE_SELL_ORE:
                sellOreHandler.execute(quest);
                break;
            case TYPE_KILL_PLAYERS:
                killPlayersHandler.execute();
                break;
            default:
                // Unknown quest type — skip silently
                break;
        }
    }

    private boolean checkPreTravel(ExecutionQuest quest) {
        switch (quest.type) {
            case TYPE_COLLECT:
            case TYPE_SALVAGE:
                if (collectHandler.checkPreTravel()) {
                    return true;
                }
                break;
            case TYPE_SELL_ORE:
                if (sellOreHandler.checkPreTravel()) {
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private boolean isQuestFullyComplete(ExecutionQuest quest) {
        switch (quest.type) {
            case TYPE_COLLECT:
            case TYPE_SALVAGE:
                return collectHandler.handlePostQuest();
            case TYPE_SELL_ORE:
                return sellOreHandler.handlePostQuest();
            default:
                return true;
        }
    }

    // =========================================================================
    // Quest Lifecycle
    // =========================================================================

    /** Called when the quest is no longer found in the UI list. */
    private void handleQuestGone(ExecutionQuest quest) {
        module.questManager.updateAcceptedQuestDataProgress(quest);
        quest.remaining = 0;
    }

    /** Called when the current quest finishes (remaining <= 0). */
    private void onQuestComplete(ExecutionQuest quest) {
        if (TYPE_KILL_NPC.equals(quest.type)) {
            killNpcHandler.cleanup(quest.targetNpc);
        } else if (TYPE_KILL_PLAYERS.equals(quest.type)) {
            killPlayersHandler.cleanup();
        }
        collectHandler.reset();
        module.questManager.setupNextQuest();
    }

    // =========================================================================
    // Map Travel
    // =========================================================================

    private boolean isOnTargetMap(String targetMap) {
        return module.travelHandler.getCurrentMap().equals(targetMap);
    }

    private void travelToMap(String targetMap) {
        module.travelHandler.setWorkingMap(targetMap);
        module.travelHandler.moveToMap(targetMap);
    }
}