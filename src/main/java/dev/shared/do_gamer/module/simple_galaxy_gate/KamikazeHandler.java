package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.objects.facades.SettingsProxy;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.KamikazeNpcFlag;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.Maps;
import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.GateHandler;
import dev.shared.do_gamer.utils.PetGearHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.util.Timer;

public class KamikazeHandler {

    private final CustomLootModule lootModule;
    private final HeroAPI hero;
    private final MovementAPI movement;
    private final PetAPI pet;
    private final GroupAPI group;
    private final SettingsProxy settingsProxy;
    private final PetGearHelper petGearHelper;

    private SimpleGalaxyGateConfig config;
    private GateHandler gateHandler;

    private Stage stage = Stage.PRIMED;
    private final Timer timer = Timer.get();
    private final Map<Npc, Long> lastAiming = new WeakHashMap<>();
    private final Timer delay = Timer.get(5_000L);
    private final Timer stuckTimer = Timer.get(10_000L);
    private final Timer detonateTimer = Timer.get(10_000L);

    private static final double MAX_DISTANCE = 3_000.0;
    private static final double RADIUS = 1_500.0;
    private static final double MAX_PAIR_DISTANCE = 300.0;
    private static final int PET_LOW_HP = 10_000;

    private enum Stage {
        INACTIVE, PRIMED, ACTIVE
    }

    public KamikazeHandler(CustomLootModule lootModule, PluginAPI api) {
        this.lootModule = lootModule;
        this.hero = api.requireAPI(HeroAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.group = api.requireAPI(GroupAPI.class);
        this.settingsProxy = Main.INSTANCE.facadeManager.settings;
        this.petGearHelper = new PetGearHelper(api);
    }

    public void setConfig(SimpleGalaxyGateConfig config) {
        this.config = config;
    }

    public void setGateHandler(GateHandler gateHandler) {
        this.gateHandler = gateHandler;
    }

    public boolean tick() {
        if (this.config == null || !this.config.kamikaze.enabled || !this.petGearHelper.isEnabled()) {
            return false;
        }

        // Reset to primed state when waiting in gate
        if (StateStore.current() == StateStore.State.WAITING_IN_GATE) {
            this.setPrimed();
        }

        if (this.isActive()) {
            this.handleActive();
            return true;
        }

        List<Npc> validTargets = this.lootModule.getNpcs().stream()
                .filter(this::isValidTarget)
                .collect(Collectors.toList());

        if (validTargets.size() < this.config.kamikaze.minNpcs) {
            this.setInactive();
            return false;
        }

        if (this.tryActivate(validTargets)) {
            return true;
        }

        // Move around center to group NPCs together
        this.petGearHelper.setPassive();
        this.moveAroundPoint(this.centerX(), this.centerY(), RADIUS);
        return true;
    }

    private double centerX() {
        return Maps.getMapCenterX() + this.gateHandler.getKamikazeShiftX();
    }

    private double centerY() {
        return Maps.getMapCenterY() + this.gateHandler.getKamikazeShiftY();
    }

    private boolean isPetAlive() {
        return this.pet.getHealth().getHp() > 0;
    }

    private boolean isPetHpLow() {
        return this.pet.getHealth().getHp() < PET_LOW_HP;
    }

    private boolean petReadyForKamikaze() {
        if (this.isPetAlive()) {
            this.stuckTimer.disarm();
            // Note: attack to PET doesn't work in groups, so skip lower HP functionality
            if (this.isPetHpLow() || this.group.hasGroup()) {
                return true; // PET is already low on HP
            }

            // Lower the PET HP
            this.lootModule.getAttacker().setTarget((Lockable) this.pet);
            this.lootModule.getAttacker().tryLockTarget();
            this.hero.launchRocket();
        } else {
            // Try to fix PET HP stuck at 0 by reactivating it
            if (!this.stuckTimer.isArmed()) {
                // Arm the timer to detect if PET HP is stuck at 0 after respawn
                this.stuckTimer.activate();
            } else if (this.stuckTimer.isInactive()) {
                // Timer expired, try to reactivate PET
                this.settingsProxy.pressKeybind(SettingsProxy.KeyBind.ACTIVE_PET);
                this.stuckTimer.disarm();
            }
        }
        return false;
    }

    // ############################# //
    // Stage handling methods (start)
    private boolean isActive() {
        return this.stage == Stage.ACTIVE;
    }

    private boolean isPrimed() {
        return this.stage == Stage.PRIMED;
    }

    private void setActive() {
        this.stage = Stage.ACTIVE;
    }

    private void setInactive() {
        this.stage = Stage.INACTIVE;
        this.detonateTimer.disarm();
    }

    private void setPrimed() {
        this.stage = Stage.PRIMED;
    }

    private void setCooldown() {
        this.setInactive();
        this.timer.activate(this.config.kamikaze.cooldown.seconds * 1_000L);
    }

    private boolean hasCooldown() {
        return this.timer.isActive();
    }
    // Stage handling methods (end)
    // ########################### //

    private boolean recentlyAimingAtHero(Npc npc) {
        if (npc.isAiming(this.hero)) {
            this.lastAiming.put(npc, System.currentTimeMillis());
            return true;
        }
        Long last = this.lastAiming.get(npc);
        if (last != null && System.currentTimeMillis() - last <= 1_000L) {
            return true;
        }
        this.lastAiming.remove(npc);
        return false;
    }

    private boolean isValidTarget(Npc npc) {
        return npc.getInfo().hasExtraFlag(KamikazeNpcFlag.KAMIKAZE) && this.recentlyAimingAtHero(npc)
                && (this.isPrimed()
                        || npc.distanceTo(this.centerX(), this.centerY()) <= MAX_DISTANCE);
    }

    private void enterKamikazeState() {
        // Stop attacking
        if (this.lootModule.getAttacker().isAttacking() && this.isPetHpLow()) {
            this.lootModule.getAttacker().stopAttack();
        }

        // Use kamikaze config
        this.hero.setMode(this.config.kamikaze.shipMode);
        StateStore.request(StateStore.State.KAMIKAZE);
    }

    private void handleActive() {
        this.enterKamikazeState();

        if (!this.isPetAlive()
                || this.hero.getHealth().hpPercent() <= this.config.kamikaze.hpRange.getMin()
                || this.hero.getHealth().shieldPercent() <= this.config.kamikaze.shieldRange.getMin()) {
            this.setCooldown();
        } else {
            // stop movement, set pet to kamikaze gear and wait
            if (this.movement.isMoving()) {
                this.movement.stop(false);
            }
            this.petGearHelper.tryUse(PetGear.KAMIKAZE);

            // If the PET fails to detonate quickly, abort kamikaze state
            if (!this.detonateTimer.isArmed()) {
                this.detonateTimer.activate();
            }
            if (this.detonateTimer.isInactive()) {
                this.setInactive();
            }
        }
    }

    private boolean tryActivate(List<Npc> validTargets) {
        this.enterKamikazeState();

        if (!this.hasCooldown() && this.petReadyForKamikaze()
                && this.hero.getHealth().hpPercent() >= this.config.kamikaze.hpRange.getMax()
                && this.hero.getHealth().shieldPercent() >= this.config.kamikaze.shieldRange.getMax()) {
            if (this.isNpcsCloseEnough(validTargets)) {
                if (this.delay.isInactive()) {
                    this.setActive();
                }
                return true;
            }
            // Additional delay for primed attempt to wait next wave of NPCs
            if (this.isPrimed()) {
                this.delay.activate();
            }
        }

        return false;
    }

    private boolean isNpcsCloseEnough(List<Npc> validTargets) {
        double maxPairDist = 0.0;
        int size = validTargets.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double dist = validTargets.get(i).distanceTo(validTargets.get(j));
                if (dist > maxPairDist) {
                    maxPairDist = dist;
                }
            }
        }
        return maxPairDist <= MAX_PAIR_DISTANCE;
    }

    private void moveAroundPoint(double x, double y, double radius) {
        Location targetLoc = Location.of(x, y);
        double distance = this.hero.distanceTo(x, y);
        double angle = targetLoc.angleTo(this.hero);

        double maxRadFix = radius / 2.0;
        double radiusFix = ((int) Math.max(Math.min(radius - distance, maxRadFix), -maxRadFix));
        distance = radius + radiusFix;
        double angleDiff = Math.max((double) this.hero.getSpeed() * 0.625F
                + 200.0F * 0.625F
                - this.hero.distanceTo(Location.of(targetLoc, angle, distance)), 0.0F) / distance;

        Location direction = Location.of(targetLoc, angle + angleDiff, distance);
        this.lootModule.searchValidLocation(direction, targetLoc, angle, distance);
        this.movement.moveTo(direction);
    }
}
