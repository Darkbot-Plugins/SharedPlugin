package dev.shared.do_gamer.utils;

import java.util.Objects;

import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;

/**
 * Helper for driving a {@link MapTraveler} from a module tick.
 */
public final class MapTravelHelper {

    private MapTravelHelper() {
    }

    /**
     * Travels towards the target map, one step per call.
     * <p>
     * {@link MapTraveler#setTarget(GameMap)} clears the traveler's wait after a jump,
     * so it is only called when a new trip starts: the target changed, or the last trip
     * finished and the hero is not on the target map. Calling it on every tick lets the
     * traveler plan from the previous map's data right after a jump and jump straight back.
     */
    public static void travel(MapTraveler traveler, StarSystemAPI starSystem, GameMap target) {
        boolean newTrip = !Objects.equals(traveler.target, target)
                || (traveler.isDone() && !Objects.equals(starSystem.getCurrentMap(), target));
        if (newTrip) {
            traveler.setTarget(target);
        }
        if (!traveler.isDone()) {
            traveler.tick();
        }
    }
}
