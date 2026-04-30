package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.github.manolo8.darkbot.core.entities.Npc;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig.EternalBlacklightSettings.BoostersTable.Piority;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.EternalBlacklightGateAPI;
import eu.darkbot.api.managers.HeroItemsAPI;

public final class EternalBlacklightGate extends GateHandler {
    private static final String GUI = "eternal_blacklight";
    private boolean autoStart = false;
    private EternalBlacklightGateAPI ebgApi;
    private HeroItemsAPI items;

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
    protected void onModuleSet(PluginAPI api) {
        this.ebgApi = api.requireAPI(EternalBlacklightGateAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
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
        this.items.useItem(SelectableItem.Cpu.ETERNAL_BLACKLIGHT_CPU, 250,
                ItemFlag.USABLE, ItemFlag.READY, ItemFlag.AVAILABLE, ItemFlag.NOT_SELECTED);
    }

    /**
     * Checks if the player has any Eternal Blacklight CPUs available.
     */
    private boolean hasCpu() {
        return this.ebgApi.getCpuCount() > 0;
    }

    @Override
    public boolean attackTickModule() {
        if (this.isSuicideWaveReached() && this.pauseForSuicideWave()) {
            return true;
        }
        this.showGateWave();
        return false;
    }

    @Override
    public boolean collectTickModule() {
        if (StateStore.current() == StateStore.State.COLLECTING) {
            this.showGateWave();
        } else {
            this.reset();
        }
        this.selectBestBooster();
        return false;
    }

    /**
     * Selects the best available booster based on configured priorities.
     * Options are sorted by percentage descending; the one whose category has
     * the lowest configured priority value is preferred.
     */
    private void selectBestBooster() {
        if (this.ebgApi.getBoosterPoints() <= 0) {
            this.getVisibleGui(GUI).ifPresent(gui -> gui.setVisible(false));
            return;
        }
        Map<String, Piority> boosters = this.module.getConfig().eternalBlacklight.boosters.table;
        List<? extends EternalBlacklightGateAPI.Booster> options = this.ebgApi.getBoosterOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        EternalBlacklightGateAPI.Booster best = options.stream()
                .sorted(Comparator.comparingInt(EternalBlacklightGateAPI.Booster::getPercentage).reversed())
                .min(Comparator.comparingInt(b -> {
                    Piority p = boosters.get(b.getCategoryType().name());
                    return p != null ? p.priority : 0;
                }))
                .orElse(null);
        if (best != null) {
            this.ebgApi.selectBooster(best);
        }
    }

    @Override
    public void stoppedTickModule() {
        if (!this.autoStart) {
            return; // Only handle auto-start scenario
        }
        if (this.module.hero.getHealth().getHp() > 0) {
            this.statusDetails = "suicide wave";
            return;
        }
        this.module.bot.setRunning(true);
        this.autoStart = false;
    }

    /**
     * Updates the status details to show the current wave.
     */
    private void showGateWave() {
        this.statusDetails = "Wave: " + this.ebgApi.getCurrentWave();
        int suicideWave = this.module.getConfig().eternalBlacklight.suicideOnWave;
        if (suicideWave > 0) {
            this.statusDetails += " (suicide on " + suicideWave + ")";
        }
    }

    /**
     * Checks whether the configured suicide wave has been reached or exceeded.
     */
    private boolean isSuicideWaveReached() {
        int suicideWave = this.module.getConfig().eternalBlacklight.suicideOnWave;
        return suicideWave > 0 && this.ebgApi.getCurrentWave() >= suicideWave;
    }

    /**
     * Pauses the bot before the suicide to prevent other plugins activity.
     */
    private boolean pauseForSuicideWave() {
        Npc target = this.module.lootModule.getAttacker().getTargetAs(Npc.class);
        if (target != null) {
            this.module.petGearHelper.setPassive();
            if (this.module.lootModule.getAttacker().isAttacking()) {
                this.module.lootModule.getAttacker().stopAttack();
            }
            this.module.lootModule.moveToTarget(target);
            if (target.distanceTo(this.module.hero) < 1_000.0) {
                this.module.bot.setRunning(false);
                this.autoStart = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
        if (!this.autoStart) {
            this.statusDetails = null;
        }
    }
}
