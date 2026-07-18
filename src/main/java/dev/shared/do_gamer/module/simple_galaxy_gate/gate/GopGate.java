package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.StaticEntity.PlutusGenerator;
import eu.darkbot.api.game.other.Lockable;
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
        this.npcMap.put(PLUTUS_NAME, new NpcParam(600.0));
        this.defaultNpcParam = new NpcParam(580.0);
        this.showCompletedGates = false;
        this.approachToCenter = false;
        this.skipFarTargets = false;
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

    /**
     * Checks if the given NPC is a Plutus.
     */
    private boolean isPlutus(Npc npc) {
        return this.nameContains(npc, PLUTUS_NAME);
    }

    /**
     * Checks if there are Plutus present.
     */
    private boolean isPlutusPresent() {
        return this.module.lootModule.getNpcs().stream().anyMatch(this::isPlutus);
    }

    /**
     * Checks if the given NPC is a Rocket.
     */
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

    /**
     * Checks if the given NPC is a Turret.
     */
    private boolean isTurret(Npc npc) {
        return this.nameContains(npc, TURRET_NAME);
    }

    /**
     * Checks if there are any Turret present.
     */
    private boolean isTurretPresent() {
        return this.module.lootModule.getNpcs().stream().anyMatch(this::isTurret);
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
     * Checks if there are any other NPCs present.
     */
    private boolean hasOtherNpc(int priority) {
        return this.module.lootModule.getNpcs().stream()
                .anyMatch(n -> !this.isTurret(n) && !this.isPlutus(n) // Ignore Turrets and Plutus
                        && !n.getInfo().hasExtraFlag(NpcFlag.PASSIVE) // Ignore passive NPCs
                        && n.getInfo().getPriority() <= priority // Ignore NPCs with higest priority
                );
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Never attack the Plutus if a turret is present
        if (this.isPlutus(npc) && this.isTurretPresent()) {
            return KillDecision.NO;
        }
        return KillDecision.YES;
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

    /**
     * Handles attacking the nearest rocket or turret NPC if one is present.
     */
    private boolean handleRocketOrTurretAttack() {
        Npc npc = this.getTurretNpc();
        if (npc != null) {
            Npc rocketNpc = this.getRocketNpc();
            if (rocketNpc != null) {
                npc = rocketNpc; // Prioritize attacking rockets over turrets
            } else if (this.hasOtherNpc(npc.getInfo().getPriority())) {
                return false; // If there are other NPCs, don't attack the turret
            }
            this.module.lootModule.moveToTarget(npc);
            this.module.lootModule.getAttacker().tryLockAndAttack();
            return true;
        }
        return false;
    }

    @Override
    public double getTargetRadius(Lockable target) {
        if (this.isPlutusPresent()) {
            Npc npc = (target instanceof Npc) ? (Npc) target : null;
            if (npc != null && !this.isPlutus(npc) && !this.isRocket(npc) && !this.isTurret(npc)) {
                // Reduce radius for other NPCs when Plutus is present
                return npc.getInfo().getRadius() * 0.65;
            }
        }
        return super.getTargetRadius(target);
    }
}
