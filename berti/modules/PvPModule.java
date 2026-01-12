package dev.shared.berti.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.PlayerInfo;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.selectors.LaserSelector;
import eu.darkbot.api.extensions.selectors.PrioritizedSupplier;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.items.Item;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.Health;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.game.other.Movable;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import dev.shared.berti.behaviors.configs.PvpHelperConfig;
import eu.darkbot.shared.modules.MapModule;
import eu.darkbot.shared.utils.SafetyFinder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Feature(name="PvP Module", description="This Module Attacks enemies without Formations on Slotbar")
public class PvPModule
implements Configurable<PvpHelperConfig>,
LaserSelector,
PrioritizedSupplier<SelectableItem.Laser>,
Module {
    protected AttackAPI attackAPI;
    protected BackpageAPI backpageAPI;
    protected BotAPI botAPI;
    protected ConfigAPI configAPI;
    protected EntitiesAPI entitiesAPI;
    protected HeroAPI heroAPI;
    protected HeroItemsAPI itemsAPI;
    protected Main main;
    protected MovementAPI movementAPI;
    protected PluginAPI api;
    protected StarSystemAPI starSystemAPI;
    protected SafetyFinder safetyFinder;
    protected GroupAPI groupAPI;
    protected Collection<? extends Portal> portals;
    protected Collection<? extends Player> allShips;
    protected Collection<? extends Station> station;
    protected final ConfigSetting<Integer> workingMap;
    protected final ConfigSetting<Integer> npcDistanceIgnore;
    protected final ConfigSetting<Integer> maxCircleIterations;
    protected long lastCheckPvp;
    protected boolean backwards;
    protected Item rsb;
    private long usedRsb = 0L;
    private PvpHelperConfig config;
    protected long lastSpotTime;
    protected long lastMoveToSpot;
    protected long lastReportTime;

    public void setConfig(ConfigSetting<PvpHelperConfig> config) {
        this.config = (PvpHelperConfig)config.getValue();
    }

    public PvPModule(PluginAPI api, Main main) {
        this.api = api;
        this.main = main;
        this.heroAPI = (HeroAPI)api.requireAPI(HeroAPI.class);
        this.attackAPI = (AttackAPI)api.requireAPI(AttackAPI.class);
        this.configAPI = (ConfigAPI)api.requireAPI(ConfigAPI.class);
        this.movementAPI = (MovementAPI)api.requireAPI(MovementAPI.class);
        this.entitiesAPI = (EntitiesAPI)api.requireAPI(EntitiesAPI.class);
        this.itemsAPI = (HeroItemsAPI)api.requireAPI(HeroItemsAPI.class);
        this.backpageAPI = (BackpageAPI)api.requireAPI(BackpageAPI.class);
        this.groupAPI = (GroupAPI)api.requireAPI(GroupAPI.class);
        this.starSystemAPI = (StarSystemAPI)api.requireAPI(StarSystemAPI.class);
        this.botAPI = (BotAPI)api.requireAPI(BotAPI.class);
        this.safetyFinder = (SafetyFinder)api.requireInstance(SafetyFinder.class);
        this.maxCircleIterations = this.configAPI.requireConfig("loot.max_circle_iterations");
        this.npcDistanceIgnore = this.configAPI.requireConfig("loot.npc_distance_ignore");
        this.workingMap = this.configAPI.requireConfig("general.working_map");
        this.station = this.entitiesAPI.getStations();
        this.portals = this.entitiesAPI.getPortals();
        this.allShips = this.entitiesAPI.getPlayers();
    }

    public boolean FindEnemy() {
        boolean canAttack = this.heroAPI.getMap().isPvp() || this.portals.stream().noneMatch(p -> this.heroAPI.distanceTo((Locatable)p) < this.config.ATTACK_RADIUS);
        Lockable currentTarget = this.attackAPI.hasTarget() ? this.attackAPI.getTarget() : null;
        if (canAttack && currentTarget instanceof Player && currentTarget.isValid() && this.heroAPI.getLocalTarget() != null && currentTarget.distanceTo((Locatable)this.heroAPI) <= (double)((Integer)this.npcDistanceIgnore.getValue()).intValue() && this.portals.stream().noneMatch(p -> currentTarget.distanceTo((Locatable)p) < this.config.ATTACK_RADIUS)) {
            return true;
        }
        if (currentTarget != null && (!currentTarget.isValid() || currentTarget.getHealth().hpPercent() <= 0.0)) {
            this.attackAPI.setTarget(null);
        }
        if (this.lastCheckPvp < System.currentTimeMillis()) {
            Station localStation = this.station.stream().filter(x -> x instanceof Station.Headquarter || x instanceof Station.HomeBase).findFirst().orElse(null);
            boolean hasBlacklistedEnemy = this.allShips.stream().filter(Entity::isValid).filter(x -> x.getEntityInfo().isEnemy()).map(x -> (PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId())).filter(Objects::nonNull).anyMatch(tag -> this.config.BLACKLIST_TAG.hasTag((eu.darkbot.api.config.types.PlayerInfo)tag));
            Player player = this.allShips.stream().filter(Entity::isValid).filter(x -> !(!x.getEntityInfo().isEnemy() || !canAttack && !x.isAttacking() || x.hasEffect(290) || x.hasEffect(325) || !x.hasEffect(341) && this.backpageAPI.getInstanceURI().getRawPath().contains("gbl1"))).filter(x -> !this.isInGroup(x.getId()) && this.portals.stream().noneMatch(p -> x.distanceTo((Locatable)p) < this.config.ATTACK_RADIUS) && this.movementAPI.canMove((Locatable)x) && x.getLocationInfo().distanceTo((Locatable)this.heroAPI) <= this.config.ATTACK_RADIUS).filter(x -> {
                boolean isBlacklisted;
                eu.darkbot.api.config.types.PlayerInfo tag = (eu.darkbot.api.config.types.PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId());
                boolean whiteListEnabled = this.config.WHITELIST_TAG != null;
                boolean blackListEnabled = this.config.BLACKLIST_TAG != null;
                boolean isWhitelisted = whiteListEnabled && this.config.WHITELIST_TAG.hasTag(tag);
                boolean bl = isBlacklisted = blackListEnabled && this.config.BLACKLIST_TAG.hasTag(tag);
                if (!whiteListEnabled && !blackListEnabled) {
                    return true;
                }
                if (whiteListEnabled && !blackListEnabled) {
                    return !isWhitelisted;
                }
                if (!whiteListEnabled) {
                    return isBlacklisted;
                }
                return isBlacklisted && !isWhitelisted || !hasBlacklistedEnemy && this.config.FOCUS_OTHERS_ON_EMPTY_BLACKLIST;
            }).filter(x -> localStation == null || x.distanceTo((Locatable)localStation) >= this.config.ATTACK_RADIUS && this.heroAPI.distanceTo((Locatable)localStation) >= this.config.ATTACK_RADIUS || x.isAttacking()).min(Comparator.comparingDouble((Player s) -> s.getLocationInfo().distanceTo((Locatable)this.heroAPI)).thenComparingDouble(s -> s.getHealth().hpPercent())).orElse(null);
            if (player != null) {
                this.attackAPI.setTarget((Lockable)player);
                this.lastCheckPvp = System.currentTimeMillis() + 500L;
                return true;
            }
            this.lastCheckPvp = System.currentTimeMillis() + 150L;
            return false;
        }
        return canAttack && this.attackAPI.hasTarget();
    }

    @NotNull
    public PrioritizedSupplier<SelectableItem.Laser> getLaserSupplier() {
        return this;
    }

    public SelectableItem.Laser get() {
        if (this.shouldRsb()) {
            return this.rsb != null ? (SelectableItem.Laser)this.rsb.getAs(SelectableItem.Laser.class) : null;
        }
        Item i = this.itemsAPI.getItem(this.main.config.LOOT.AMMO_KEY);
        if (i == null) {
            i = this.itemsAPI.getItem((SelectableItem)SelectableItem.Laser.UCB_100, new ItemFlag[0]).orElse(null);
        }
        return i != null ? (SelectableItem.Laser)i.getAs(SelectableItem.Laser.class) : null;
    }

    @Nullable
    public PrioritizedSupplier.Priority getPriority() {
        return PrioritizedSupplier.Priority.MODERATE;
    }

    private boolean shouldRsb() {
        boolean isReady;
        if (!this.main.config.LOOT.RSB.ENABLED && this.config.USE_RSB || this.main.config.LOOT.RSB.KEY == null || this.attackAPI.hasTarget() && this.attackAPI.getTarget() instanceof Npc) {
            return false;
        }
        this.rsb = this.itemsAPI.getItem((SelectableItem)SelectableItem.Laser.RSB_75, new ItemFlag[0]).orElse(null);
        if (this.rsb == null) {
            this.rsb = this.itemsAPI.getItem(this.main.config.LOOT.RSB.KEY);
        }
        boolean bl = isReady = this.rsb != null && this.rsb.isUsable() && this.rsb.isReady();
        if (isReady && this.usedRsb < System.currentTimeMillis() - 1000L) {
            this.usedRsb = System.currentTimeMillis();
            return true;
        }
        return this.usedRsb > System.currentTimeMillis() - 250L;
    }

    public void AttackEnemy() {
        if (this.FindEnemy()) {
            if (this.config.CHASE_ENEMY) {
                this.pvpMovement();
            }
            this.ignoreInvalids();
            this.attackAPI.tryLockAndAttack();
        } else {
            this.attackAPI.stopAttack();
            this.attackAPI.setTarget(null);
            if (System.currentTimeMillis() - this.lastMoveToSpot <= (long)(5500 + new Random().nextInt(1000))) {
                return;
            }
            this.lastMoveToSpot = System.currentTimeMillis();
            this.movementAPI.moveRandom();
        }
    }

    protected void ignoreInvalids() {
        Lockable target = this.attackAPI.getTarget();
        double closestDist = this.movementAPI.getClosestDistance((Locatable)this.attackAPI.getTarget());
        if (target.hasEffect(325)) {
            this.attackAPI.stopAttack();
            this.attackAPI.setBlacklisted(1000L);
            this.heroAPI.setLocalTarget(null);
        } else if (!Objects.equals(this.heroAPI.getTarget(), this.attackAPI.getTarget())) {
            if (closestDist > this.config.ATTACK_RADIUS) {
                this.attackAPI.setBlacklisted(1000L);
                this.heroAPI.setLocalTarget((Lockable)null);
            }
        } else if (this.ignore(closestDist, target)) {
            this.attackAPI.setBlacklisted(5000L);
            this.heroAPI.setLocalTarget((Lockable)null);
        }
    }

    protected boolean ignore(double closestDist, Lockable target) {
        if (!this.attackAPI.isBugged() && !(this.heroAPI.distanceTo((Locatable)target) > (double)((Integer)this.npcDistanceIgnore.getValue()).intValue())) {
            Health health = target.getHealth();
            if (closestDist > this.config.ATTACK_RADIUS && health.hpPercent() > 0.9) {
                return true;
            }
            return closestDist > this.config.ATTACK_RADIUS && !target.isMoving() && (target.getHealth().shieldIncreasedIn(1000) || target.getHealth().shieldPercent() > 0.99);
        }
        return true;
    }

    public boolean isInGroup(int id) {
        if (this.groupAPI.hasGroup()) {
            return this.groupAPI.getMembers().stream().anyMatch(gm -> gm.getId() == id);
        }
        return false;
    }

    protected int speed(Lockable target) {
        return target instanceof Movable ? ((Movable)target).getSpeed() : 0;
    }

    protected void pvpMovement() {
        if (this.attackAPI.hasTarget()) {
            double angleDiff;
            Lockable target = this.attackAPI.getTarget();
            Location direction = this.movementAPI.getDestination();
            Location targetLoc = target.getLocationInfo().destinationInTime(250L);
            double distance = this.heroAPI.distanceTo((Locatable)this.attackAPI.getTarget());
            double angle = targetLoc.angleTo((Locatable)this.heroAPI);
            double radius = this.radius();
            double speed = this.speed(target);
            boolean noCircle = this.attackAPI.hasExtraFlag((Enum)NpcFlag.NO_CIRCLE);
            if (radius > 750.0) {
                noCircle = false;
            }
            if (noCircle) {
                double dist = targetLoc.distanceTo((Locatable)direction);
                double minRad = Math.max(0.0, Math.min(radius - 200.0, radius * 0.5));
                if (dist <= radius && dist >= minRad) {
                    this.setFormation((Locatable)direction);
                    return;
                }
                distance = minRad + Math.random() * (radius - minRad - 10.0);
                angleDiff = Math.random() * 0.1 - 0.05;
            } else {
                double dist = radius / 2.0;
                double minRad = (int)Math.max(Math.min(radius - distance, dist), -dist);
                distance = radius += minRad;
                angleDiff = Math.max((double)this.heroAPI.getSpeed() * 0.625 + Math.max(200.0, speed) * 0.625 - this.heroAPI.distanceTo((Locatable)Location.of((Locatable)targetLoc, (double)angle, (double)radius)), 0.0) / radius;
            }
            direction = this.bestDir((Locatable)targetLoc, angle, angleDiff, distance);
            this.searchLoc(direction, targetLoc, angle, distance);
            this.setFormation((Locatable)direction);
            this.movementAPI.moveTo((Locatable)direction);
        }
    }

    protected void searchLoc(Location direction, Location targetLoc, double angle, double distance) {
        while (!this.movementAPI.canMove((Locatable)direction) && distance < 10000.0) {
            direction.toAngle((Locatable)targetLoc, angle += this.backwards ? -0.3 : 0.3, distance += 2.0);
        }
        if (distance >= 10000.0) {
            direction.toAngle((Locatable)targetLoc, angle, 500.0);
        }
    }

    protected Location bestDir(Locatable targetLoc, double angle, double angleDiff, double distance) {
        int maxCircleIterations = (Integer)this.maxCircleIterations.getValue();
        int iteration = 1;
        double forwardScore = 0.0;
        double backScore = 0.0;
        while ((forwardScore += this.movementScore(Locatable.of((Locatable)targetLoc, (double)(angle + angleDiff * (double)iteration), (double)distance))) < 0.0 == (backScore += this.movementScore(Locatable.of((Locatable)targetLoc, (double)(angle - angleDiff * (double)iteration), (double)distance))) < 0.0 && !(Math.abs(forwardScore - backScore) > 300.0) && iteration++ < maxCircleIterations) {
        }
        if (iteration <= maxCircleIterations) {
            this.backwards = backScore > forwardScore;
        }
        return Location.of((Locatable)targetLoc, (double)(angle + angleDiff * (double)(this.backwards ? -1 : 1)), (double)distance);
    }

    protected double movementScore(Locatable loc) {
        return (double)(this.movementAPI.canMove(loc) ? 0 : -1000) - this.allShips.stream().filter(n -> this.attackAPI.getTarget() != n).mapToDouble(n -> Math.max(0.0, 550.0 - n.distanceTo(loc))).sum();
    }

    protected double radius() {
        return this.attackAPI.modifyRadius(this.config.RADIUS);
    }

    public void setFormation(Locatable direction) {
        if (this.attackAPI.hasTarget() && this.attackAPI.getTarget() instanceof Player && this.attackAPI.getTarget().isValid()) {
            if (this.attackAPI.getTarget().getHealth().hpPercent() < 0.25 && this.heroAPI.distanceTo(direction) > this.config.RADIUS * 2.0) {
                this.heroAPI.setMode((ShipMode)this.config.RUN_CONFIG);
            } else if (this.heroAPI.distanceTo(direction) > this.config.RADIUS * 3.0) {
                this.heroAPI.setMode((ShipMode)this.config.ROAM_CONFIG);
            } else {
                this.heroAPI.setMode((ShipMode)this.config.ATTACK_CONFIG);
            }
        } else {
            this.heroAPI.setMode((ShipMode)this.config.ROAM_CONFIG);
        }
    }

    protected boolean checkMap() {
        if (!((Integer)this.workingMap.getValue()).equals(this.starSystemAPI.getCurrentMap().getId()) && !this.portals.isEmpty()) {
            ((MapModule)this.botAPI.setModule((Module)new MapModule(this.api, true))).setTarget(this.starSystemAPI.getOrCreateMap(((Integer)this.workingMap.getValue()).intValue()));
            return false;
        }
        return true;
    }

    public void onTickModule() {
        if (this.safetyFinder.tick() && this.checkMap()) {
            this.AttackEnemy();
        }
    }
}
