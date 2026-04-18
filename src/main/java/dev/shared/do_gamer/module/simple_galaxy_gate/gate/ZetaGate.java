package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import com.github.manolo8.darkbot.core.entities.Npc;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.config.types.NpcFlag;

public final class ZetaGate extends GateHandler {

    public ZetaGate() {
        this.npcMap.put("( UberKristallin )", new NpcParam(590.0));
        this.npcMap.put("( UberLordakia )", new NpcParam(550.0));
        this.npcMap.put("( UberSaimon )", new NpcParam(580.0));
        this.npcMap.put("( UberSibelonit )", new NpcParam(590.0));
        this.npcMap.put("( UberStreuneR )", new NpcParam(590.0));
        this.npcMap.put("( UberStreuner )", new NpcParam(560.0));
        this.npcMap.put("-=[ Devourer ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Infernal ]=-", new NpcParam(550.0));
        this.npcMap.put("-=[ Kristallin ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Lordakia ]=-", new NpcParam(550.0));
        this.npcMap.put("-=[ Melter ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Saimon ]=-", new NpcParam(550.0));
        this.npcMap.put("-=[ Scorcher ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Sibelonit ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ StreuneR ]=-", new NpcParam(590.0));
        this.npcMap.put("-=[ Streuner ]=-", new NpcParam(550.0));
        this.npcMap.put("..::{ Boss Kristallin }::..", new NpcParam(590.0));
        this.npcMap.put("..::{ Boss Lordakia }::..", new NpcParam(550.0));
        this.npcMap.put("..::{ Boss Saimon }::..", new NpcParam(550.0));
        this.npcMap.put("..::{ Boss Sibelonit }::..", new NpcParam(590.0));
        this.npcMap.put("..::{ Boss Streuner }::..", new NpcParam(550.0));
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
