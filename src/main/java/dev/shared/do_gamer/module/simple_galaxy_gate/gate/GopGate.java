package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;

import dev.shared.do_gamer.module.simple_galaxy_gate.utils.GateHandler;
import dev.shared.do_gamer.module.simple_galaxy_gate.utils.StateStore;
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

    // Per-tick cache for Plutus/Turret presence, to avoid scanning the NPC
    // list multiple times when shouldKillNpc/getTargetRadius are called
    // repeatedly (once per candidate NPC) within the same tick.
    private boolean presenceCacheDirty = true;
    private boolean plutusPresentCache;
    private boolean turretPresentCache;

    // Per-tick cache for the nearest Rocket NPC, to avoid repeating the
    // stream/filter/min lookup when getRocketNpc() is called more than once
    // within the same tick.
    private boolean rocketNpcCacheDirty = true;
    private Npc rocketNpcCache;

    public GopGate() {
        this.npcMap.put(SEEKER_ROCKET_NAME, new NpcParam(620.0, -80));
        this.npcMap.put(WARHEAD_NAME, new NpcParam(620.0, -80));
        this.npcMap.put(PLUTUS_NAME, new NpcParam(620.0));
        this.defaultNpcParam = new NpcParam(600.0);
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
        this.presenceCacheDirty = true;
        this.rocketNpcCacheDirty = true;
    }

    /**
     * Checks if the given NPC is a Plutus.
     */
    private boolean isPlutus(Npc npc) {
        return this.nameContains(npc, PLUTUS_NAME);
    }

    /**
     * Refreshes the cached Plutus/Turret presence flags with a single pass
     * over the NPC list, instead of streaming over it once per flag.
     */
    private void refreshPresenceCache() {
        boolean plutus = false;
        boolean turret = false;
        for (Npc npc : this.module.lootModule.getNpcs()) {
            if (!plutus && this.isPlutus(npc)) {
                plutus = true;
            }
            if (!turret && this.isTurret(npc)) {
                turret = true;
            }
            if (plutus && turret) {
                break;
            }
        }
        this.plutusPresentCache = plutus;
        this.turretPresentCache = turret;
        this.presenceCacheDirty = false;
    }

    /**
     * Checks if there are Plutus present.
     * The result is cached per tick, see {@link #refreshPresenceCache()}.
     */
    private boolean isPlutusPresent() {
        if (this.presenceCacheDirty) {
            this.refreshPresenceCache();
        }
        return this.plutusPresentCache;
    }

    /**
     * Checks if the given NPC is a Rocket.
     */
    private boolean isRocket(Npc npc) {
        return this.nameContains(npc, SEEKER_ROCKET_NAME) || this.nameContains(npc, WARHEAD_NAME);
    }

    /**
     * Gets the nearest Rocket NPC to the hero.
     * The result is cached per tick, invalidated at the start of each tick.
     */
    private Npc getRocketNpc() {
        if (this.rocketNpcCacheDirty) {
            this.rocketNpcCache = this.module.lootModule.getNpcs().stream()
                    .filter(this::isRocket)
                    .min(Comparator.comparingDouble(npc -> npc.distanceTo(this.module.hero)))
                    .orElse(null);
            this.rocketNpcCacheDirty = false;
        }
        return this.rocketNpcCache;
    }

    /**
     * Checks if the given NPC is a Turret.
     */
    private boolean isTurret(Npc npc) {
        return this.nameContains(npc, TURRET_NAME);
    }

    /**
     * Checks if there are any Turret present.
     * The result is cached per tick, see {@link #refreshPresenceCache()}.
     */
    private boolean isTurretPresent() {
        if (this.presenceCacheDirty) {
            this.refreshPresenceCache();
        }
        return this.turretPresentCache;
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
                .anyMatch(n -> n != null && n.isValid() && n.isSelectable()
                        && !this.isTurret(n) && !this.isPlutus(n) // Ignore Turrets and Plutus
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
        // Invalidate the per-tick caches at the start of each tick, since it's
        // called before shouldKillNpc/getTargetRadius are evaluated per NPC.
        this.presenceCacheDirty = true;
        this.rocketNpcCacheDirty = true;

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

            // Kill the nearest rocket if one is present while moving to the heal generator
            Npc rocketNpc = this.getRocketNpc();
            if (rocketNpc != null) {
                this.module.lootModule.getAttacker().setTarget(rocketNpc);
                this.module.lootModule.getAttacker().tryLockAndAttack();
            }
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
        if (this.isPlutusPresent() && this.isTurretPresent()) {
            Npc npc = (target instanceof Npc) ? (Npc) target : null;
            if (npc != null && !this.isPlutus(npc) && !this.isRocket(npc) && !this.isTurret(npc)) {
                // Reduce radius for other NPCs when Plutus is present
                return npc.getInfo().getRadius() * 0.75;
            }
        }
        return super.getTargetRadius(target);
    }
}
