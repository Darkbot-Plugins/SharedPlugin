package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.Lockable;

public class AnyGate extends GateHandler {
    public AnyGate() {
        this.approachToCenter = false;
        this.skipFarTargets = false;
    }

    @Override
    public double getTargetRadius(Lockable target) {
        return this.module.getConfig().anyGate.npcRadius;
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        return this.module.getConfig().anyGate.killAllNpcs ? KillDecision.YES : KillDecision.DEFAULT;
    }

    @Override
    public boolean isJumpToNextMap() {
        return this.module.getConfig().anyGate.jumpToNextMap;
    }

    @Override
    public boolean isMoveToCenter() {
        return this.module.getConfig().anyGate.moveToCenter;
    }
}
