package dev.shared.do_gamer.module.spaceball;

import java.util.Random;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.shared.modules.CollectorModule;

public class CustomCollectorModule extends CollectorModule {

    private static final double GATE_RADIUS = 800;
    private static final int MAX_MOVES_AROUND_GATE = 30;
    private static final int DIRECTION_SWITCH_MOVES = 16;
    private static final double[] OFFSETS_X = new double[8];
    private static final double[] OFFSETS_Y = new double[8];

    static {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4; // 45-degree steps
            OFFSETS_X[i] = GATE_RADIUS * Math.cos(angle);
            OFFSETS_Y[i] = GATE_RADIUS * Math.sin(angle);
        }
    }

    private int directionMoves = 0; // Track moves around the gate
    private boolean moveClockwise = true; // Direction flag
    private int moveCounter = 0; // Track total moves
    private Random random = new Random();

    public CustomCollectorModule(PluginAPI api) {
        super(api);
    }

    @Override
    public String getStatus() {
        if (this.skipMoving()) {
            return "Skipping";
        }
        return super.getStatus();
    }

    @Override
    public void onTickModule() {
        if (this.isNotWaiting() && this.checkDangerousAndCurrentMap()) {
            this.hero.setRoamMode();
            this.pet.setEnabled(true);
            this.checkInvisibility();
            this.checkDangerous();
            this.findBox();
            if (!this.tryCollectNearestBox()
                    && (this.hero.distanceTo(this.movement.getDestination()) < 20.0 || this.movement.isOutOfMap())) {
                this.moveAroundGate();
            }
        }
    }

    private boolean skipMoving() {
        return this.moveCounter > MAX_MOVES_AROUND_GATE;
    }

    public void resetMoveCounter() {
        this.moveCounter = 0; // Reset total moves counter
    }

    private Portal findClosestGate() {
        return this.portals.stream()
                .filter(portal -> portal.getTargetMap()
                        .map(map -> {
                            String mapName = map.getName();
                            return mapName.equals("1-5") || mapName.equals("2-5") || mapName.equals("3-5");
                        })
                        .orElse(false))
                .min((p1, p2) -> Double.compare(this.hero.distanceTo(p1), this.hero.distanceTo(p2)))
                .orElse(null); // Return null if no gate is found
    }

    private void moveAroundGate() {
        if (this.skipMoving()) {
            return; // Skip moving if too many moves have been made
        }

        Portal gate = this.findClosestGate();
        if (gate == null) {
            return; // No gate found, skip moving
        }

        double gateX = gate.getX();
        double gateY = gate.getY();

        int randomShift = this.random.nextInt(201) - 100; // Random shift (-100 to +100) to humanize movement

        // Find the closest point among the 8 points
        int closestIndex = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double distance = this.hero.distanceTo(
                    gateX + OFFSETS_X[i] + randomShift,
                    gateY + OFFSETS_Y[i] + randomShift);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }

        // Determine the next point based on the direction
        int nextIndex = this.moveClockwise
                ? (closestIndex + 1) % 8 // Clockwise
                : (closestIndex + 7) % 8; // Counterclockwise

        // Update movement direction if two full loops are completed
        this.directionMoves++;
        if (this.directionMoves >= DIRECTION_SWITCH_MOVES) {
            this.moveClockwise = !this.moveClockwise; // Switch direction
            this.directionMoves = 0; // Reset counter
        }

        this.movement.moveTo(
                gateX + OFFSETS_X[nextIndex] + randomShift,
                gateY + OFFSETS_Y[nextIndex] + randomShift); // Move to the next point

        this.moveCounter++; // Increment total moves counter
    }
}
