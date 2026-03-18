package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.List;

import eu.darkbot.api.game.entities.Npc;

public class KuiperGate extends GateHandler {

    public KuiperGate() {
        this.npcMap.put("-=[ Streuner Specialist ]=-", new NpcParam(610.0));
        this.npcMap.put("-=[ Streuner Rocketeer ]=-", new NpcParam(600.0));
        this.npcMap.put("-=[ Streuner Soldier ]=-", new NpcParam(640.0));
        this.npcMap.put("-=[ Streuner Emperor ]=-", new NpcParam(600.0));
        this.npcMap.put("-=[ Streuner Turret ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Seeker Rocket ]=-", new NpcParam(600.0));
        this.npcMap.put("-=[ StreuneR ]=-", new NpcParam(550.0));
        this.npcMap.put("-=[ Saimon ]=-", new NpcParam(550.0));
        this.npcMap.put("..::{ Boss Sibelon }::..", new NpcParam(580.0));
        this.npcMap.put("..::{ Boss Saimon }::..", new NpcParam(590.0));
        this.npcMap.put("( UberStreuneR )", new NpcParam(590.0));
        this.npcMap.put("( UberSibelon )", new NpcParam(600.0));
    }

    private boolean isSpecialist(Npc npc) {
        return this.nameContains(npc, "-=[ Streuner Specialist ]=-");
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        List<Npc> npcs = this.module.lootModule.getNpcs();
        // Kill first the Streuner Specialist if present
        if (!this.isSpecialist(npc)
                && npcs.stream().anyMatch(n -> this.isSpecialist(n) && n.distanceTo(npc) < 2_000.0)) {
            return KillDecision.NO;
        }
        return super.shouldKillNpc(npc);
    }
}