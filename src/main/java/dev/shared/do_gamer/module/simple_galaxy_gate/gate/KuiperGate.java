package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.List;

import eu.darkbot.api.game.entities.Npc;

public class KuiperGate extends GateHandler {

    public KuiperGate() {
        this.npcRadiusMap.put("-=[ Streuner Specialist ]=-", 610.0);
        this.npcRadiusMap.put("-=[ Streuner Rocketeer ]=-", 600.0);
        this.npcRadiusMap.put("-=[ Streuner Soldier ]=-", 640.0);
        this.npcRadiusMap.put("-=[ Streuner Emperor ]=-", 600.0);
        this.npcRadiusMap.put("-=[ Streuner Turret ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Seeker Rocket ]=-", 600.0);
        this.npcRadiusMap.put("-=[ StreuneR ]=-", 550.0);
        this.npcRadiusMap.put("-=[ Saimon ]=-", 550.0);
        this.npcRadiusMap.put("..::{ Boss Sibelon }::..", 580.0);
        this.npcRadiusMap.put("..::{ Boss Saimon }::..", 590.0);
        this.npcRadiusMap.put("( UberStreuneR )", 590.0);
        this.npcRadiusMap.put("( UberSibelon )", 600.0);
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