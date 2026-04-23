package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.GateNpcFlag;

public final class AbgGate extends GateHandler {

    public AbgGate() {
        this.npcMap.put("-=[ Devolarium ]=-", new NpcParam(560.0));
        this.npcMap.put("-=[ Kristallin ]=-", new NpcParam(605.0, GateNpcFlag.KAMIKAZE));
        this.npcMap.put("-=[ Kristallon ]=-", new NpcParam(610.0));
        this.npcMap.put("-=[ Lordakia ]=-", new NpcParam(500.0));
        this.npcMap.put("-=[ Mordon ]=-", new NpcParam(550.0));
        this.npcMap.put("-=[ Protegit ]=-", new NpcParam(630.0));
        this.npcMap.put("-=[ Saimon ]=-", new NpcParam(580.0, GateNpcFlag.KAMIKAZE));
        this.npcMap.put("-=[ Sibelon ]=-", new NpcParam(570.0));
        this.npcMap.put("-=[ Sibelonit ]=-", new NpcParam(605.0, GateNpcFlag.KAMIKAZE));
        this.npcMap.put("-=[ Streuner ]=-", new NpcParam(450.0));
    }
}
