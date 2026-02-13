package dev.shared.berti.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.PlayerInfo;
import dev.shared.berti.modules.configs.BaseDefendModuleConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.events.EventHandler;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.selectors.LaserSelector;
import eu.darkbot.api.extensions.selectors.PrioritizedSupplier;
import eu.darkbot.api.game.entities.*;
import eu.darkbot.api.game.group.GroupMember;
import eu.darkbot.api.game.items.Item;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.*;
import eu.darkbot.api.managers.*;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Feature(name="Base Defend", description="Defend Clan Battle Stations")
public class BaseDefendModule
implements Module,
Configurable<BaseDefendModuleConfig>,
Listener,
LaserSelector,
PrioritizedSupplier<SelectableItem.Laser> {
    protected final Main main;
    protected Collection<? extends BattleStation> stations;
    protected Collection<? extends GroupMember> groupMembers;
    protected Collection<? extends Player> allShips;
    protected Collection<? extends Portal> portals;
    protected BackpageAPI backpageAPI;
    protected HeroItemsAPI itemsAPI;
    protected AttackAPI attack;
    protected HeroAPI hero;
    protected MovementAPI movement;
    protected PetAPI pet;
    protected ConfigAPI configApi;
    protected HeroItemsAPI heroItemsAPI;
    protected StarSystemAPI starSystem;
    protected PortalJumper portalJumper;
    protected SafetyFinder safety;
    protected ConfigSetting<Integer> workingMap;
    protected ConfigSetting<Integer> npcDistanceIgnore;
    protected ConfigSetting<Integer> maxCircleIterations;
    protected ConfigSetting<Boolean> runConfigInCircle;
    private BaseDefendModuleConfig config;
    private long lastCheckPvp;
    protected Location lastSpot = Location.of((double)0.0, (double)0.0);
    protected Integer waitTicks = 0;
    protected GroupAPI group;
    protected Item rsb;
    private long usedRsb = 0L;
    protected boolean backwards;
    protected long lastSpotTime;
    protected long lastMoveToSpot;
    protected long lastReportTime;
    protected final Map<String, Integer> mapStringToMapId = Map.ofEntries(new AbstractMap.SimpleEntry<String, Integer>("1-3", 3), new AbstractMap.SimpleEntry<String, Integer>("1-4", 4), new AbstractMap.SimpleEntry<String, Integer>("2-3", 7), new AbstractMap.SimpleEntry<String, Integer>("2-4", 8), new AbstractMap.SimpleEntry<String, Integer>("3-3", 11), new AbstractMap.SimpleEntry<String, Integer>("3-4", 12), new AbstractMap.SimpleEntry<String, Integer>("4-1", 13), new AbstractMap.SimpleEntry<String, Integer>("4-2", 14), new AbstractMap.SimpleEntry<String, Integer>("4-3", 15), new AbstractMap.SimpleEntry<String, Integer>("4-4", 16), new AbstractMap.SimpleEntry<String, Integer>("4-5", 29), new AbstractMap.SimpleEntry<String, Integer>("1-5", 17), new AbstractMap.SimpleEntry<String, Integer>("1-6", 18), new AbstractMap.SimpleEntry<String, Integer>("1-7", 19), new AbstractMap.SimpleEntry<String, Integer>("2-5", 21), new AbstractMap.SimpleEntry<String, Integer>("2-6", 22), new AbstractMap.SimpleEntry<String, Integer>("2-7", 23), new AbstractMap.SimpleEntry<String, Integer>("3-5", 25), new AbstractMap.SimpleEntry<String, Integer>("3-6", 26), new AbstractMap.SimpleEntry<String, Integer>("3-7", 27), new AbstractMap.SimpleEntry<String, Integer>("1-BL", 306), new AbstractMap.SimpleEntry<String, Integer>("2-BL", 307), new AbstractMap.SimpleEntry<String, Integer>("3-BL", 308));
    protected final Map<Integer, Integer> intToMapId = Map.ofEntries(new AbstractMap.SimpleEntry<Integer, Integer>(1, 3), new AbstractMap.SimpleEntry<Integer, Integer>(2, 4), new AbstractMap.SimpleEntry<Integer, Integer>(3, 7), new AbstractMap.SimpleEntry<Integer, Integer>(4, 8), new AbstractMap.SimpleEntry<Integer, Integer>(5, 11), new AbstractMap.SimpleEntry<Integer, Integer>(6, 12), new AbstractMap.SimpleEntry<Integer, Integer>(7, 13), new AbstractMap.SimpleEntry<Integer, Integer>(8, 14), new AbstractMap.SimpleEntry<Integer, Integer>(9, 15), new AbstractMap.SimpleEntry<Integer, Integer>(10, 16), new AbstractMap.SimpleEntry<Integer, Integer>(11, 29), new AbstractMap.SimpleEntry<Integer, Integer>(12, 17), new AbstractMap.SimpleEntry<Integer, Integer>(13, 18), new AbstractMap.SimpleEntry<Integer, Integer>(14, 19), new AbstractMap.SimpleEntry<Integer, Integer>(15, 21), new AbstractMap.SimpleEntry<Integer, Integer>(16, 22), new AbstractMap.SimpleEntry<Integer, Integer>(17, 23), new AbstractMap.SimpleEntry<Integer, Integer>(18, 25), new AbstractMap.SimpleEntry<Integer, Integer>(19, 26), new AbstractMap.SimpleEntry<Integer, Integer>(20, 27), new AbstractMap.SimpleEntry<Integer, Integer>(21, 306), new AbstractMap.SimpleEntry<Integer, Integer>(22, 307), new AbstractMap.SimpleEntry<Integer, Integer>(23, 308));
    protected final Map<Integer, Integer> mapIdToInt = Map.ofEntries(new AbstractMap.SimpleEntry<Integer, Integer>(3, 1), new AbstractMap.SimpleEntry<Integer, Integer>(4, 2), new AbstractMap.SimpleEntry<Integer, Integer>(7, 3), new AbstractMap.SimpleEntry<Integer, Integer>(8, 4), new AbstractMap.SimpleEntry<Integer, Integer>(11, 5), new AbstractMap.SimpleEntry<Integer, Integer>(12, 6), new AbstractMap.SimpleEntry<Integer, Integer>(13, 7), new AbstractMap.SimpleEntry<Integer, Integer>(14, 8), new AbstractMap.SimpleEntry<Integer, Integer>(15, 9), new AbstractMap.SimpleEntry<Integer, Integer>(16, 10), new AbstractMap.SimpleEntry<Integer, Integer>(29, 11), new AbstractMap.SimpleEntry<Integer, Integer>(17, 12), new AbstractMap.SimpleEntry<Integer, Integer>(18, 13), new AbstractMap.SimpleEntry<Integer, Integer>(19, 14), new AbstractMap.SimpleEntry<Integer, Integer>(21, 15), new AbstractMap.SimpleEntry<Integer, Integer>(22, 16), new AbstractMap.SimpleEntry<Integer, Integer>(23, 17), new AbstractMap.SimpleEntry<Integer, Integer>(25, 18), new AbstractMap.SimpleEntry<Integer, Integer>(26, 19), new AbstractMap.SimpleEntry<Integer, Integer>(27, 20), new AbstractMap.SimpleEntry<Integer, Integer>(306, 21), new AbstractMap.SimpleEntry<Integer, Integer>(307, 22), new AbstractMap.SimpleEntry<Integer, Integer>(308, 23));
    private static final Set<String> CHAT_TYPES = Stream.of(ChatAPI.Type.CLAN, ChatAPI.Type.GROUP).map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    public BaseDefendModule(EntitiesAPI entitiesAPI, PluginAPI api, PortalJumper portalJumper, Main main, SafetyFinder safety) {
        this.portalJumper = portalJumper;
        this.safety = safety;
        this.main = main;
        this.configApi = (ConfigAPI)api.getAPI(ConfigAPI.class);
        this.itemsAPI = (HeroItemsAPI)api.requireAPI(HeroItemsAPI.class);
        this.backpageAPI = (BackpageAPI)api.requireAPI(BackpageAPI.class);
        this.pet = (PetAPI)api.requireAPI(PetAPI.class);
        this.attack = (AttackAPI)api.requireAPI(AttackAPI.class);
        this.hero = (HeroAPI)api.requireAPI(HeroAPI.class);
        this.movement = (MovementAPI)api.requireAPI(MovementAPI.class);
        this.starSystem = (StarSystemAPI)api.requireAPI(StarSystemAPI.class);
        this.group = (GroupAPI)api.requireAPI(GroupAPI.class);
        this.workingMap = this.configApi.requireConfig("general.working_map");
        this.npcDistanceIgnore = this.configApi.requireConfig("loot.npc_distance_ignore");
        this.maxCircleIterations = this.configApi.requireConfig("loot.max_circle_iterations");
        this.runConfigInCircle = this.configApi.requireConfig("loot.run_config_in_circle");
        this.stations = entitiesAPI.getBattleStations();
        this.allShips = entitiesAPI.getPlayers();
        this.portals = entitiesAPI.getPortals();
        this.groupMembers = this.group.getMembers();
        this.lastReportTime = 0L;
    }

    public void onTickModule() {
        if (this.safety.tick() && this.checkMap()) {
            try {
                this.pet.setEnabled(true);
                Optional<? extends BattleStation> friendlyStation = this.stations.stream().filter(s -> !s.getEntityInfo().isEnemy() && s instanceof BattleStation.Hull).findFirst();
                Optional<? extends BattleStation> asteroid = this.stations.stream().filter(a -> a instanceof BattleStation.Asteroid).findFirst();
                Optional<? extends BattleStation> enemyStation = this.stations.stream().filter(s -> s.getEntityInfo().isEnemy() && s instanceof BattleStation.Hull).findFirst();
                if (friendlyStation.isPresent() && !friendlyStation.get().hasEffect(45)) {
                    this.RoamToSpotAndAttackEnemy(friendlyStation.get());
                } else if (this.config.cycleMapsAutomatically && (asteroid.isPresent() || friendlyStation.isPresent() || enemyStation.isPresent())) {
                    if ((Integer)this.workingMap.getValue() == 308) {
                        this.workingMap.setValue(this.intToMapId.get(1));
                    } else if (((Integer)this.workingMap.getValue()).intValue() == this.hero.getMap().getId()) {
                        this.workingMap.setValue(this.intToMapId.get(this.mapIdToInt.get(this.workingMap.getValue()) + 1));
                    }
                } else if (this.config.waitOnAstroid && asteroid.isPresent()) {
                    this.RoamToSpotAndAttackEnemy(asteroid.get());
                } else if (enemyStation.isPresent() || asteroid.isPresent()) {
                    this.MoveToClosestPortal();
                } else {
                    friendlyStation.ifPresent(this::RoamToSpotAndAttackEnemy);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
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
        if (!this.main.config.LOOT.RSB.ENABLED && this.config.useRSB || this.main.config.LOOT.RSB.KEY == null || this.attack.hasTarget() && this.attack.getTarget() instanceof Npc) {
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

    public Location DetermineLocationToMoveTo(int radius, BattleStation location) {
        Random rand = new Random();
        return Location.of((double)((double)location.x() - (double)radius / 4.0 + (double)rand.nextInt(radius)), (double)((double)location.y() - (double)radius / 4.0 + (double)rand.nextInt(radius)));
    }

    private void MoveToClosestPortal() {
        this.portals.stream().min(Comparator.comparing(portal -> portal.getLocationInfo().distanceTo((Locatable)this.hero))).ifPresent(closest -> {
            if (closest.getLocationInfo().getCurrent().distanceTo((Locatable)this.hero) < 100.0) {
                this.movement.stop(false);
            } else {
                this.movement.moveTo((Locatable)closest);
            }
        });
    }

    private void RoamToSpotAndAttackEnemy(BattleStation friendlyStation) {
        if (this.config.wait_for_group && this.groupMembers.stream().anyMatch(groupMember -> groupMember.getMapId() != this.hero.getMap().getId())) {
            this.MoveToClosestPortal();
        } else {
            if (this.hero.getHealth().hpPercent() <= 0.25) {
                this.hero.setMode((ShipMode)this.config.RUN_CONFIG);
            }
            this.AttackEnemy(friendlyStation);
        }
    }

    public void RoamToSpot(BattleStation station) {
        if (System.currentTimeMillis() - this.lastMoveToSpot <= (long)(1000 + new Random().nextInt(1000))) {
            return;
        }
        if (System.currentTimeMillis() >= this.lastSpotTime + 30000L && this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) <= 20.0) {
            this.hero.setMode((ShipMode)this.config.ROAM_CONFIG);
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo((int)this.config.radius, station));
            this.lastMoveToSpot = System.currentTimeMillis();
        } else if (this.hero.distanceTo((Locatable)station) >= 900.0) {
            this.hero.setMode((ShipMode)this.config.ROAM_CONFIG);
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo((int)this.config.radius, station));
        }
        if (this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) > 1.0) {
            this.lastSpot = this.movement.getDestination();
            this.lastSpotTime = System.currentTimeMillis();
            this.lastMoveToSpot = System.currentTimeMillis();
        }
    }

    protected boolean checkMap() {
        GameMap map = this.starSystem.findMap(((Integer)this.workingMap.getValue()).intValue()).orElse(null);
        if (map != null && !this.portals.isEmpty() && map != this.starSystem.getCurrentMap()) {
            this.hero.setMode((ShipMode)this.config.RUN_CONFIG);
            this.portalJumper.travelAndJump(this.starSystem.findNext(map));
            return false;
        }
        return map == this.starSystem.getCurrentMap();
    }

    public boolean isInGroup(int id) {
        if (this.group.hasGroup()) {
            return this.group.getMembers().stream().anyMatch(gm -> gm.getId() == id);
        }
        return false;
    }

    public boolean FindEnemy(BattleStation friendlyStation) {
        Lockable currentTarget = this.attack.hasTarget() ? this.attack.getTarget() : null;
        if (currentTarget instanceof Player && currentTarget.isValid() && this.hero.getLocalTarget() != null && currentTarget.distanceTo((Locatable)this.hero) <= (double)((Integer)this.npcDistanceIgnore.getValue()).intValue() && currentTarget.distanceTo((Locatable)friendlyStation) < this.config.ATTACK_RADIUS && currentTarget.distanceTo((Locatable)this.hero) < this.config.ATTACK_RADIUS) {
            return true;
        }
        if (currentTarget != null && (!currentTarget.isValid() || currentTarget.getHealth().hpPercent() <= 0.0)) {
            this.attack.setTarget(null);
        }
        if (this.lastCheckPvp < System.currentTimeMillis()) {
            boolean hasBlacklistedEnemy = this.allShips.stream().filter(Entity::isValid).filter(x -> x.getEntityInfo().isEnemy()).map(x -> (PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId())).filter(Objects::nonNull).anyMatch(tag -> this.config.BLACKLIST_TAG.hasTag((eu.darkbot.api.config.types.PlayerInfo)tag));
            Player player = this.allShips.stream().filter(Entity::isValid).filter(x -> x.isAttacking((Lockable)friendlyStation) && !x.isOwned() || x.getEntityInfo().isEnemy() && !x.hasEffect(290) && !x.hasEffect(325) && (x.hasEffect(341) || !this.backpageAPI.getInstanceURI().getRawPath().contains("gbl1"))).filter(x -> !this.isInGroup(x.getId()) && this.portals.stream().noneMatch(p -> x.distanceTo((Locatable)p) < this.config.ATTACK_RADIUS) && this.movement.canMove((Locatable)x) && x.getLocationInfo().distanceTo((Locatable)this.hero) <= this.config.ATTACK_RADIUS).filter(x -> {
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
            }).min(Comparator.comparingDouble(s -> s.getLocationInfo().distanceTo((Locatable)friendlyStation))).orElse(null);
            if (player != null) {
                this.attack.setTarget((Lockable)player);
                this.lastCheckPvp = System.currentTimeMillis() + 500L;
                return true;
            }
            this.lastCheckPvp = System.currentTimeMillis() + 150L;
            return false;
        }
        return this.attack.hasTarget();
    }

    protected int speed(Lockable target) {
        return target instanceof Movable ? ((Movable)target).getSpeed() : 0;
    }

    protected void pvpMovement() {
        if (this.attack.hasTarget()) {
            double angleDiff;
            Lockable target = this.attack.getTarget();
            Location direction = this.movement.getDestination();
            Location targetLoc = target.getLocationInfo().destinationInTime(250L);
            double distance = this.hero.distanceTo((Locatable)this.attack.getTarget());
            double angle = targetLoc.angleTo((Locatable)this.hero);
            double radius = this.radius(target);
            double speed = this.speed(target);
            boolean noCircle = this.attack.hasExtraFlag((Enum)NpcFlag.NO_CIRCLE);
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
                angleDiff = Math.max((double)this.hero.getSpeed() * 0.625 + Math.max(200.0, speed) * 0.625 - this.hero.distanceTo((Locatable)Location.of((Locatable)targetLoc, (double)angle, (double)radius)), 0.0) / radius;
            }
            direction = this.bestDir((Locatable)targetLoc, angle, angleDiff, distance);
            this.searchLoc(direction, targetLoc, angle, distance);
            this.setFormation((Locatable)direction);
            this.movement.moveTo((Locatable)direction);
        }
    }

    protected void searchLoc(Location direction, Location targetLoc, double angle, double distance) {
        while (!this.movement.canMove((Locatable)direction) && distance < 10000.0) {
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
        return (double)(this.movement.canMove(loc) ? 0 : -1000) - this.allShips.stream().filter(n -> this.attack.getTarget() != n).mapToDouble(n -> Math.max(0.0, 550.0 - n.distanceTo(loc))).sum();
    }

    public void setFormation(Locatable direction) {
        Player target = (Player)this.attack.getTargetAs(Player.class);
        if (target != null && target.isValid() && this.hero.distanceTo((Locatable)target) < (double)((Integer)this.npcDistanceIgnore.getValue()).intValue()) {
            if (((Boolean)this.runConfigInCircle.getValue()).booleanValue() && this.hero.distanceTo(direction) > this.config.radius * 1.75) {
                this.hero.setMode((ShipMode)this.config.RUN_CONFIG);
            } else if (this.hero.getHealth().hpPercent() < this.config.chasePercent) {
                this.hero.setMode((ShipMode)this.config.RUN_CONFIG);
            } else if (this.hero.distanceTo(direction) > this.config.radius * 1.75) {
                this.hero.setMode((ShipMode)this.config.ROAM_CONFIG);
            } else {
                this.hero.setMode((ShipMode)this.config.ATTACK_CONFIG);
            }
        } else {
            this.hero.setMode((ShipMode)this.config.ROAM_CONFIG);
        }
    }

    public void AttackEnemy(BattleStation friendlyStation) {
        if (this.FindEnemy(friendlyStation)) {
            this.attack.tryLockAndAttack();
            this.ignoreInvalids();
            if (this.hero.distanceTo((Locatable)friendlyStation) >= this.config.BASE_RADIUS) {
                this.RoamToSpot(friendlyStation);
            } else {
                this.pvpMovement();
            }
            return;
        }
        this.RoamToSpot(friendlyStation);
    }

    protected void ignoreInvalids() {
        Lockable target = this.attack.getTarget();
        double closestDist = this.movement.getClosestDistance((Locatable)this.attack.getTarget());
        if (target.hasEffect(325)) {
            this.attack.stopAttack();
            this.attack.setBlacklisted(1000L);
            this.hero.setLocalTarget(null);
        } else if (!Objects.equals(this.hero.getTarget(), this.attack.getTarget())) {
            if (closestDist > this.config.ATTACK_RADIUS) {
                this.attack.setBlacklisted(1000L);
                this.hero.setLocalTarget((Lockable)null);
            }
        } else if (this.ignore(closestDist, target)) {
            this.attack.setBlacklisted(5000L);
            this.hero.setLocalTarget((Lockable)null);
        }
    }

    protected boolean ignore(double closestDist, Lockable target) {
        if (!this.attack.isBugged() && !(this.hero.distanceTo((Locatable)target) > (double)((Integer)this.npcDistanceIgnore.getValue()).intValue())) {
            Health health = target.getHealth();
            if (closestDist > this.config.ATTACK_RADIUS && health.hpPercent() > 0.9) {
                return true;
            }
            return closestDist > this.config.ATTACK_RADIUS && !target.isMoving() && (target.getHealth().shieldIncreasedIn(1000) || target.getHealth().shieldPercent() > 0.99);
        }
        return true;
    }

    @EventHandler
    public void onLogReceived(ChatAPI.MessageSentEvent ev) {
        if (!CHAT_TYPES.contains(ev.getRoom().toLowerCase(Locale.ROOT))) {
            return;
        }
        Integer mapId = this.mapStringToMapId.get(ev.getMessage().getMessage());
        if (mapId != null) {
            this.workingMap.setValue(mapId);
        }
    }

    protected double radius(Lockable target) {
        return this.attack.modifyRadius(this.config.radius);
    }

    public void setConfig(ConfigSetting<BaseDefendModuleConfig> configSetting) {
        this.config = (BaseDefendModuleConfig)configSetting.getValue();
    }

    public boolean canRefresh() {
        return !this.hero.isAttacking();
    }
}
