package dev.shared.do_gamer.module.simple_galaxy_gate.gate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.shared.do_gamer.module.simple_galaxy_gate.SimpleGalaxyGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Defaults;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.game.other.Lockable;

public class GateHandler {
    protected SimpleGalaxyGate module;
    protected final Map<String, Double> npcRadiusMap = new HashMap<>();

    // Enum to represent the decision on whether to kill an NPC
    public enum KillDecision {
        YES, NO, DEFAULT
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
    public double getMapCenterX() {
        return Defaults.MAP_CENTER_X;
    }

    /**
     * Gets the Y coordinate of the map center point.
     */
    public double getMapCenterY() {
        return Defaults.MAP_CENTER_Y;
    }

    /**
     * Gets the tolerance distance from the center point to safely kill NPCs.
     */
    public double getToleranceDistance() {
        return Defaults.TOLERANCE_DISTANCE;
    }

    /**
     * Gets shift on X coordinate for the kamikaze strategy.
     */
    public double getKamikazeShiftX() {
        return Defaults.KAMIKAZE_SHIFT_X;
    }

    /**
     * Gets shift on Y coordinate for the kamikaze strategy.
     */
    public double getKamikazeShiftY() {
        return Defaults.KAMIKAZE_SHIFT_Y;
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
        for (Map.Entry<String, Double> entry : this.npcRadiusMap.entrySet()) {
            if (npcName.contains(entry.getKey())) {
                double radius = entry.getValue();
                npcInfo.setShouldKill(true);
                npcInfo.setRadius(radius); // populate radius
                return radius;
            }
        }

        return 0.0;
    }

    /**
     * Specific radius to use for repair
     */
    public double getRepairRadius() {
        return Defaults.REPAIR_RADIUS;
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
        return true;
    }

    /**
     * Return true to activate approach-to-center logic
     */
    public boolean isApproachToCenter() {
        return true;
    }

    /**
     * Return true to skip far targets when have closer ones
     */
    public boolean isSkipFarTargets() {
        return true;
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
     * Return the gate ID to travel to, or null to use default logic
     */
    public GameMap getMapForTravel() {
        return null;
    }

    /**
     * Return true to fetch server offset on background tick
     */
    public boolean fetchServerOffset() {
        return false;
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
     * Finds a portal by its type ID.
     */
    protected final Portal getPortalByTypeId(int portalTypeId) {
        return this.module.entities.getPortals().stream()
                .filter(p -> p.getTypeId() == portalTypeId)
                .findFirst()
                .orElse(null);
    }
}
