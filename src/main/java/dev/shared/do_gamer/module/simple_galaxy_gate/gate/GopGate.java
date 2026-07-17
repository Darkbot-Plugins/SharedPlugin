package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.StaticEntity.PlutusGenerator;
import eu.darkbot.api.managers.GauntletPlutusAPI;

public final class GopGate extends GateHandler {
    private static final String SEEKER_ROCKET_NAME = "-=[ Seeker Rocket ]=-";
    private static final String WARHEAD_NAME = "-=[ Warhead ]=-";
    private static final String PLUTUS_NAME = "=^(Plutus)^=";
    private static final String TURRET_NAME = "-=[ Turret ]=-";

    private GauntletPlutusAPI gopApi;

    public GopGate() {
        this.npcMap.put(SEEKER_ROCKET_NAME, new NpcParam(600.0, -80));
        this.npcMap.put(WARHEAD_NAME, new NpcParam(600.0, -80));
        this.npcMap.put(PLUTUS_NAME, new NpcParam(600.0, -20));
        this.defaultNpcParam = new NpcParam(580.0);
        this.moveToCenter = false;
        this.showCompletedGates = false;
    }

    @Override
    protected void onModuleSet(PluginAPI api) {
        this.gopApi = api.requireAPI(GauntletPlutusAPI.class);
    }

    @Override
    public boolean prepareTickModule() {
        if (this.gopApi == null) {
            return false;
        }

        GauntletPlutusAPI.Status status = this.gopApi.getStatus();
        if (status != GauntletPlutusAPI.Status.AVAILABLE) {
            if (this.module.moveToRefinery()) {
                StateStore.request(StateStore.State.MOVE_TO_SAFE_POSITION);
            } else {
                this.statusDetails = status == GauntletPlutusAPI.Status.COMPLETED
                        ? "gate is completed."
                        : "gate not available.";
                StateStore.request(StateStore.State.WAITING);
            }
            return true;
        }
        this.reset();
        return false;
    }

    @Override
    public void reset() {
        this.statusDetails = null;
    }

    private boolean isPlutus(Npc npc) {
        return this.nameContains(npc, PLUTUS_NAME);
    }

    private boolean isPlutusPresent() {
        return this.module.lootModule.getNpcs().stream().anyMatch(this::isPlutus);
    }

    private boolean isRocket(Npc npc) {
        return this.nameContains(npc, SEEKER_ROCKET_NAME) || this.nameContains(npc, WARHEAD_NAME);
    }

    /**
     * Gets the nearest Rocket NPC to the hero.
     */
    private Npc getRocketNpc() {
        return this.module.lootModule.getNpcs().stream()
                .filter(this::isRocket)
                .min(Comparator.comparingDouble(npc -> npc.distanceTo(this.module.hero)))
                .orElse(null);
    }

    private boolean isTurret(Npc npc) {
        return this.nameContains(npc, TURRET_NAME);
    }

    /**
     * Gets the nearest Turret NPC to the hero.
     */
    private Npc getTurretNpc() {
        return this.module.lootModule.getNpcs().stream()
                .filter(this::isTurret)
                .min(Comparator.comparingDouble(npc -> npc.distanceTo(this.module.hero)))
                .orElse(null);
    }

    /**
     * Handles attacking the nearest rocket or turret NPC if one is present.
     */
    private boolean handleRocketOrTurretAttack() {
        // Attack the rocket first
        Npc npc = this.getRocketNpc();
        if (npc == null) {
            npc = this.getTurretNpc();
        }
        if (npc != null) {
            this.module.lootModule.moveToTarget(npc);
            this.module.lootModule.getAttacker().tryLockAndAttack();
            return true;
        }
        return false;
    }

    @Override
    public boolean attackTickModule() {
        if (!this.isPlutusPresent()) {
            return false;
        }

        return this.moveToHealGenerator() || this.handleRocketOrTurretAttack();
    }

    /**
     * Moves the hero to the nearest heal generator if one is present.
     */
    private boolean moveToHealGenerator() {
        PlutusGenerator healGenerator = this.module.entities.getStaticEntities().stream()
                .filter(PlutusGenerator.class::isInstance)
                .map(PlutusGenerator.class::cast)
                .filter(PlutusGenerator::isHealType)
                .min(Comparator.comparingDouble(g -> g.distanceTo(this.module.hero)))
                .orElse(null);

        if (healGenerator != null) {
            this.module.movement.moveTo(healGenerator);
            return true;
        }
        return false;
    }
}
