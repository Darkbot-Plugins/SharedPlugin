package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;

public final class EternalBlacklightGate extends GateHandler {
    private boolean autoStart = false;

    public EternalBlacklightGate() {
        this.npcMap.put("-=[ Barrage Seeker Rocket ]=-", new NpcParam(600.0, -90));
        this.npcMap.put("\\\\ Strokelight Barrage //", new NpcParam(600.0));
        this.npcMap.put("\\\\ Steadfast III //", new NpcParam(400.0, NpcFlag.PASSIVE));
        this.npcMap.put("\\\\ Abide I //", new NpcParam(400.0, NpcFlag.PASSIVE));
        this.npcMap.put("( UberKristallon )", new NpcParam(645.0));
        this.npcMap.put("( UberKristallin )", new NpcParam(580.0));
        this.npcMap.put("( UberSibelon )", new NpcParam(600.0));
        this.npcMap.put("( UberSibelonit )", new NpcParam(575.0));
        this.npcMap.put("( UberLordakium )", new NpcParam(600.0));
        this.npcMap.put("( Uber Annihilator )", new NpcParam(580.0));
        this.npcMap.put("( Uber Saboteur )", new NpcParam(590.0));
        this.npcMap.put("..::{ Boss Kristallon }::..", new NpcParam(615.0));
        this.npcMap.put("..::{ Boss Kristallin }::..", new NpcParam(575.0));
        this.npcMap.put("..::{ Boss Sibelon }::..", new NpcParam(570.0));
        this.npcMap.put("<=< Ice Meteoroid >=>", new NpcParam(615.0));
        this.npcMap.put("<=< Icy >=>", new NpcParam(600.0));
        this.npcMap.put("<=< Kucurbium >=>", new NpcParam(600.0));
        this.npcMap.put("\\\\ Attend IX //", new NpcParam(600.0));
        this.npcMap.put("\\\\ Impulse II //", new NpcParam(600.0));
        this.defaultNpcParam = new NpcParam(560.0);
        this.showCompletedGates = false;
    }

    @Override
    public boolean prepareTickModule() {
        Integer gateId = this.module.getConfig().gateId;
        if (Maps.isGateAccessibleFromCurrentMap(gateId, this.module.starSystem)) {
            if (!this.module.isGateAvailable(gateId) && this.hasCpu()) {
                this.useCpu();
            }
        } else {
            if (!this.hasCpu()) {
                if (this.module.moveToRefinery()) {
                    StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
                } else {
                    this.statusDetails = "no CPU available.";
                    StateStore.request(StateStore.State.WAITING);
                }
                return true; // Wait until we have a CPU before proceeding
            }
        }
        return false;
    }

    /**
     * Attempts to use the Eternal Blacklight CPU if available.
     */
    private void useCpu() {
        this.module.items.useItem(SelectableItem.Cpu.ETERNAL_BLACKLIGHT_CPU, 250,
                ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED);
    }

    /**
     * Checks if the player has any Eternal Blacklight CPUs available.
     */
    private boolean hasCpu() {
        return this.module.ebgApi.getCpuCount() > 0;
    }

    @Override
    public boolean attackTickModule() {
        this.showGateWave();
        return false;
    }

    @Override
    public boolean collectTickModule() {
        if (this.isSuicideWaveReached() && this.module.entities.getPortals().isEmpty()) {
            this.pauseForSuicideWave();
            return true;
        }
        if (StateStore.current() == StateStore.State.COLLECTING) {
            this.showGateWave();
        } else {
            this.reset();
        }
        return false;
    }

    @Override
    public void stoppedTickModule() {
        if (!this.autoStart) {
            return; // Only handle auto-start scenario
        }
        if (this.isSuicideWaveReached()
                && (this.module.hero.getHealth().getHp() == 0 || !this.module.entities.getPortals().isEmpty())) {
            this.module.bot.setRunning(true);
            this.autoStart = false;
            return;
        }

        this.module.petGearHelper.setPassive();
        this.showSuicideOnWave();
    }

    /**
     * Updates the status details to show the current wave.
     */
    private void showGateWave() {
        this.statusDetails = "Wave: " + this.module.ebgApi.getCurrentWave();
        int suicideWave = this.module.getConfig().eternalBlacklight.suicideOnWave;
        if (suicideWave > 0) {
            this.statusDetails += " (suicide on " + suicideWave + ")";
        }
    }

    private boolean isSuicideWaveReached() {
        int suicideWave = this.module.getConfig().eternalBlacklight.suicideOnWave;
        return suicideWave > 0 && this.module.ebgApi.getCurrentWave() >= suicideWave;
    }

    private void pauseForSuicideWave() {
        this.module.petGearHelper.setPassive();
        this.module.bot.setRunning(false);
        this.showSuicideOnWave();
        this.autoStart = true;
    }

    private void showSuicideOnWave() {
        this.statusDetails = "suicide wave";
    }

    @Override
    public void reset() {
        if (!this.autoStart) {
            this.statusDetails = null;
        }
    }
}
