package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.Defaults;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig.EternalBlacklightSettings.BoostersTable;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig.EternalBlacklightSettings.BrakeAction;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.GateHandler;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.StateStore;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.EternalBlacklightGateAPI;
import eu.darkbot.api.managers.HeroItemsAPI;

public final class EternalBlacklightGate extends GateHandler {
    private EternalBlacklightGateAPI ebgApi;
    private HeroItemsAPI items;

    private boolean autoStart = false;
    private boolean exitRequested = false;
    private static final String GUI = "eternal_blacklight";
    private static final int GATE_CYCLE_WAVES = 51;
    private static final int UBER_KRISTALLON_WAVE_IN_CYCLE = 47;
    private static final double UBER_KRISTALLON_SPLIT_DISTANCE = 300.0;
    private static final double UBER_KRISTALLON_CENTER_SHIFT_X = 4_000.0;
    private static final double UBER_KRISTALLON_CENTER_SHIFT_Y = 2_000.0;
    private static final double UBER_KRISTALLON_TOLERANCE_DISTANCE = 1_500.0;
    /**
     * Standard "to home map" portal type id. The next-wave portal uses a
     * different type id, so filtering by this id reliably picks the exit.
     */
    private static final int EXIT_PORTAL_TYPE_ID = 1;

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
        // We just landed back home after the configured exit wave — pause.
        if (this.exitRequested) {
            this.module.bot.setRunning(false);
            this.exitRequested = false; // reset so resuming the bot does not re-pause
            return true;
        }

        Integer gateId = this.module.getConfig().gateId;
        if (Maps.isGateAccessibleFromCurrentMap(gateId, this.module.starSystem)) {
            if (!this.module.isGateAvailable(gateId) && this.hasCpu()) {
                this.useCpu();
            }
        } else {
            if (!this.hasCpu() && this.ebgApi.getCurrentWave() == 0) {
                if (this.module.moveToRefinery()) {
                    StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
                } else {
                    this.statusDetails = "no CPU available.";
                    StateStore.request(StateStore.State.WAITING);
                }
                return true; // Wait until we have a CPU before proceeding
            }
        }
        this.statusDetails = this.getCpuStatus();
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
        if (this.pauseForSuicideWave()) {
            return true;
        }
        this.updateUberKristallonCenter();
        this.showGateInfo();
        return false;
    }

    /**
     * Once the configured brake wave is reached with the EXIT action and
     * there are no more boxes to collect, travel through the home portal.
     */
    private boolean tryExit() {
        if (!this.isBrakeWaveReached()
                || this.module.getConfig().eternalBlacklight.brakeAction != BrakeAction.EXIT
                || !this.module.collectorModule.hasNoBox()) {
            return false;
        }
        if (this.handleTravelToGate(EXIT_PORTAL_TYPE_ID)) {
            this.exitRequested = true;
            StateStore.request(StateStore.State.TRAVELING_TO_GATE);
            return true;
        }
        return false;
    }

    /**
     * Determines if the given NPC is Uber Kristallon.
     */
    private boolean npcHasUberKristallonName(Npc npc) {
        return this.nameContains(npc, "( UberKristallon )");
    }

    /**
     * Determines if the current wave is the Uber Kristallon appears in the cycle.
     */
    private boolean isUberKristallonWave() {
        return this.ebgApi.getCurrentWave() % GATE_CYCLE_WAVES == UBER_KRISTALLON_WAVE_IN_CYCLE;
    }

    /**
     * Updates the map center and tolerance distance for Uber Kristallon wave.
     */
    private void updateUberKristallonCenter() {
        if (this.module.getConfig().eternalBlacklight.trySplitUberKristallon && this.isUberKristallonWave()) {
            List<Npc> ubers = this.module.lootModule.getNpcs().stream()
                    .filter(this::npcHasUberKristallonName)
                    .collect(Collectors.toList());
            if (ubers.size() == 2) {
                double dist = ubers.get(0).distanceTo(ubers.get(1));
                if (dist < UBER_KRISTALLON_SPLIT_DISTANCE) {
                    // left top
                    this.mapCenterX = Defaults.MAP_CENTER_X - UBER_KRISTALLON_CENTER_SHIFT_X;
                    this.mapCenterY = Defaults.MAP_CENTER_Y - UBER_KRISTALLON_CENTER_SHIFT_Y;
                } else {
                    // right bottom
                    this.mapCenterX = Defaults.MAP_CENTER_X + UBER_KRISTALLON_CENTER_SHIFT_X;
                    this.mapCenterY = Defaults.MAP_CENTER_Y + UBER_KRISTALLON_CENTER_SHIFT_Y;
                }
                this.toleranceDistance = UBER_KRISTALLON_TOLERANCE_DISTANCE;
                return;
            }
        }

        // Reset to defaults
        this.mapCenterX = Defaults.MAP_CENTER_X;
        this.mapCenterY = Defaults.MAP_CENTER_Y;
        this.toleranceDistance = Defaults.TOLERANCE_DISTANCE;
    }

    @Override
    public boolean collectTickModule() {
        if (this.tryExit()) {
            return true;
        }
        this.showGateInfo();
        this.selectBestBooster();
        return false;
    }

    /**
     * Selects the best available booster based on configured priorities.
     * Options are sorted by percentage descending; the one whose category has
     * the lowest configured priority value is preferred.
     */
    private void selectBestBooster() {
        if (!this.module.getConfig().eternalBlacklight.boosters.autoSelect) {
            return; // Auto-select is disabled
        }
        if (this.ebgApi.getBoosterPoints() <= 0) {
            this.getVisibleGui(GUI).ifPresent(gui -> gui.setVisible(false));
            return;
        }
        Map<String, BoostersTable.BoosterPriority> boosters = this.module.getConfig().eternalBlacklight.boosters.table;
        List<? extends EternalBlacklightGateAPI.Booster> options = this.ebgApi.getBoosterOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        EternalBlacklightGateAPI.Booster best = options.stream()
                .min(Comparator.<EternalBlacklightGateAPI.Booster>comparingInt(b -> {
                    BoostersTable.BoosterPriority p = boosters.get(b.getCategoryType().name());
                    return p != null ? p.priority : 0;
                }).thenComparing(Comparator.comparingInt(EternalBlacklightGateAPI.Booster::getPercentage).reversed()))
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
     * Updates the status details with the CPUs in stock, the current wave
     * and the optional brake wave / action hint.
     */
    private void showGateInfo() {
        this.statusDetails = this.getCpuStatus() + " | Wave: " + this.ebgApi.getCurrentWave();
        int brakeWave = this.module.getConfig().eternalBlacklight.brakeOnWave;
        if (brakeWave > 0) {
            String action = this.module.getConfig().eternalBlacklight.brakeAction.label.toLowerCase();
            this.statusDetails += " (" + action + " on " + brakeWave + ")";
        }
    }

    /**
     * Status string for the current CPU count.
     */
    private String getCpuStatus() {
        return "CPU: " + this.ebgApi.getCpuCount();
    }

    /**
     * Checks whether the configured brake wave has been reached or exceeded.
     */
    private boolean isBrakeWaveReached() {
        int brakeWave = this.module.getConfig().eternalBlacklight.brakeOnWave;
        return brakeWave > 0 && this.ebgApi.getCurrentWave() >= brakeWave;
    }

    /**
     * Pauses the bot before the suicide to prevent other plugins activity.
     */
    private boolean pauseForSuicideWave() {
        if (!this.isBrakeWaveReached()
                || this.module.getConfig().eternalBlacklight.brakeAction != BrakeAction.SUICIDE) {
            return false;
        }
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
