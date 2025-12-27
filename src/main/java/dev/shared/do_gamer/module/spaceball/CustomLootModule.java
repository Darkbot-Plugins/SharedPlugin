package dev.shared.do_gamer.module.spaceball;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.shared.modules.LootModule;

public class CustomLootModule extends LootModule {

    private static final int ANGLE_MOVES_BEFORE_FLIP = 20;

    private int angleMoves = 0; // Track moves
    private boolean flipAngle = false;
    private boolean isCustom = true; // Flag to indicate custom mode

    public CustomLootModule(PluginAPI api) {
        super(api);
    }

    @Override
    public void onTickModule() {
        this.isCustom = false;
        super.onTickModule();
    }

    // Custom Attack mode tick
    public void onTickAttackModule() {
        this.isCustom = true;
        super.onTickModule();
    }

    // Custom Follow mode tick
    public void onTickFollowModule() {
        this.isCustom = true;
        if (this.checkDangerousAndCurrentMap()) {
            this.pet.setEnabled(true);
            if (this.findTarget()) {
                this.moveToAnSafePosition();
                this.ignoreInvalidTarget();
                // Follow only
                this.hero.setRoamMode();
                this.attack.tryLockTarget();
                if (this.attack.isAttacking()) {
                    this.attack.stopAttack();
                }
            }
        }
    }

    // Public access to findTarget method
    public boolean customFindTarget() {
        return this.findTarget();
    }

    @Override
    protected boolean shouldIgnore(double closestDist, Lockable target) {
        if (this.isCustom) {
            // Never ignore in custom mode
            return false;
        }
        return super.shouldIgnore(closestDist, target);
    }

    @Override
    protected void moveToAnSafePosition() {
        if (this.isCustom) {
            this.customMoveToAnSafePosition();
        } else {
            super.moveToAnSafePosition();
        }

    }

    // Custom movement logic without circle around target
    private void customMoveToAnSafePosition() {
        if (this.attack.hasTarget()) {
            Lockable target = this.attack.getTarget();
            Location direction = this.movement.getDestination();
            Location targetLoc = target.getLocationInfo().destinationInTime(250L);
            double angle = targetLoc.angleTo(this.hero);
            double radius = this.getRadius(target);

            double minRad;
            double distance;
            double angleDiff;
            double angleDiffAdjust;
            double dist;

            // Always no circle
            dist = targetLoc.distanceTo(direction);
            minRad = Math.max(0.0, Math.min(radius - 200.0, radius * 0.5));
            if (dist <= radius && dist >= minRad) {
                this.setConfig(direction);
                return;
            }

            distance = minRad + Math.random() * (radius - minRad - 10.0);
            angleDiff = Math.random() * 0.1 - 0.05;

            // Angle diff to adjust the flight path alongside the target.
            angleDiffAdjust = 0.8 + Math.random() * 0.3;

            // Update angle movements
            this.angleMoves++;
            if (this.angleMoves > ANGLE_MOVES_BEFORE_FLIP) {
                this.flipAngle = !this.flipAngle; // Switch angle
                this.angleMoves = 0; // Reset counter
            }

            if (this.flipAngle) {
                angleDiff -= angleDiffAdjust; // Flipped
            } else {
                angleDiff += angleDiffAdjust; // Normal
            }

            direction = this.getBestDir(targetLoc, angle, angleDiff, distance);
            this.searchValidLocation(direction, targetLoc, angle, distance);
            this.setConfig(direction);
            this.movement.moveTo(direction);
        }
    }
}
