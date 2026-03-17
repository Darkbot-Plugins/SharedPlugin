package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import com.github.manolo8.darkbot.core.entities.Npc;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.config.types.NpcFlag;

public class ZetaGate extends GateHandler {

    public ZetaGate() {
        this.npcRadiusMap.put("( UberKristallin )", 590.0);
        this.npcRadiusMap.put("( UberLordakia )", 550.0);
        this.npcRadiusMap.put("( UberSaimon )", 580.0);
        this.npcRadiusMap.put("( UberSibelonit )", 590.0);
        this.npcRadiusMap.put("( UberStreuneR )", 590.0);
        this.npcRadiusMap.put("( UberStreuner )", 560.0);
        this.npcRadiusMap.put("-=[ Devourer ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Infernal ]=-", 550.0);
        this.npcRadiusMap.put("-=[ Kristallin ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Lordakia ]=-", 550.0);
        this.npcRadiusMap.put("-=[ Melter ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Saimon ]=-", 550.0);
        this.npcRadiusMap.put("-=[ Scorcher ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Sibelonit ]=-", 590.0);
        this.npcRadiusMap.put("-=[ StreuneR ]=-", 590.0);
        this.npcRadiusMap.put("-=[ Streuner ]=-", 550.0);
        this.npcRadiusMap.put("..::{ Boss Kristallin }::..", 590.0);
        this.npcRadiusMap.put("..::{ Boss Lordakia }::..", 550.0);
        this.npcRadiusMap.put("..::{ Boss Saimon }::..", 550.0);
        this.npcRadiusMap.put("..::{ Boss Sibelonit }::..", 590.0);
        this.npcRadiusMap.put("..::{ Boss Streuner }::..", 550.0);
    }

    @Override
    public boolean attackTickModule() {
        Npc target = this.module.lootModule.getAttacker().getTargetAs(Npc.class);
        if (target != null
                && this.shouldCollectWithDevourer(target)
                && this.module.collectorModule.collectIfAvailable()) {
            StateStore.request(StateStore.State.COLLECTING);
            return true;
        }

        return false;
    }

    private boolean isDevourer(Npc npc) {
        return this.nameContains(npc, "-=[ Devourer ]=-");
    }

    /**
     * Determines if we should prioritize collecting loot with the Devourer.
     */
    private boolean shouldCollectWithDevourer(Npc npc) {
        return this.isDevourer(npc) && npc.getHealth().hpPercent() < 0.25
                && !npc.getInfo().hasExtraFlag(NpcFlag.IGNORE_BOXES);
    }
}
