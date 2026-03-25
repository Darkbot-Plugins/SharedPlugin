package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.github.weisj.jsvg.nodes.prototype.spec.NotImplemented;

import dev.shared.do_gamer.module.simple_galaxy_gate.SimpleGalaxyGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Defaults;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Lockable;

public class GateHandler {
    protected SimpleGalaxyGate module;
    protected final Map<String, NpcParam> npcMap = new HashMap<>();
    protected NpcParam defaultNpcParam = null;
    protected double mapCenterX = Defaults.MAP_CENTER_X;
    protected double mapCenterY = Defaults.MAP_CENTER_Y;
    protected double toleranceDistance = Defaults.TOLERANCE_DISTANCE;
    protected double kamikazeOffsetX = Defaults.KAMIKAZE_OFFSET_X;
    protected double kamikazeOffsetY = Defaults.KAMIKAZE_OFFSET_Y;
    protected double repairRadius = Defaults.REPAIR_RADIUS;
    protected double farTargetDistance = Defaults.FAR_TARGET_DISTANCE;
    protected boolean jumpToNextMap = true;
    protected boolean moveToCenter = true;
    protected boolean approachToCenter = true;
    protected boolean skipFarTargets = true;
    protected boolean stickToTarget = false;
    protected boolean fetchServerOffset = false;
    protected boolean safeRefreshInGate = true;
    protected String statusDetails = null;
    protected boolean useGuardableNpcAsSearchLocation = false;
    private Npc cachedGuardableNpc = null;

    // Enum to represent the decision on whether to kill an NPC
    public enum KillDecision {
        YES, NO, DEFAULT
    }

    /**
     * Class to hold default NPC parameters.
     */
    protected static final class NpcParam {
        public final double radius;
        public final int priority;
        public final List<NpcFlag> flags;

        public NpcParam(double radius, int priority, NpcFlag... flags) {
            this.radius = radius;
            this.priority = priority;
            this.flags = List.of(flags);
        }

        public NpcParam(double radius) {
            this(radius, 0);
        }

        public NpcParam(double radius, NpcFlag... flags) {
            this(radius, 0, flags);
        }
    }

    public GateHandler() {
        // Default constructor
    }

    /**
     * Set the module instance for this gate handler
     */
    public final void setModule(SimpleGalaxyGate module) {
        this.module = module;
    }

    /**
     * Gets the X coordinate of the map center point.
     */
    public final double getMapCenterX() {
        return this.mapCenterX;
    }

    /**
     * Gets the Y coordinate of the map center point.
     */
    public final double getMapCenterY() {
        return this.mapCenterY;
    }

    /**
     * Gets the tolerance distance from the center point to safely kill NPCs.
     */
    public final double getToleranceDistance() {
        return this.toleranceDistance;
    }

    /**
     * Gets offset on X coordinate for the kamikaze strategy.
     */
    public final double getKamikazeOffsetX() {
        return this.kamikazeOffsetX;
    }

    /**
     * Gets offset on Y coordinate for the kamikaze strategy.
     */
    public final double getKamikazeOffsetY() {
        return this.kamikazeOffsetY;
    }

    /**
     * Gets the location reference for NPC searching.
     * By default, it returns the hero's position.
     */
    public final Locatable getNpcSearchLocation() {
        if (this.useGuardableNpcAsSearchLocation) {
            Npc guardableNpc = this.getGuardableNpc();
            if (guardableNpc != null) {
                return guardableNpc;
            }
        }
        return this.module.hero;
    }

    /**
     * Filters the given collection based on gate handler needs.
     * By default, it returns a list of the given NPCs without any filtering.
     * Override this method to implement custom filtering logic.
     */
    public List<Npc> getFilteredNpcs(List<Npc> npcs) {
        return npcs;
    }

    /**
     * Specific radius to use for the target
     * Return 0.0 to use default radius from NPC table
     */
    public double getTargetRadius(Lockable target) {
        NpcInfo npcInfo = ((Npc) target).getInfo();
        // If the NPC is already marked to be killed, return the stored radius
        if (npcInfo.getShouldKill()) {
            return npcInfo.getRadius();
        }

        // Check if the NPC name contains any of the specified substrings
        String npcName = target.getEntityInfo().getUsername();
        for (Map.Entry<String, NpcParam> entry : this.npcMap.entrySet()) {
            if (npcName.contains(entry.getKey())) {
                return this.populateNpcInfo(npcInfo, entry.getValue());
            }
        }

        // Populate default params
        if (this.defaultNpcParam != null) {
            return this.populateNpcInfo(npcInfo, this.defaultNpcParam);
        }

        return 0.0;
    }

    /**
     * Populates the given NpcInfo with values from the provided params.
     */
    private final double populateNpcInfo(NpcInfo npcInfo, NpcParam params) {
        npcInfo.setShouldKill(true);
        // populate radius
        npcInfo.setRadius(params.radius);
        // populate priority
        if (params.priority != 0) {
            npcInfo.setPriority(params.priority);
        }
        // populate flags
        if (!params.flags.isEmpty()) {
            for (NpcFlag flag : params.flags) {
                npcInfo.setExtraFlag(flag, true);
            }
        }
        return params.radius;
    }

    /**
     * Specific radius to use for repair
     */
    public final double getRepairRadius() {
        return this.repairRadius;
    }

    /**
     * Specific distance to consider a target as "far"
     */
    public final double getFarTargetDistance() {
        return this.farTargetDistance;
    }

    /**
     * Return:
     * YES - to kill the NPC,
     * NO - to skip it,
     * DEFAULT - to use default logic
     */
    public KillDecision shouldKillNpc(Npc npc) {
        return npc != null ? KillDecision.YES : KillDecision.NO;
    }

    /**
     * Return true to jump to next map
     */
    public boolean isJumpToNextMap() {
        return this.jumpToNextMap;
    }

    /**
     * Return true to move to center when have no boxes to collect
     */
    public boolean isMoveToCenter() {
        return this.moveToCenter;
    }

    /**
     * Return true to activate approach-to-center logic
     */
    public final boolean isApproachToCenter() {
        return this.approachToCenter;
    }

    /**
     * Return true to skip far targets when have closer ones
     */
    public final boolean isSkipFarTargets() {
        return this.skipFarTargets;
    }

    /**
     * Return true to stick to current target and ignore new ones (except priority)
     */
    public final boolean isStickToTarget() {
        return this.stickToTarget;
    }

    /**
     * Return the status details to use in module status
     */
    public final String getStatusDetails() {
        return this.statusDetails;
    }

    /**
     * Implement the attack tick logic and return true if have something to process
     */
    public boolean attackTickModule() {
        return false;
    }

    /**
     * Implement the collect tick logic and return true if have something to process
     */
    public boolean collectTickModule() {
        return false;
    }

    /**
     * Implement the prepare tick logic and return true if have something to process
     */
    public boolean prepareTickModule() {
        return false;
    }

    /**
     * Implement the stopped tick logic, called when the module is stopped
     */
    public void stoppedTickModule() {
        // Default implementation does nothing, override if needed
    }

    /**
     * Return the gate ID to travel to, or null to use default logic
     */
    public GameMap getMapForTravel() {
        return null;
    }

    /**
     * Helper method to get the faction-based map for travel
     * based on the hero's faction and specified map number.
     */
    protected final GameMap getFactionMapForTravel(int mapNumber) {
        if (!Maps.isGateAccessibleFromCurrentMap(this.module.getConfig().gateId, this.module.starSystem)) {
            int faction = this.getHeroFractionIdx();
            if (faction == -1) {
                return null; // Unknown faction, cannot determine map
            }
            String map = String.format("%d-%d", faction, mapNumber);
            return this.module.starSystem.getOrCreateMap(map);
        }
        return null; // Already on gate map, no need to travel
    }

    /**
     * Return true to fetch server offset on background tick
     */
    public boolean isFetchServerOffset() {
        return this.fetchServerOffset;
    }

    /**
     * Determines if it's safe to refresh the map while in the gate.
     */
    public final boolean canSafeRefreshInGate() {
        if (this.safeRefreshInGate) {
            return this.module.isMapGG()
                    && this.module.lootModule.getNpcs().isEmpty()
                    && this.module.collectorModule.hasNoBox()
                    && this.module.entities.getPortals().stream()
                            .anyMatch(p -> p.distanceTo(this.module.hero) < 1_000.0);
        }
        return false;
    }

    /**
     * Determines if the specified box should be ignored for collection.
     */
    public boolean shouldIgnoreBox(Box box) {
        return box == null; // Default implementation ingnores null boxes, override if needed
    }

    /**
     * Checks if the NPC name matches the specified substring.
     * Or is empty if `substring` is null.
     */
    protected final boolean nameEquals(Npc npc, String substring) {
        String name = npc.getEntityInfo().getUsername();
        return substring != null ? name.equals(substring) : name.isEmpty();
    }

    /**
     * Checks if the NPC name contains the specified substring.
     * The `substring` parameter is required (non-null)
     */
    protected final boolean nameContains(Npc npc, String substring) {
        Objects.requireNonNull(substring, "substring must not be null");
        String name = npc.getEntityInfo().getUsername();
        return name.contains(substring);
    }

    /**
     * Reset any internal state of the gate handler (if needed)
     */
    public void reset() {
        // Default implementation does nothing, override if needed
    }

    /**
     * Returns the visible GUI matching the given ID, if present.
     */
    protected final Optional<Gui> getVisibleGui(String guiId) {
        Gui gui = this.module.gameScreenApi.getGui(guiId);
        if (gui != null && gui.isVisible()) {
            return Optional.of(gui);
        }
        return Optional.empty();
    }

    /**
     * Closes the specified GUI if it is open.
     */
    protected final void closeGui(String guiId) {
        this.getVisibleGui(guiId).ifPresent(gui -> gui.setVisible(false));
    }

    /**
     * Gets the hero's faction index.
     */
    protected final int getHeroFractionIdx() {
        EntityInfo.Faction faction = this.module.hero.getEntityInfo().getFaction();
        switch (faction) {
            case MMO:
                return 1;
            case EIC:
                return 2;
            case VRU:
                return 3;
            default:
                return -1; // Unknown faction
        }
    }

    /**
     * Gets the first portal matching any of the specified type IDs.
     */
    private final Portal getPortalByType(List<Integer> portalTypeIds) {
        return this.module.entities.getPortals().stream()
                .filter(p -> portalTypeIds.contains(p.getTypeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Handles traveling to the gate portal if it's visible
     */
    protected final boolean handleTravelToGate(List<Integer> portalTypeIds) {
        // Check for portal and travel if found
        Portal portal = this.getPortalByType(portalTypeIds);
        if (portal != null) {
            this.module.jumper.travelAndJump(portal);
            return true;
        }
        return false; // Not traveling, allow default logic
    }

    /**
     * Overload of handleTravelToGate for a single portal type ID.
     */
    protected final boolean handleTravelToGate(int portalTypeId) {
        return this.handleTravelToGate(List.of(portalTypeId));
    }

    /**
     * Checks if the configured gate is accessible from the current map.
     */
    protected final boolean isGateAccessibleFromCurrentMap() {
        if (this.module.getConfig() == null) {
            return false;
        }
        return Maps.isGateAccessibleFromCurrentMap(this.module.getConfig().gateId, this.module.starSystem);
    }

    /**
     * Finds the guardable NPC if present
     */
    protected final Npc getGuardableNpc() {
        // Return cached guardable NPC if still valid
        if (this.cachedGuardableNpc != null && this.module.lootModule.getNpcs().contains(this.cachedGuardableNpc)) {
            return this.cachedGuardableNpc;
        }

        // Search for guardable NPC and cache it for future ticks
        this.resetCachedGuardableNpc();
        for (Npc npc : this.module.lootModule.getNpcs()) {
            if (this.npcHasGuardableName(npc)) {
                this.cachedGuardableNpc = npc;
                break;
            }
        }

        return this.cachedGuardableNpc;
    }

    /**
     * Resets the cached guardable NPC
     */
    protected final void resetCachedGuardableNpc() {
        this.cachedGuardableNpc = null;
    }

    /**
     * Determines if the specified NPC is a guardable NPC based on its name.
     */
    @NotImplemented("Override in specific gate handler to define guardable NPCs")
    protected boolean npcHasGuardableName(Npc npc) {
        return false;
    }

    /**
     * Checks if the given NPC is the cached guardable NPC
     */
    protected final boolean isGuardableNpc(Npc npc) {
        Npc guardableNpc = this.getGuardableNpc();
        return guardableNpc != null && Objects.equals(npc, guardableNpc);
    }
}
