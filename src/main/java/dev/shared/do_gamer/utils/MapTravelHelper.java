package dev.shared.do_gamer.utils;

import java.util.Objects;

import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.util.Timer;

/**
 * Helper for driving a {@link MapTraveler} from a module tick.
 */
public class MapTravelHelper {

    // Minimum time between two starts of a trip to the same target
    private static final long RESTART_DELAY_MS = 3_000L;

    private final MapTraveler traveler;
    private final StarSystemAPI starSystem;
    private final Timer restartTimer = Timer.get(RESTART_DELAY_MS);

    public MapTravelHelper(MapTraveler traveler, StarSystemAPI starSystem) {
        this.traveler = traveler;
        this.starSystem = starSystem;
    }

    /**
     * Travels towards the target map, one step per call.
     * <p>
     * {@link MapTraveler#setTarget(GameMap)} clears the traveler's wait after a jump,
     * so it is only called when a trip starts: the target changed, or the last trip
     * finished and the hero is not on the target map. Calling it on every tick lets the
     * traveler plan from the previous map's data right after a jump and jump straight back.
     * A finished trip is started again at most once per {@link #RESTART_DELAY_MS},
     * so a failed trip (no portal found) is not retried on every tick.
     */
    public void travel(GameMap target) {
        if (!Objects.equals(this.traveler.target, target) || this.canRestart(target)) {
            this.traveler.setTarget(target);
            this.restartTimer.activate();
        }
        if (!this.traveler.isDone()) {
            this.traveler.tick();
        }
    }

    /**
     * Checks if the last trip to the target finished away from it and can be started again.
     */
    private boolean canRestart(GameMap target) {
        return this.traveler.isDone()
                && !Objects.equals(this.starSystem.getCurrentMap(), target)
                && this.restartTimer.isInactive();
    }
}
