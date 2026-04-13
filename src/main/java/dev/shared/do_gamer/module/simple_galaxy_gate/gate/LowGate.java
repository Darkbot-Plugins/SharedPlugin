package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.manolo8.darkbot.config.types.suppliers.BrowserApi;

import dev.shared.do_gamer.module.simple_galaxy_gate.StateStore;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Relay;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.utils.Version;

public class LowGate extends GateHandler {

    private static final double FAR_TARGET_DISTANCE = 1_200.0;

    // List of Relay IDs in specific spawn order
    private static final List<Integer> RELAY_IDS = List.of(
            100000104,
            100000102,
            100000103,
            100000101);

    // Enum to represent the boss state
    private enum BossState {
        NONE, ARRIVED, DESTROYED
    }

    // Tracks the boss state
    private BossState bossState = BossState.NONE;

    public LowGate() {
        this.npcMap.put("-=[ Century Falcon ]=-", new NpcParam(600.0, 50));
        this.npcMap.put("-=[ Vagrant ]=-", new NpcParam(540.0, 100, NpcFlag.AGGRESSIVE_FOLLOW));

        this.defaultNpcParam = new NpcParam(540.0, NpcFlag.AGGRESSIVE_FOLLOW);
        this.jumpToNextMap = false;
        this.moveToCenter = false;
        this.approachToCenter = false;
        this.skipFarTargets = false;
        this.showBoxCount = false;
        this.noPortalsInGate = true;
        this.farTargetDistance = FAR_TARGET_DISTANCE;
        // Probably will never use Kamikaze in this gate,
        // but set offset to 0 just in case
        this.kamikazeOffsetX = 0.0;
        this.kamikazeOffsetY = 0.0;
    }

    @Override
    public boolean collectTickModule() {
        this.processAttackTick();
        return true;
    }

    @Override
    public boolean attackTickModule() {
        return this.processAttackTick();
    }

    @Override
    public boolean prepareTickModule() {
        this.reset();
        return false;
    }

    /**
     * Processes the attack tick logic.
     */
    private boolean processAttackTick() {
        List<Relay> relays = this.getRelays();
        int npcsCount = this.module.lootModule.getNpcs().size();
        int relaysCount = relays.size();

        this.updateBossStatus(relaysCount);

        if (npcsCount == 0 && relaysCount > 0 && this.bossState == BossState.NONE) {
            this.handleRelayAttack(relays.get(0));
            return true;
        }

        switch (this.bossState) {
            case ARRIVED:
                this.statusDetails = "Boss has arrived!";
                break;
            case DESTROYED:
                this.statusDetails = "Boss destroyed!";
                break;
            default:
                this.statusDetails = null;
                break;
        }
        return false;
    }

    /**
     * Updates the boss arrival status based on relay count and NPC presence.
     */
    private void updateBossStatus(int relaysCount) {
        // Reset boss state if less than 4 relays are present
        if (relaysCount < 4) {
            this.bossState = BossState.NONE;
            return;
        }

        // Check for boss arrival
        if (this.bossState == BossState.NONE) {
            if (this.isBossPresent()) {
                this.bossState = BossState.ARRIVED;
            }
            return;
        }

        // Check if boss has been destroyed
        if (this.bossState == BossState.ARRIVED && !this.isBossPresent()) {
            this.bossState = BossState.DESTROYED;
        }
    }

    /**
     * Checks if the boss NPC is present.
     */
    private boolean isBossPresent() {
        return this.module.lootModule.getNpcs().stream().anyMatch(this::isCenturyFalcon);
    }

    /**
     * Checks if the given NPC is the Century Falcon boss.
     */
    private boolean isCenturyFalcon(Npc npc) {
        return this.nameEquals(npc, "-=[ Century Falcon ]=-");
    }

    /**
     * Handles the attack on relays.
     */
    private void handleRelayAttack(Relay targetRelay) {
        this.statusDetails = String.format("Attacking Relay %d", this.getNumber(targetRelay));
        StateStore.request(StateStore.State.ATTACKING);

        // Move closer to Relay
        this.module.lootModule.moveToTarget(targetRelay);

        // Relay attack not supported for Tanos API in bot versions older than 1.131.8
        if (this.module.botBrowserApi.getValue().equals(BrowserApi.TANOS_API)
                && this.module.bot.getVersion().isOlderThan(Version.of(1, 131, 8))) {
            return; // Prevent error: Invalid flash method signature! 23(set target)(2626)1016221500
        }

        // Attack Relay
        this.module.lootModule.getAttacker().tryLockAndAttack();
    }

    /**
     * Retrieves a list of Relays that haven't been attacked yet.
     */
    private List<Relay> getRelays() {
        return this.module.entities.getNpcs().stream()
                .filter(Objects::nonNull)
                .filter(n -> this.nameEquals(n, null) && RELAY_IDS.contains(n.getId()))
                .map(Relay.class::cast)
                .sorted(Comparator.comparingInt(r -> RELAY_IDS.indexOf(r.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<Npc> getFilteredNpcs(List<Npc> npcs) {
        return npcs.stream()
                .filter(n -> !this.nameEquals(n, null)) // Exclude NPCs without a name (e.g., Relays)
                .collect(Collectors.toList());
    }

    /**
     * Gets the Relay number based on its ID.
     */
    private int getNumber(Relay relay) {
        return relay.getId() - 100000100;
    }

    @Override
    public double getTargetRadius(Lockable target) {
        // Static radius for Relays
        if (target instanceof Relay) {
            return 400.0;
        }
        return super.getTargetRadius(target);
    }

    @Override
    public KillDecision shouldKillNpc(Npc npc) {
        // Skip Relays if NPCs present
        if (npc instanceof Relay && !this.module.lootModule.getNpcs().isEmpty()) {
            return KillDecision.NO;
        }

        // In other cases, kill all NPCs
        return KillDecision.YES;
    }

    @Override
    public void reset() {
        this.bossState = BossState.NONE;
        this.statusDetails = null;
    }

}
