package dev.shared.berti.modules.BL;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.ConfigEntity;
import com.github.manolo8.darkbot.config.NpcExtra;
import com.github.manolo8.darkbot.config.NpcExtraFlag;
import com.github.manolo8.darkbot.config.NpcInfo;
import com.github.manolo8.darkbot.config.PlayerInfo;
import com.github.manolo8.darkbot.config.ZoneInfo;
import com.github.manolo8.darkbot.core.manager.MapManager;
import com.github.manolo8.darkbot.core.objects.facades.SettingsProxy;
import com.github.manolo8.darkbot.core.utils.Location;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.BoxInfo;
import eu.darkbot.api.config.types.NpcFlag;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.events.EventHandler;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.extensions.selectors.LaserSelector;
import eu.darkbot.api.extensions.selectors.PrioritizedSupplier;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Entity;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Player;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.group.GroupMember;
import eu.darkbot.api.game.items.Item;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Health;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.LocationInfo;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.game.other.Movable;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.BoosterAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameLogAPI;
import eu.darkbot.api.managers.GameResourcesAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;
import dev.shared.berti.modules.configs.BLConfig;
import dev.shared.berti.types.enums.BLMaps;
import dev.shared.berti.types.enums.CustomEvent;
import dev.shared.berti.types.enums.Mode;
import dev.shared.berti.types.enums.RestartOptions;
import dev.shared.berti.utils.Utils;
import eu.darkbot.shared.modules.LootModule;
import eu.darkbot.shared.modules.MapModule;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class BLModule
extends LootModule
implements Task,
Listener,
LaserSelector,
PrioritizedSupplier<SelectableItem.Laser> {
    protected final PluginAPI api;
    protected final ConfigSetting<Integer> collectRadius;
    protected final Collection<? extends Box> boxes;
    protected final Collection<? extends BoosterAPI.Booster> boosters;
    protected Collection<? extends Player> allShips;
    protected Collection<? extends GroupMember> groupMembers;
    protected GroupAPI group;
    protected final RepairAPI repairAPI;
    protected final HeroItemsAPI itemsAPI;
    protected final PortalJumper portalJumper;
    protected final EntitiesAPI entitiesAPI;
    protected final BoosterAPI boosterAPI;
    protected final BackpageAPI backpageAPI;
    protected final SettingsProxy settingsProxy;
    protected List<Box> boxesCheckList = new ArrayList<Box>();
    protected boolean changeMap;
    protected boolean mainNPCKilled;
    protected boolean mainNPCSpawned;
    protected boolean dmgBeaconUsed;
    protected Item rsb;
    protected Item dmg_beacon;
    protected boolean ablFirst;
    private long usedRsb = 0L;
    private long ablStartTime = 0L;
    protected long mainNPCDeathTime = 0L;
    private ConfigAPI configAPI;
    private long lastCheckPvp;
    protected Integer waitTicks = 0;
    protected BoosterAPI.Booster boost;
    protected eu.darkbot.api.game.other.Location lastSpot;
    protected long lastSpotTime;
    protected long lastCloakPurchasedTime;
    protected int deathCount;
    protected int lastDeathAmount;
    protected Npc behemoth;
    protected Npc strokeLight;
    protected Npc invoke;
    protected Npc rocket;
    protected Npc abide;
    protected Npc steadfast;
    protected Npc attend;
    protected Npc impulse;
    protected Npc anyNPC;
    protected Npc abideAndSteadfast;
    protected Npc impulseAndAttend;
    protected Npc impulseAbideAndSteadfast;
    protected Npc agressiveNpc;
    protected Box currentBox;
    protected long oneBLNextMainNPCSpawnTime;
    protected long twoBLNextMainNPCSpawnTime;
    protected long threeBLNextMainNPCSpawnTime;
    protected long currentMapMainNPCRespawnTime;
    protected long nextMapMainNPCRespawnTime;
    protected long lastMoveToSpot;
    protected int oneBLBoxCount;
    protected int twoBLBoxCount;
    protected int threeBLBoxCount;
    protected long lastBoxMovementTimeout;
    protected boolean serverRestart;
    protected long serverRestartTimer;
    protected Long forceTime = 0L;
    protected boolean force;
    protected ConfigEntity conf;
    protected final List<GameLogAPI.LogMessageEvent> pastLogMessages = new ArrayList<GameLogAPI.LogMessageEvent>();
    protected long waitingUntil;
    protected final Main main;
    protected final GameResourcesAPI gameResourcesAPI;
    protected int currentMapId = -1;
    protected Mode currentMode;
    protected eu.darkbot.api.game.other.Location centerLoc;
    protected Map<Integer, BLMaps> mapIdToBLMap = Map.of(306, BLMaps.ONE_BL, 307, BLMaps.TWO_BL, 308, BLMaps.THREE_BL);
    protected Map<Integer, BLMaps> nextMapRotationON = Map.of(306, BLMaps.THREE_BL, 307, BLMaps.ONE_BL, 308, BLMaps.TWO_BL);
    protected Map<Integer, BLMaps> nextMapRotationOFF = Map.of(306, BLMaps.TWO_BL, 307, BLMaps.THREE_BL, 308, BLMaps.ONE_BL);
    protected Map<Integer, BLMaps> previousMapRotationON = Map.of(306, BLMaps.TWO_BL, 307, BLMaps.THREE_BL, 308, BLMaps.ONE_BL);
    protected Map<Integer, BLMaps> previousMapRotationOFF = Map.of(306, BLMaps.THREE_BL, 307, BLMaps.ONE_BL, 308, BLMaps.TWO_BL);
    protected Map<BLMaps, eu.darkbot.api.game.other.Location> beheLocation = Map.of(BLMaps.ONE_BL, eu.darkbot.api.game.other.Location.of((double)19330.0, (double)5780.0), BLMaps.TWO_BL, eu.darkbot.api.game.other.Location.of((double)6939.0, (double)5000.0), BLMaps.THREE_BL, eu.darkbot.api.game.other.Location.of((double)4731.0, (double)3924.0));
    protected Map<BLMaps, eu.darkbot.api.game.other.Location> strokeLocation = Map.of(BLMaps.ONE_BL, eu.darkbot.api.game.other.Location.of((double)2339.0, (double)2084.0), BLMaps.TWO_BL, eu.darkbot.api.game.other.Location.of((double)18589.0, (double)5097.0), BLMaps.THREE_BL, eu.darkbot.api.game.other.Location.of((double)18042.0, (double)5772.0));
    protected Map<BLMaps, eu.darkbot.api.game.other.Location> invokeLocation = Map.of(BLMaps.ONE_BL, eu.darkbot.api.game.other.Location.of((double)3100.0, (double)4800.0), BLMaps.TWO_BL, eu.darkbot.api.game.other.Location.of((double)9100.0, (double)11500.0), BLMaps.THREE_BL, eu.darkbot.api.game.other.Location.of((double)18500.0, (double)4000.0));
    protected Map<Mode, Map<BLMaps, eu.darkbot.api.game.other.Location>> modeToBLMapLocation = Map.of(Mode.BEHE, this.beheLocation, Mode.STROKE, this.strokeLocation, Mode.INVOKE, this.invokeLocation);
    protected BLConfig config;
    private static final int PET_LINK = 326;
    private Player closestEnemy;
    private Location lastRandomMove;
    private List<ZoneInfo.Zone> lastZones;
    private int lastZoneIdx;

    public BLModule(Main main, PluginAPI api, PortalJumper portalJumper) {
        super(api);
        this.api = api;
        this.repairAPI = (RepairAPI)api.requireAPI(RepairAPI.class);
        this.configAPI = (ConfigAPI)api.requireAPI(ConfigAPI.class);
        this.collectRadius = this.configAPI.requireConfig("collect.radius");
        this.entitiesAPI = (EntitiesAPI)api.requireAPI(EntitiesAPI.class);
        this.boxes = this.entitiesAPI.getBoxes();
        this.groupMembers = ((GroupAPI)api.requireAPI(GroupAPI.class)).getMembers();
        this.itemsAPI = (HeroItemsAPI)api.requireAPI(HeroItemsAPI.class);
        this.portalJumper = portalJumper;
        this.boosterAPI = (BoosterAPI)api.requireAPI(BoosterAPI.class);
        this.boosters = this.boosterAPI.getBoosters();
        this.backpageAPI = (BackpageAPI)api.requireAPI(BackpageAPI.class);
        this.settingsProxy = (SettingsProxy)api.requireInstance(SettingsProxy.class);
        this.main = main;
        this.gameResourcesAPI = (GameResourcesAPI)api.requireAPI(GameResourcesAPI.class);
        this.allShips = this.entitiesAPI.getPlayers();
        this.group = (GroupAPI)api.requireAPI(GroupAPI.class);
        this.conf = ConfigEntity.INSTANCE;
        this.changeMap = false;
        this.mainNPCKilled = false;
        this.mainNPCSpawned = false;
        this.dmgBeaconUsed = false;
        this.ablFirst = true;
        this.lastSpot = eu.darkbot.api.game.other.Location.of((double)0.0, (double)0.0);
        this.lastSpotTime = 0L;
        this.lastCloakPurchasedTime = 0L;
        this.deathCount = 0;
        this.lastDeathAmount = this.repairAPI.getDeathAmount();
        this.twoBLNextMainNPCSpawnTime = this.threeBLNextMainNPCSpawnTime = System.currentTimeMillis();
        this.oneBLNextMainNPCSpawnTime = this.threeBLNextMainNPCSpawnTime;
        this.currentMapMainNPCRespawnTime = 0L;
        this.threeBLBoxCount = 0;
        this.twoBLBoxCount = 0;
        this.oneBLBoxCount = 0;
        this.lastBoxMovementTimeout = 0L;
        this.serverRestart = false;
        this.serverRestartTimer = 0L;
    }

    public void beheSettings() {
        this.setNpc(this.conf.getOrCreateNpcInfo("\\\\ Mindfire Behemoth //"), -100, true, true);
        this.setNpc(this.conf.getOrCreateNpcInfo("\\\\ Attend IX //"), 0, true, true);
        this.setNpc(this.conf.getOrCreateNpcInfo("\\\\ Impulse II //"), 0, true, true);
        this.setNpc(this.conf.getOrCreateNpcInfo("\\\\ Invoke XVI //"), -50, true, true);
        this.setNpc(this.conf.getOrCreateNpcInfo("\\\\ Strokelight Barrage //"), -100, true, true);
        this.setNpc(this.conf.getOrCreateNpcInfo("-=[ Barrage Seeker Rocket ]=-"), -200, true, true);
    }

    public void boxSettings() {
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("SOLAR_CLASH"), true, 50, -100);
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("LUMINIUM"), true, 50, 0);
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("LUMINIUM_2"), true, 50, 0);
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("LUMINIUM_3"), true, 50, 0);
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("LUMINIUM_4"), true, 50, 0);
        this.createOrUpdateBox((BoxInfo)this.conf.getOrCreateBoxInfo("LUMINIUM_5"), true, 50, 0);
    }

    private void setNpc(NpcInfo info, int priority, boolean ownerShip, boolean attacked) {
        if (!(info.getShouldKill() || info.hasExtraFlag((Enum)NpcFlag.IGNORE_ATTACKED) || info.hasExtraFlag((Enum)NpcFlag.IGNORE_OWNERSHIP))) {
            info.set(Double.valueOf(550.0), Integer.valueOf(priority), Boolean.valueOf(true), null, null);
            if (ownerShip) {
                info.extra.set((NpcExtraFlag)NpcExtra.IGNORE_OWNERSHIP, true);
            }
            if (attacked) {
                info.extra.set((NpcExtraFlag)NpcExtra.IGNORE_ATTACKED, true);
            }
        }
    }

    private void createOrUpdateBox(BoxInfo info, boolean collect, int waitTime, int priority) {
        if (!info.shouldCollect() && info.getWaitTime() == 0 && info.getPriority() >= 0) {
            info.setShouldCollect(collect);
            info.setWaitTime(waitTime);
            info.setPriority(priority);
        }
    }

    @NotNull
    public PrioritizedSupplier<SelectableItem.Laser> getLaserSupplier() {
        return this;
    }

    public SelectableItem.Laser get() {
        Optional ammo;
        Npc target = (Npc)this.hero.getLocalTargetAs(Npc.class);
        if (this.attack.hasTarget() && this.attack.getTarget() instanceof Npc && (this.ablFirst && this.config.AMMO_SETTINGS.ABL_FIRST || this.ablStartTime > 0L && System.currentTimeMillis() - this.ablStartTime < 2000L) && this.ablFirst && (Objects.equals(Objects.requireNonNull(this.attack.getTarget()).getEntityInfo().getUsername(), "\\\\ Mindfire Behemoth //") || Objects.equals(Objects.requireNonNull(this.attack.getTarget()).getEntityInfo().getUsername(), "\\\\ Strokelight Barrage //") || Objects.equals(Objects.requireNonNull(this.attack.getTarget()).getEntityInfo().getUsername(), "\\\\ Invoke XVI //"))) {
            Optional ammo2;
            this.ablFirst = false;
            this.ablStartTime = System.currentTimeMillis();
            if (target != null && (ammo2 = target.getInfo().getAmmo()).isPresent()) {
                return (SelectableItem.Laser)ammo2.get();
            }
            Item i = this.itemsAPI.getItem((SelectableItem)SelectableItem.Laser.A_BL, new ItemFlag[0]).orElse(null);
            if (i == null) {
                i = this.itemsAPI.getItem(this.main.config.LOOT.AMMO_KEY);
            }
            return i != null ? (SelectableItem.Laser)i.getAs(SelectableItem.Laser.class) : null;
        }
        if (this.shouldRsb()) {
            return this.rsb != null ? (SelectableItem.Laser)this.rsb.getAs(SelectableItem.Laser.class) : null;
        }
        if (this.shouldSab()) {
            Item sab = this.itemsAPI.getItem(this.main.config.LOOT.SAB.KEY);
            return sab != null ? (SelectableItem.Laser)sab.getAs(SelectableItem.Laser.class) : null;
        }
        if (target != null && this.attack.hasTarget() && !(this.attack.getTarget() instanceof Player) && (ammo = target.getInfo().getAmmo()).isPresent()) {
            return (SelectableItem.Laser)ammo.get();
        }
        Item i = this.itemsAPI.getItem(this.main.config.LOOT.AMMO_KEY);
        if (i == null) {
            i = this.itemsAPI.getItem((SelectableItem)SelectableItem.Laser.UCB_100, new ItemFlag[0]).orElse(null);
        }
        return i != null ? (SelectableItem.Laser)i.getAs(SelectableItem.Laser.class) : null;
    }

    private boolean shouldRsb() {
        boolean isReady;
        if (!this.main.config.LOOT.RSB.ENABLED || this.main.config.LOOT.RSB.KEY == null || this.attack.hasTarget() && this.attack.getTarget() instanceof Npc && !this.attack.hasExtraFlag((Enum)NpcExtra.USE_RSB)) {
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

    private boolean shouldSab() {
        if (!this.main.config.LOOT.SAB.ENABLED || this.attack.hasExtraFlag((Enum)NpcExtra.NO_SAB)) {
            return false;
        }
        Config.Loot.Sab SAB = this.main.config.LOOT.SAB;
        return this.hero.getHealth().shieldPercent() <= SAB.PERCENT && this.attack.getTarget().getHealth().getShield() > SAB.NPC_AMOUNT && (SAB.CONDITION == null || SAB.CONDITION.get(this.main).toBoolean());
    }

    @Nullable
    public PrioritizedSupplier.Priority getPriority() {
        return PrioritizedSupplier.Priority.HIGHEST;
    }

    public void onTickModule() {
        this.assign();
    }

    protected void assign() {
        try {
            this.currentMapId = (Integer)this.workingMap.getValue();
            if (!this.mapIdToBLMap.containsKey(this.currentMapId)) {
                return;
            }
            if (this.portals.isEmpty()) {
                return;
            }
            this.boxesCheckList = this.boxes.stream().filter(box -> box.getInfo().shouldCollect() && box.isValid() && (this.LocationWithinBoundaries(box.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS * 2.0) || this.movement.isInPreferredZone((Locatable)box))).sorted(Comparator.comparing(arg_0 -> ((HeroAPI)this.hero).distanceTo(arg_0))).collect(Collectors.toList());
            this.boost = this.boosters.stream().filter(booster -> booster.getType() == BoosterAPI.Type.EXPERIENCE_POINTS).findFirst().orElse(null);
            if (this.currentMapId != 306 && this.currentMapId != 307 && this.currentMapId != 308) {
                if (this.hero.getEntityInfo().getFaction() == EntityInfo.Faction.MMO) {
                    this.workingMap.setValue(306);
                } else if (this.hero.getEntityInfo().getFaction() == EntityInfo.Faction.EIC) {
                    this.workingMap.setValue(307);
                } else if (this.hero.getEntityInfo().getFaction() == EntityInfo.Faction.VRU) {
                    this.workingMap.setValue(308);
                }
            }
            this.centerLoc = this.modeToBLMapLocation.get((Object)this.currentMode).get((Object)this.mapIdToBLMap.get(this.currentMapId));
            Box box2 = this.currentBox = this.boxesCheckList.isEmpty() ? null : this.boxesCheckList.get(0);
            if (this.boost != null && (int)this.boost.getAmount() <= this.config.COLLECT_SETTING.MIN_EXP_BOOST && this.boxesCheckList.stream().anyMatch(box -> !box.getTypeName().equals("SOLAR_CLASH"))) {
                this.currentBox = (Box)this.boxesCheckList.stream().filter(box -> !box.getTypeName().equals("SOLAR_CLASH")).collect(Collectors.toList()).get(0);
            }
            this.behemoth = null;
            this.strokeLight = null;
            this.invoke = null;
            this.steadfast = null;
            this.abide = null;
            this.rocket = null;
            this.attend = null;
            this.impulse = null;
            this.anyNPC = null;
            this.agressiveNpc = null;
            this.abideAndSteadfast = null;
            this.impulseAndAttend = null;
            block22: for (Npc npc : this.npcs) {
                String name = npc.getEntityInfo().getUsername();
                if (npc.isAttacking((Lockable)this.hero)) {
                    this.agressiveNpc = Utils.GetClosestNpc(this.hero, this.agressiveNpc, npc);
                }
                switch (name) {
                    case "\\\\ Mindfire Behemoth //": {
                        this.behemoth = Utils.GetClosestNpc(this.hero, this.behemoth, npc);
                        continue block22;
                    }
                    case "\\\\ Strokelight Barrage //": {
                        this.strokeLight = Utils.GetClosestNpc(this.hero, this.strokeLight, npc);
                        continue block22;
                    }
                    case "\\\\ Invoke XVI //": {
                        this.invoke = Utils.GetClosestNpc(this.hero, this.invoke, npc);
                        continue block22;
                    }
                    case "\\\\ Steadfast III //": {
                        this.steadfast = Utils.GetClosestNpc(this.hero, this.steadfast, npc);
                        this.abideAndSteadfast = Utils.GetClosestNpc(this.hero, this.steadfast, npc);
                        this.impulseAbideAndSteadfast = Utils.GetClosestNpc(this.hero, this.steadfast, npc);
                        this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
                        continue block22;
                    }
                    case "\\\\ Abide I //": {
                        this.abide = Utils.GetClosestNpc(this.hero, this.abide, npc);
                        this.abideAndSteadfast = Utils.GetClosestNpc(this.hero, this.abide, npc);
                        this.impulseAbideAndSteadfast = Utils.GetClosestNpc(this.hero, this.abide, npc);
                        this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
                        continue block22;
                    }
                    case "-=[ Barrage Seeker Rocket ]=-": {
                        this.rocket = Utils.GetClosestNpc(this.hero, this.rocket, npc);
                        this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
                        continue block22;
                    }
                    case "\\\\ Attend IX //": {
                        this.attend = Utils.GetClosestNpc(this.hero, this.attend, npc);
                        this.impulseAndAttend = Utils.GetClosestNpc(this.hero, this.attend, npc);
                        this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
                        continue block22;
                    }
                    case "\\\\ Impulse II //": {
                        this.impulse = Utils.GetClosestNpc(this.hero, this.impulse, npc);
                        this.impulseAndAttend = Utils.GetClosestNpc(this.hero, this.impulse, npc);
                        this.impulseAbideAndSteadfast = Utils.GetClosestNpc(this.hero, this.impulse, npc);
                        this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
                        continue block22;
                    }
                }
                this.anyNPC = Utils.GetClosestNpc(this.hero, this.anyNPC, npc);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public BLMaps getNextMap() {
        try {
            if (this.config.MAP_SETTINGS.ROTATION) {
                return this.nextMapRotationON.get(this.mapIdToBLMap.get(this.hero.getMap().getId()).getMapId());
            }
            return this.nextMapRotationOFF.get(this.mapIdToBLMap.get(this.hero.getMap().getId()).getMapId());
        }
        catch (NullPointerException exception) {
            return null;
        }
    }

    public BLMaps getPreviousMap() {
        if (this.config.MAP_SETTINGS.ROTATION) {
            return this.previousMapRotationON.get(this.mapIdToBLMap.get(this.hero.getMap().getId()).getMapId());
        }
        return this.previousMapRotationOFF.get(this.mapIdToBLMap.get(this.hero.getMap().getId()).getMapId());
    }

    public void buyCloak() throws IOException {
        if (!(this.hero.isInvisible() || System.currentTimeMillis() - this.lastCloakPurchasedTime < 10000L && (!this.config.CLOAK_SETTING.BUY_CLOAK_ON_MAIN_NPC_DEATH && !this.config.CLOAK_SETTING.BUY_CLOAK_ON_NPCS_CLEARED || !this.mainNPCKilled && this.mainNPCSpawned || System.currentTimeMillis() - this.lastCloakPurchasedTime < 6500L))) {
            Optional cloakOptional = this.itemsAPI.getItem((SelectableItem)SelectableItem.Cpu.CL04K, new ItemFlag[0]);
            Item cloak = null;
            if (cloakOptional.isPresent()) {
                cloak = (Item)cloakOptional.get();
            }
            if (cloak != null && !cloak.isSelected() && cloak.isAvailable()) {
                this.itemsAPI.useItem((SelectableItem)cloak, new ItemFlag[0]);
            } else {
                this.backpageAPI.postHttp("ajax/shop.php").setRawParam((Object)"action", (Object)"purchase").setRawParam((Object)"category", (Object)"special").setRawParam((Object)"itemId", (Object)"equipment_extra_cpu_cl04k-xs").setRawParam((Object)"amount", (Object)"1").setRawParam((Object)"level", (Object)"").setRawParam((Object)"selectedName", (Object)"").getContent();
            }
            this.lastCloakPurchasedTime = System.currentTimeMillis();
        }
    }

    public void SetCurrentMapPredictedSpawnTime() {
        switch (this.hero.getMap().getId()) {
            case 306: {
                this.currentMapMainNPCRespawnTime = this.oneBLNextMainNPCSpawnTime;
                long l = this.nextMapMainNPCRespawnTime = this.config.MAP_SETTINGS.ROTATION ? this.threeBLNextMainNPCSpawnTime : this.twoBLNextMainNPCSpawnTime;
                if (!this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) break;
                this.oneBLBoxCount = this.boxesCheckList.size();
                break;
            }
            case 307: {
                this.currentMapMainNPCRespawnTime = this.twoBLNextMainNPCSpawnTime;
                long l = this.nextMapMainNPCRespawnTime = this.config.MAP_SETTINGS.ROTATION ? this.oneBLNextMainNPCSpawnTime : this.threeBLNextMainNPCSpawnTime;
                if (!this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) break;
                this.twoBLBoxCount = this.boxesCheckList.size();
                break;
            }
            case 308: {
                this.currentMapMainNPCRespawnTime = this.threeBLNextMainNPCSpawnTime;
                long l = this.nextMapMainNPCRespawnTime = this.config.MAP_SETTINGS.ROTATION ? this.twoBLNextMainNPCSpawnTime : this.oneBLNextMainNPCSpawnTime;
                if (!this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) break;
                this.threeBLBoxCount = this.boxesCheckList.size();
            }
        }
    }

    protected boolean checkMap() {
        if (this.hero.getMap().getId() != 306 && this.hero.getMap().getId() != 307 && this.hero.getMap().getId() != 308) {
            System.out.println("Hero is outside of BL map. MapId: " + this.hero.getMap().getId() + " map name: " + this.hero.getMap().getName());
            if (!((Integer)this.workingMap.getValue()).equals(this.starSystem.getCurrentMap().getId()) && !this.portals.isEmpty()) {
                ((MapModule)this.bot.setModule((Module)new MapModule(this.api, true))).setTarget(this.starSystem.getOrCreateMap(((Integer)this.workingMap.getValue()).intValue()));
                return false;
            }
            return true;
        }
        return true;
    }

    public boolean canAttackNPC(Npc target) {
        return target != null && this.LocationWithinBoundaries(target.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && (this.currentMapMainNPCRespawnTime - System.currentTimeMillis() >= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L || this.currentMapMainNPCRespawnTime + 45000L < System.currentTimeMillis() || this.currentMapMainNPCRespawnTime != 0L && TimeUnit.MILLISECONDS.toSeconds(this.currentMapMainNPCRespawnTime - System.currentTimeMillis()) <= -25L);
    }

    public void HandleNPC(Npc mainNpc) throws IOException {
        if (mainNpc == null && this.hero.getMap().getId() != ((Integer)this.workingMap.getValue()).intValue()) {
            this.ChangeWorkmap();
            this.ablFirst = true;
            this.ablStartTime = 0L;
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_FIRST && this.canAttackEnemy()) {
            this.AttackEnemy();
        } else if (mainNpc != null) {
            this.mainNPCSpawned = true;
            this.AttackNPC(mainNpc);
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.shouldCollect()) {
            this.CollectBoxes();
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_AFTER_BIG_NPCS && this.canAttackEnemy()) {
            this.AttackEnemy();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.canAttackNPC(this.attend)) {
            if (!this.attack.hasTarget() || !this.LocationWithinBoundaries(this.attack.getTarget().getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.AttackNPC(this.attend);
            } else {
                this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
            }
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.config.CLEAR_SETTINGS.CLEAN_AGGRESSIVE_NPC && this.canAttackNPC(this.agressiveNpc) && !this.hero.isInvisible()) {
            if (!this.attack.hasTarget() || !this.LocationWithinBoundaries(this.attack.getTarget().getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.AttackNPC(this.agressiveNpc);
            } else {
                this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
            }
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.canAttackNPC(this.impulse)) {
            if (!this.attack.hasTarget() || !this.LocationWithinBoundaries(this.attack.getTarget().getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.AttackNPC(this.impulse);
            } else {
                this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
            }
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.canAttackNPC(this.anyNPC)) {
            if (!this.attack.hasTarget() || !this.LocationWithinBoundaries(this.attack.getTarget().getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.AttackNPC(this.anyNPC);
            } else {
                this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
            }
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_LAST && this.canAttackEnemy()) {
            this.AttackEnemy();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else {
            this.attack.setTarget(null);
            if (this.config.WAIT_SETTINGS.WAIT_FOR_GROUP && this.groupMembers.stream().anyMatch(groupMember -> groupMember.getMapId() != this.hero.getMap().getId())) {
                this.portals.stream().min(Comparator.comparing(portal -> portal.getLocationInfo().distanceTo((Locatable)this.hero))).ifPresent(closest -> {
                    if (closest.getLocationInfo().getCurrent().distanceTo((Locatable)this.hero) < 100.0) {
                        this.movement.stop(false);
                    } else if (this.isNotWaiting()) {
                        this.movement.moveTo((Locatable)closest);
                    }
                });
            } else {
                this.SetFormation();
                this.RoamToNPCSpot();
                this.BuyCloakIfUncloaked();
            }
        }
    }

    protected void BuyCloakIfUncloaked() throws IOException {
        if (this.config.CLOAK_SETTING.REBUY_CLOAK) {
            this.buyCloak();
        }
    }

    public void attackMainNPCFirst(Npc mainNpc) {
        if (mainNpc != null && mainNpc.isValid()) {
            this.attack.setTarget((Lockable)mainNpc);
            this.attack.tryLockTarget();
            if (this.hero.distanceTo((Locatable)mainNpc) > (double)((Integer)this.npcDistanceIgnore.getValue()).intValue()) {
                this.movement.moveTo((Locatable)mainNpc);
            }
        }
    }

    public boolean canAttackEnemy() {
        boolean canAttack = this.hero.getMap().isPvp() || this.portals.stream().noneMatch(p -> this.hero.distanceTo((Locatable)p) < this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS);
        boolean hasBlacklistedEnemy = this.allShips.stream().filter(Entity::isValid).filter(x -> x.getEntityInfo().isEnemy()).map(x -> (PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId())).filter(Objects::nonNull).anyMatch(tag -> this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG.hasTag((eu.darkbot.api.config.types.PlayerInfo)tag));
        this.closestEnemy = this.allShips.stream().filter(Entity::isValid).filter(x -> !(!x.getEntityInfo().isEnemy() || !canAttack && !x.isAttacking() || x.hasEffect(290) || x.hasEffect(325) || !x.hasEffect(341) && this.backpageAPI.getInstanceURI().getRawPath().contains("gbl1"))).filter(x -> !this.isInGroup(x.getId()) && this.portals.stream().noneMatch(p -> x.distanceTo((Locatable)p) < this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS) && this.movement.canMove((Locatable)x) && x.getLocationInfo().distanceTo((Locatable)this.hero) <= this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS).filter(x -> {
            boolean isBlacklisted;
            eu.darkbot.api.config.types.PlayerInfo tag = (eu.darkbot.api.config.types.PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId());
            boolean whiteListEnabled = this.config.PVP_HELPER_SETTINGS.WHITELIST_TAG != null;
            boolean blackListEnabled = this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG != null;
            boolean isWhitelisted = whiteListEnabled && this.config.PVP_HELPER_SETTINGS.WHITELIST_TAG.hasTag(tag);
            boolean bl = isBlacklisted = blackListEnabled && this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG.hasTag(tag);
            if (!whiteListEnabled && !blackListEnabled) {
                return true;
            }
            if (whiteListEnabled && !blackListEnabled) {
                return !isWhitelisted;
            }
            if (!whiteListEnabled) {
                return isBlacklisted;
            }
            return isBlacklisted && !isWhitelisted || !hasBlacklistedEnemy && this.config.PVP_HELPER_SETTINGS.FOCUS_OTHERS_ON_EMPTY_BLACKLIST;
        }).filter(x -> !this.config.PVP_HELPER_SETTINGS.ONLY_RESPOND || x.isAttacking((Lockable)this.hero)).min(Comparator.comparingDouble((Player s) -> s.getLocationInfo().distanceTo((Locatable)this.hero)).thenComparingDouble(s -> s.getHealth().hpPercent())).orElse(null);
        return this.closestEnemy != null;
    }

    public void MapChangeStuff(Npc npcToClear, long timeToAdd) throws IOException {
        if (System.currentTimeMillis() - this.mainNPCDeathTime > 3500L) {
            if (this.config.MAP_SETTINGS.ENABLE_DEATH_COUNT && this.deathCount == this.config.MAP_SETTINGS.DEATH_COUNT) {
                this.changeMap = true;
                this.PredictNextSpawnTime(timeToAdd);
            } else if (this.mainNPCKilled && npcToClear == null) {
                if (this.config.CLOAK_SETTING.BUY_CLOAK_ON_NPCS_CLEARED) {
                    this.buyCloak();
                }
                this.changeMap = true;
            }
        }
    }

    public boolean clearAttendOnCurrentMap() {
        return (this.hero.getMap().getId() == 306 || this.hero.getMap().getId() == 307 || this.hero.getMap().getId() == 308) && this.config.CLEAR_SETTINGS.MAPS_TO_CLEAN.contains((Object)this.mapIdToBLMap.get(this.hero.getMap().getId()));
    }

    public void ServerRestartStuff() {
        if (this.config.SERVER_RESTART.FORCE_COLLECT_SERVER_RESTART && this.serverRestart) {
            if (this.oneBLBoxCount >= this.config.SERVER_RESTART.MINIMUM_BOX_AMOUNT) {
                this.changeMap = true;
            }
            if (this.twoBLBoxCount >= this.config.SERVER_RESTART.MINIMUM_BOX_AMOUNT) {
                this.changeMap = true;
            }
            if (this.threeBLBoxCount >= this.config.SERVER_RESTART.MINIMUM_BOX_AMOUNT) {
                this.changeMap = true;
            }
        }
    }

    public void NPCSpawnStuff(Npc mainNpc, long timeToAdd) throws IOException {
        if (this.mainNPCSpawned && mainNpc == null) {
            this.mainNPCKilled = true;
            this.mainNPCSpawned = false;
            this.ablFirst = true;
            this.ablStartTime = 0L;
            this.mainNPCDeathTime = System.currentTimeMillis();
            if (this.config.CLOAK_SETTING.BUY_CLOAK_ON_MAIN_NPC_DEATH) {
                this.buyCloak();
            }
            this.PredictNextSpawnTime(timeToAdd);
        }
    }

    public void ChangeMapStuff() {
        if (this.changeMap && !this.shouldCollect()) {
            if (this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.mapIdToBLMap.get(this.GetMapByID(this.getNextMap().getMapId()).getId()))) {
                this.SetWorkmap(this.GetMapByID(this.getNextMap().getMapId()));
            } else if (this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.mapIdToBLMap.get(this.GetMapByID(this.getPreviousMap().getMapId()).getId()))) {
                this.SetWorkmap(this.GetMapByID(this.getPreviousMap().getMapId()));
            }
            this.changeMap = false;
            this.deathCount = 0;
            this.mainNPCKilled = false;
            this.mainNPCSpawned = false;
            this.dmgBeaconUsed = false;
            this.ablFirst = true;
            this.ablStartTime = 0L;
        }
    }

    public void DeathCountStuff() {
        if (this.repairAPI.getDeathAmount() != this.lastDeathAmount) {
            this.lastDeathAmount = this.repairAPI.getDeathAmount();
            ++this.deathCount;
        }
    }

    @EventHandler
    public void onLogReceived(GameLogAPI.LogMessageEvent ev) {
        if (this.gameResourcesAPI.findTranslation("server_restart_n_minutes").isEmpty() && this.gameResourcesAPI.findTranslation("server_restart_n_seconds").isEmpty() && this.gameResourcesAPI.findTranslation("server_restarting").isEmpty() && this.gameResourcesAPI.findTranslation("server_shutdown_n_minutes").isEmpty() && this.gameResourcesAPI.findTranslation("server_shutdown_n_seconds").isEmpty() && this.gameResourcesAPI.findTranslation("log_boot_message").isEmpty()) {
            this.pastLogMessages.add(ev);
            return;
        }
        String log = ev.getMessage();
        if (this.config.SERVER_RESTART.FORCE_COLLECT_SERVER_RESTART && this.serverRestart && System.currentTimeMillis() >= this.serverRestartTimer && this.gameResourcesAPI.findTranslation("log_boot_message").orElse("").contains(log)) {
            System.out.println("Behemoth Module, Server Restart Done");
            this.serverRestart = false;
            this.serverRestartTimer = 0L;
            this.twoBLNextMainNPCSpawnTime = this.threeBLNextMainNPCSpawnTime = System.currentTimeMillis();
            this.oneBLNextMainNPCSpawnTime = this.threeBLNextMainNPCSpawnTime;
            this.threeBLBoxCount = 0;
            this.twoBLBoxCount = 0;
            this.oneBLBoxCount = 0;
            this.changeMap = false;
            this.deathCount = 0;
            this.mainNPCKilled = false;
            this.mainNPCSpawned = false;
            this.dmgBeaconUsed = false;
            this.ablFirst = true;
            this.ablStartTime = 0L;
        }
        if (this.config.SERVER_RESTART.FORCE_COLLECT_SERVER_RESTART && !this.serverRestart) {
            for (RestartOptions restart : RestartOptions.values()) {
                if (!restart.matches(log, this.gameResourcesAPI)) continue;
                System.out.println("Behemoth Module, Server Restart Detected force collecting: " + log);
                int min = restart == RestartOptions.RESTART_MIN || restart == RestartOptions.SHUTDOWN_MIN ? restart.getTime() : 0;
                int sec = restart == RestartOptions.RESTART_SEC || restart == RestartOptions.SHUTDOWN_SEC ? restart.getTime() : 0;
                this.serverRestart = true;
                this.serverRestartTimer = System.currentTimeMillis() + (long)min * 60000L + (long)sec * 1000L;
                break;
            }
        }
    }

    public void CustomEventStuff() {
        if (this.currentMapMainNPCRespawnTime > 0L && this.currentMapMainNPCRespawnTime - System.currentTimeMillis() <= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L) {
            this.DmgBeacon();
        }
        if ((this.behemoth != null || this.invoke != null || this.strokeLight != null) && this.config.CUSTOM_EVENT.CUSTOM_EVENTS.contains((Object)CustomEvent.SPEARHEAD) && (this.attack.isAttacking() || this.hero.isAttacking())) {
            this.itemsAPI.useItem((SelectableItem)SelectableItem.Ability.SPEARHEAD_TARGET_MARKER, new ItemFlag[0]);
        }
    }

    public void PetStuff() {
        if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) || this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo())) {
            if (!this.pet.isEnabled()) {
                this.pet.setEnabled(true);
            }
            if (this.pet.isValid()) {
                Npc target;
                if (this.config.PET_SETTINGS.COMBO_REPAIR && this.pet.hasGear(PetGear.COMBO_REPAIR) && !this.pet.hasEffect(326) && this.hero.getHealth().hpPercent() < 1.0 && this.hero.getHealth().hpIncreasedIn(1000) && ((target = (Npc)this.hero.getLocalTargetAs(Npc.class)) == null || this.hero.distanceTo((Locatable)target) >= 850.0)) {
                    try {
                        this.pet.setGear(PetGear.COMBO_REPAIR);
                    }
                    catch (ItemNotEquippedException e) {
                        System.out.println("Unable to set pet to Combo Repair");
                    }
                    return;
                }
                if (this.pet.hasEffect(326) && this.pet.hasGear(PetGear.HP_LINK)) {
                    try {
                        this.pet.setGear(PetGear.HP_LINK);
                    }
                    catch (ItemNotEquippedException e) {
                        System.out.println("Unable to set pet to Pet HP Link");
                    }
                } else if (this.config.PET_SETTINGS.PET_LINK_HP > 0.0 && this.pet.hasGear(PetGear.REPAIR) && !this.pet.hasEffect(326) && this.pet.getHealth().hpPercent() < 1.0) {
                    try {
                        this.pet.setGear(PetGear.REPAIR);
                    }
                    catch (ItemNotEquippedException e) {
                        System.out.println("Unable to set pet to Pet Repair");
                    }
                } else if (this.config.PET_SETTINGS.PET_LINK_HP > 0.0 && this.pet.hasGear(PetGear.HP_LINK) && this.hero.isInMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG) && !this.pet.hasCooldown(PetGear.Cooldown.HP_LINK) && !this.pet.hasEffect(326) && this.hero.getHealth().hpPercent() < this.config.PET_SETTINGS.PET_LINK_HP) {
                    try {
                        this.itemsAPI.useItem((SelectableItem)SelectableItem.Pet.G_HPL1, new ItemFlag[0]);
                        this.pet.setGear(PetGear.HP_LINK);
                    }
                    catch (ItemNotEquippedException e) {
                        System.out.println("Unable to set pet to Pet HP Link");
                    }
                } else if (!this.pet.hasEffect(326)) {
                    if (this.config.PET_SETTINGS.PET_ANTI_PUSH) {
                        if (this.attack.hasTarget() && (this.attack.getTarget() == this.behemoth || this.attack.getTarget() == this.strokeLight)) {
                            try {
                                this.pet.setGear(PetGear.GUARD);
                            }
                            catch (ItemNotEquippedException e) {
                                System.out.println("Unable to set pet to Guard mode");
                            }
                        } else if (this.attack.hasTarget() && this.attack.getTarget() == this.invoke) {
                            try {
                                this.pet.setGear(PetGear.ENEMY_LOCATOR);
                            }
                            catch (ItemNotEquippedException e) {
                                System.out.println("Unable to set pet to Guard mode");
                            }
                        } else {
                            try {
                                this.pet.setGear(PetGear.PASSIVE);
                            }
                            catch (ItemNotEquippedException e) {
                                System.out.println("Unable to set pet to Guard mode");
                            }
                        }
                    } else if (this.currentMode == Mode.INVOKE) {
                        try {
                            this.pet.setGear(PetGear.ENEMY_LOCATOR);
                        }
                        catch (ItemNotEquippedException e) {
                            System.out.println("Unable to set pet to enemy locator mode");
                        }
                    } else {
                        try {
                            this.pet.setGear(PetGear.GUARD);
                        }
                        catch (ItemNotEquippedException e) {
                            System.out.println("Unable to set pet to Guard mode");
                        }
                    }
                }
            }
        } else {
            if (this.pet.isEnabled() && this.pet.isValid()) {
                try {
                    this.pet.setGear(PetGear.PASSIVE);
                }
                catch (ItemNotEquippedException e) {
                    System.out.println("Unable to Set Pet to Passive");
                }
            }
            this.pet.setEnabled(false);
        }
    }

    public void PredictNextSpawnTime(long timeToAdd) {
        switch ((Integer)this.workingMap.getValue()) {
            case 306: {
                this.oneBLNextMainNPCSpawnTime = System.currentTimeMillis() + timeToAdd;
                break;
            }
            case 307: {
                this.twoBLNextMainNPCSpawnTime = System.currentTimeMillis() + timeToAdd;
                break;
            }
            case 308: {
                this.threeBLNextMainNPCSpawnTime = System.currentTimeMillis() + timeToAdd;
            }
        }
    }

    public void DmgBeacon() {
        if (this.config.CUSTOM_EVENT.MAPS_TO_USE_CUSTOM_EVENTS.contains((Object)this.mapIdToBLMap.get(this.hero.getMap().getId())) && this.config.CUSTOM_EVENT.CUSTOM_EVENTS.contains((Object)CustomEvent.DMG_BEACON) && !this.pet.hasCooldown(PetGear.Cooldown.BEACON_COMBAT) && !this.dmgBeaconUsed) {
            if (this.dmg_beacon == null) {
                System.out.println("Assigning dmg beacon");
                this.dmg_beacon = this.itemsAPI.getItem((SelectableItem)SelectableItem.Pet.G_BC1, new ItemFlag[0]).orElse(null);
            }
            if (this.dmg_beacon != null) {
                this.itemsAPI.useItem((SelectableItem)SelectableItem.Pet.G_BC1, new ItemFlag[0]);
                this.dmgBeaconUsed = true;
            }
            try {
                if (!this.dmgBeaconUsed) {
                    this.pet.setGear(PetGear.BEACON_COMBAT);
                    this.dmgBeaconUsed = true;
                }
            }
            catch (ItemNotEquippedException e) {
                System.out.println("Unable to set pet to Damage beacon");
            }
        }
    }

    public void AttackNPC(Npc npc) {
        if (npc == null || !npc.isValid() || !this.shouldKill(npc)) {
            this.RoamToNPCSpot();
            return;
        }
        this.attack.setTarget((Lockable)Objects.requireNonNullElse(this.rocket, npc));
        this.attack.tryLockAndAttack();
        if (this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo()) || this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
            this.moveToAnSafePosition();
        } else {
            this.ignoreInvalidTarget();
            this.RoamToNPCSpot();
        }
        this.SetFormation();
    }

    public boolean isInGroup(int id) {
        if (this.group.hasGroup()) {
            return this.group.getMembers().stream().anyMatch(gm -> gm.getId() == id);
        }
        return false;
    }

    public boolean FindEnemy() {
        boolean canAttack = this.hero.getMap().isPvp() || this.portals.stream().noneMatch(p -> this.hero.distanceTo((Locatable)p) < this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS);
        Lockable currentTarget = this.attack.hasTarget() ? this.attack.getTarget() : null;
        if (canAttack && currentTarget instanceof Player && currentTarget.isValid() && this.hero.getLocalTarget() != null && currentTarget.distanceTo((Locatable)this.hero) <= (double)((Integer)this.npcDistanceIgnore.getValue()).intValue() && this.portals.stream().noneMatch(p -> currentTarget.distanceTo((Locatable)p) < this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS)) {
            return true;
        }
        if (currentTarget != null && (!currentTarget.isValid() || currentTarget.getHealth().hpPercent() <= 0.0)) {
            this.attack.setTarget(null);
        }
        if (this.lastCheckPvp < System.currentTimeMillis()) {
            if (this.closestEnemy == null) {
                boolean hasBlacklistedEnemy = this.allShips.stream().filter(Entity::isValid).filter(x -> x.getEntityInfo().isEnemy()).map(x -> (PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId())).filter(Objects::nonNull).anyMatch(tag -> this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG.hasTag((eu.darkbot.api.config.types.PlayerInfo)tag));
                Optional<? extends Player> player = this.allShips.stream().filter(Entity::isValid).filter(x -> !(!x.getEntityInfo().isEnemy() || !canAttack && !x.isAttacking() || x.hasEffect(290) || x.hasEffect(325) || !x.hasEffect(341) && this.backpageAPI.getInstanceURI().getRawPath().contains("gbl1"))).filter(x -> !this.isInGroup(x.getId()) && this.movement.canMove((Locatable)x) && x.getLocationInfo().distanceTo((Locatable)this.hero) <= this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS).filter(x -> {
                    boolean isBlacklisted;
                    eu.darkbot.api.config.types.PlayerInfo tag = (eu.darkbot.api.config.types.PlayerInfo)this.main.config.PLAYER_INFOS.get(x.getId());
                    boolean whiteListEnabled = this.config.PVP_HELPER_SETTINGS.WHITELIST_TAG != null;
                    boolean blackListEnabled = this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG != null;
                    boolean isWhitelisted = whiteListEnabled && this.config.PVP_HELPER_SETTINGS.WHITELIST_TAG.hasTag(tag);
                    boolean bl = isBlacklisted = blackListEnabled && this.config.PVP_HELPER_SETTINGS.BLACKLIST_TAG.hasTag(tag);
                    if (!whiteListEnabled && !blackListEnabled) {
                        return true;
                    }
                    if (whiteListEnabled && !blackListEnabled) {
                        return !isWhitelisted;
                    }
                    if (!whiteListEnabled) {
                        return isBlacklisted;
                    }
                    return isBlacklisted && !isWhitelisted || !hasBlacklistedEnemy && this.config.PVP_HELPER_SETTINGS.FOCUS_OTHERS_ON_EMPTY_BLACKLIST;
                }).filter(x -> !this.config.PVP_HELPER_SETTINGS.ONLY_RESPOND || x.isAttacking((Lockable)this.hero)).min(Comparator.comparingDouble((Player s) -> s.getLocationInfo().distanceTo((Locatable)this.hero)).thenComparingDouble(s -> s.getHealth().hpPercent()));
                if (player.isPresent()) {
                    this.attack.setTarget((Lockable)player.get());
                    this.lastCheckPvp = System.currentTimeMillis() + 500L;
                    return true;
                }
                this.lastCheckPvp = System.currentTimeMillis() + 150L;
                return false;
            }
            this.attack.setTarget((Lockable)this.closestEnemy);
            this.lastCheckPvp = System.currentTimeMillis() + 500L;
            return true;
        }
        return canAttack && this.attack.hasTarget();
    }

    public void AttackEnemy() {
        if (this.FindEnemy()) {
            if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, ((Integer)this.npcDistanceIgnore.getValue()).doubleValue())) {
                this.moveToAnSafePositionPvP();
            } else {
                this.RoamToNPCSpot();
            }
            this.ignoreInvalidPVPTarget();
            this.attack.tryLockAndAttack();
        }
    }

    protected void moveToAnSafePositionPvP() {
        if (this.attack.hasTarget()) {
            double angleDiff;
            Lockable target = this.attack.getTarget();
            eu.darkbot.api.game.other.Location direction = this.movement.getDestination();
            eu.darkbot.api.game.other.Location targetLoc = target.getLocationInfo().destinationInTime(250L);
            double distance = this.hero.distanceTo((Locatable)this.attack.getTarget());
            double angle = targetLoc.angleTo((Locatable)this.hero);
            double radius = this.config.PVP_HELPER_SETTINGS.ENEMY_RADIUS;
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
                angleDiff = Math.max((double)this.hero.getSpeed() * 0.625 + Math.max(200.0, speed) * 0.625 - this.hero.distanceTo((Locatable)eu.darkbot.api.game.other.Location.of((Locatable)targetLoc, (double)angle, (double)radius)), 0.0) / radius;
            }
            direction = this.getBestPVPDir((Locatable)targetLoc, angle, angleDiff, distance);
            this.searchLoc(direction, targetLoc, angle, distance);
            this.setFormation((Locatable)direction);
            this.movement.moveTo((Locatable)direction);
        }
    }

    protected eu.darkbot.api.game.other.Location getBestPVPDir(Locatable targetLoc, double angle, double angleDiff, double distance) {
        int maxCircleIterations = (Integer)this.maxCircleIterations.getValue();
        int iteration = 1;
        double forwardScore = 0.0;
        double backScore = 0.0;
        while ((forwardScore += this.pvpScore(Locatable.of((Locatable)targetLoc, (double)(angle + angleDiff * (double)iteration), (double)distance))) < 0.0 == (backScore += this.pvpScore(Locatable.of((Locatable)targetLoc, (double)(angle - angleDiff * (double)iteration), (double)distance))) < 0.0 && !(Math.abs(forwardScore - backScore) > 300.0) && iteration++ < maxCircleIterations) {
        }
        if (iteration <= maxCircleIterations) {
            this.backwards = backScore > forwardScore;
        }
        return eu.darkbot.api.game.other.Location.of((Locatable)targetLoc, (double)(angle + angleDiff * (double)(this.backwards ? -1 : 1)), (double)distance);
    }

    protected double pvpScore(Locatable loc) {
        return (double)(this.movement.canMove(loc) ? 0 : -1000) - this.allShips.stream().filter(n -> this.attack.getTarget() != n).mapToDouble(n -> Math.max(0.0, this.config.PVP_HELPER_SETTINGS.ENEMY_RADIUS - n.distanceTo(loc))).sum();
    }

    protected int speed(Lockable target) {
        return target instanceof Movable ? ((Movable)target).getSpeed() : 0;
    }

    protected void searchLoc(eu.darkbot.api.game.other.Location direction, eu.darkbot.api.game.other.Location targetLoc, double angle, double distance) {
        while (!this.movement.canMove((Locatable)direction) && distance < 10000.0) {
            direction.toAngle((Locatable)targetLoc, angle += this.backwards ? -0.3 : 0.3, distance += 2.0);
        }
        if (distance >= 10000.0) {
            direction.toAngle((Locatable)targetLoc, angle, 500.0);
        }
    }

    public void setFormation(Locatable direction) {
        if (this.attack.hasTarget() && this.attack.getTarget() instanceof Player && this.attack.getTarget().isValid()) {
            if (this.attack.getTarget().getHealth().hpPercent() < 0.25 && this.hero.distanceTo(direction) > this.config.PVP_HELPER_SETTINGS.ENEMY_RADIUS * 2.0) {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.RUN_CONFIG);
            } else if (this.hero.distanceTo(direction) > this.config.PVP_HELPER_SETTINGS.ENEMY_RADIUS * 3.0) {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
            } else {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
            }
        } else {
            this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
        }
    }

    protected void ignoreInvalidPVPTarget() {
        Lockable target = this.attack.getTarget();
        double closestDist = this.movement.getClosestDistance((Locatable)this.attack.getTarget());
        if (target.hasEffect(325)) {
            this.attack.stopAttack();
            this.attack.setBlacklisted(1000L);
            this.hero.setLocalTarget(null);
        } else if (!Objects.equals(this.hero.getTarget(), this.attack.getTarget())) {
            if (closestDist > this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS) {
                this.attack.setBlacklisted(1000L);
                this.hero.setLocalTarget((Lockable)null);
            }
        } else if (this.shouldIgnorePVP(closestDist, target)) {
            this.attack.setBlacklisted(5000L);
            this.hero.setLocalTarget((Lockable)null);
        }
    }

    protected boolean shouldIgnorePVP(double closestDist, Lockable target) {
        if (!this.attack.isBugged() && !(this.hero.distanceTo((Locatable)target) > (double)((Integer)this.npcDistanceIgnore.getValue()).intValue())) {
            Health health = target.getHealth();
            if (closestDist > this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS && health.hpPercent() > 0.9) {
                return true;
            }
            return closestDist > this.config.PVP_HELPER_SETTINGS.ATTACK_RADIUS && !target.isMoving() && (target.getHealth().shieldIncreasedIn(1000) || target.getHealth().shieldPercent() > 0.99);
        }
        return true;
    }

    protected double getRadius(Lockable target) {
        if (!(target instanceof Npc)) {
            return this.attack.modifyRadius(this.config.PVP_HELPER_SETTINGS.ENEMY_RADIUS);
        }
        return this.attack.modifyRadius(((Npc)target).getInfo().getRadius());
    }

    public void CollectBoxes() {
        if (this.currentBox != null) {
            this.SetFormation();
            double distance = this.hero.distanceTo((Locatable)this.currentBox);
            if (distance < 700.0 && this.isNotWaiting()) {
                if (this.hero.isInMode((ShipMode)this.config.COLLECT_SETTING.COLLECT_CONFIG)) {
                    if (!this.currentBox.tryCollect()) {
                        return;
                    }
                    this.waitingUntil = System.currentTimeMillis() + (long)this.currentBox.getInfo().getWaitTime() + (long)Math.min(1000, this.currentBox.getRetries() * 100) + this.hero.timeTo(distance) + 30L;
                }
            } else {
                this.movement.moveTo((Locatable)this.currentBox);
            }
        }
    }

    protected boolean isNotWaiting() {
        if (this.currentBox != null && this.currentBox.isValid()) {
            return System.currentTimeMillis() > this.waitingUntil;
        }
        this.waitingUntil = 0L;
        return true;
    }

    public void ChangeWorkmap() {
        Portal nextPortal = this.starSystem.findNext(this.GetMapByID((Integer)this.workingMap.getValue()));
        if (nextPortal != null && !nextPortal.isJumping()) {
            this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.RUN_CONFIG);
            this.portalJumper.travelAndJump(nextPortal);
        }
    }

    public void SetWorkmap(GameMap map) {
        this.workingMap.setValue(map.getId());
    }

    public GameMap GetMapByID(Integer mapId) {
        return this.starSystem.getOrCreateMap(mapId.intValue());
    }

    public eu.darkbot.api.game.other.Location randomLocationOnPreferredZone() {
        ZoneInfo area = this.main.mapManager.preferred;
        boolean sequential = this.main.config.GENERAL.ROAMING.SEQUENTIAL;
        List zones = area == null ? Collections.emptyList() : (sequential ? area.getSortedZones() : area.getZones());
        boolean changed = !zones.equals(this.lastZones);
        this.lastZones = zones;
        if (this.main.config.GENERAL.ROAMING.KEEP && !changed && this.lastRandomMove != null) {
            return this.lastRandomMove;
        }
        if (changed && !this.lastZones.isEmpty()) {
            Location search = this.lastRandomMove != null ? this.lastRandomMove : this.movingTo();
            ZoneInfo.Zone closest = this.lastZones.stream().min(Comparator.comparingDouble(zone -> zone.innerPoint(0.5, 0.5, (double)MapManager.internalWidth, (double)MapManager.internalHeight).distance(search))).orElse(null);
            this.lastZoneIdx = this.lastZones.indexOf(closest);
        }
        if (this.lastZones.isEmpty()) {
            this.lastRandomMove = new Location(Math.random() * (double)MapManager.internalWidth, Math.random() * (double)MapManager.internalHeight);
        } else {
            int n;
            if (this.lastZoneIdx >= this.lastZones.size()) {
                this.lastZoneIdx = 0;
            }
            if (sequential) {
                int n2 = this.lastZoneIdx;
                n = n2;
                this.lastZoneIdx = n2 + 1;
            } else {
                n = new Random().nextInt(zones.size());
            }
            ZoneInfo.Zone zone2 = this.lastZones.get(n);
            this.lastRandomMove = zone2.innerPoint(Math.random(), Math.random(), (double)MapManager.internalWidth, (double)MapManager.internalHeight);
        }
        return this.lastRandomMove;
    }

    public Location movingTo() {
        return this.hero.getDestination().isPresent() ? (Location)this.hero.getLocationInfo().copy() : (Location)this.hero.getDestination().get();
    }

    public eu.darkbot.api.game.other.Location DetermineLocationToMoveTo(double radius) {
        Random rand = new Random();
        long currentSpawnSeconds = TimeUnit.MILLISECONDS.toSeconds(this.currentMapMainNPCRespawnTime - System.currentTimeMillis());
        if (this.config.WAIT_SETTINGS.WAIT_ON_PORTAL && !this.portals.isEmpty() && currentSpawnSeconds >= (long)this.config.WAIT_SETTINGS.TIME_TO_SPAWN && this.config.WAIT_SETTINGS.TIME_TO_SPAWN > 0 && currentSpawnSeconds > 0L) {
            return this.portals.stream().min(Comparator.comparing(p -> p.getLocationInfo().distanceTo((Locatable)this.hero))).get().getLocationInfo().getCurrent();
        }
        if (this.config.WAIT_SETTINGS.WAIT_ON_PREFERRED_ZONE && currentSpawnSeconds >= (long)this.config.WAIT_SETTINGS.TIME_TO_SPAWN && this.config.WAIT_SETTINGS.TIME_TO_SPAWN > 0 && currentSpawnSeconds > 0L) {
            return this.randomLocationOnPreferredZone();
        }
        if (this.centerLoc != null) {
            return eu.darkbot.api.game.other.Location.of((double)(this.centerLoc.getX() - radius / 2.0 + rand.nextDouble() * radius), (double)(this.centerLoc.getY() - radius / 2.0 + rand.nextDouble() * radius));
        }
        return this.randomLocationOnPreferredZone();
    }

    public boolean LocationWithinBoundaries(LocationInfo targetLocation, boolean npc, double additionalRadius) {
        if (this.centerLoc == null) {
            return false;
        }
        double radius = npc ? additionalRadius : 50.0;
        return targetLocation.getCurrent().distanceTo((Locatable)this.centerLoc) <= radius || this.movement.isInPreferredZone((Locatable)targetLocation);
    }

    public void RoamToNPCSpot() {
        if (this.config.MAP_SETTINGS.SAFETY && (this.safety.state() == SafetyFinder.Escaping.REFRESH || this.safety.state() == SafetyFinder.Escaping.REPAIR)) {
            return;
        }
        if (System.currentTimeMillis() - this.lastMoveToSpot <= (long)(1000 + new Random().nextInt(1000))) {
            return;
        }
        if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && System.currentTimeMillis() >= this.lastSpotTime + 30000L && this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) <= 20.0 && this.isNotWaiting() || this.currentMapMainNPCRespawnTime - System.currentTimeMillis() <= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L && this.currentMode != Mode.INVOKE || this.config.WAIT_SETTINGS.ROAM_ON_SPOT) {
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo(this.config.MAP_SETTINGS.NPC_RADIUS));
            this.lastMoveToSpot = System.currentTimeMillis();
        } else if (!this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && this.isNotWaiting()) {
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo(this.config.MAP_SETTINGS.NPC_RADIUS));
            this.lastMoveToSpot = System.currentTimeMillis();
        }
        if (this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) > 1.0) {
            this.lastSpot = this.movement.getDestination();
            this.lastSpotTime = System.currentTimeMillis();
            this.lastMoveToSpot = System.currentTimeMillis();
        }
    }

    protected boolean isForceOver() {
        if (this.hero.isInMode((ShipMode)this.config.COLLECT_SETTING.COLLECT_CONFIG) && !this.force && this.forceTime == 0L) {
            return true;
        }
        return System.currentTimeMillis() - this.forceTime >= 0L;
    }

    public void setConfig(Locatable direction) {
        this.SetFormation();
    }

    public void SetFormation() {
        if (this.config.COLLECT_SETTING.COLLECT && this.shouldCollect()) {
            this.hero.setMode((ShipMode)this.config.COLLECT_SETTING.COLLECT_CONFIG);
        } else if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && (this.currentMapMainNPCRespawnTime - System.currentTimeMillis() <= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L || this.attack.hasTarget() || this.hero.getLocalTarget() != null && this.hero.getLocalTarget().isValid() && TimeUnit.MILLISECONDS.toSeconds(this.currentMapMainNPCRespawnTime - System.currentTimeMillis()) >= 0L)) {
            this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
        } else if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
            this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
        } else {
            this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
        }
    }

    public boolean shouldCollect() {
        return this.config.COLLECT_SETTING.COLLECT && (this.movement.isInPreferredZone((Locatable)this.hero) || this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) && this.behemoth == null && this.invoke == null && this.strokeLight == null && !this.boxesCheckList.isEmpty() && (!this.currentBox.getTypeName().equals("SOLAR_CLASH") || this.config.COLLECT_SETTING.MIN_EXP_BOOST == 0 || this.boost != null && (int)this.boost.getAmount() >= this.config.COLLECT_SETTING.MIN_EXP_BOOST);
    }

    protected String GetStatusMessage() {
        try {
            String workMap = this.mapIdToBLMap.get(this.hero.getMap().getId()).getName();
            String nextMap = "";
            nextMap = this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.getNextMap()) ? this.mapIdToBLMap.get(this.getNextMap().getMapId()).getName() : (this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.getPreviousMap()) ? this.mapIdToBLMap.get(this.getPreviousMap().getMapId()).getName() : this.mapIdToBLMap.get(this.hero.getMap().getId()).getName());
            long one = this.oneBLNextMainNPCSpawnTime - System.currentTimeMillis();
            long two = this.twoBLNextMainNPCSpawnTime - System.currentTimeMillis();
            long three = this.threeBLNextMainNPCSpawnTime - System.currentTimeMillis();
            String output = ". Cur: " + workMap + ". Nxt:" + nextMap + ". Box:" + this.oneBLBoxCount + ":" + this.twoBLBoxCount + ":" + this.threeBLBoxCount + ". Spn:" + TimeUnit.MILLISECONDS.toSeconds(one) + ":" + TimeUnit.MILLISECONDS.toSeconds(two) + ":" + TimeUnit.MILLISECONDS.toSeconds(three);
            if (this.config.MAP_SETTINGS.ROTATION) {
                return "3-2-1" + output;
            }
            return "1-2-3" + output;
        }
        catch (Exception ignored) {
            return "Unable to get status message";
        }
    }

    public String getStatus() {
        return this.GetStatusMessage();
    }

    public boolean canRefresh() {
        long currentSpawnSeconds = TimeUnit.MILLISECONDS.toSeconds(this.currentMapMainNPCRespawnTime - System.currentTimeMillis());
        long nextSpawnSeconds = TimeUnit.MILLISECONDS.toSeconds(this.nextMapMainNPCRespawnTime - System.currentTimeMillis());
        try {
            if (!(!this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE || this.config.WAIT_SETTINGS.WAIT_ON_PORTAL || this.config.WAIT_SETTINGS.WAIT_ON_PREFERRED_ZONE || this.hero.getMap().getId() != 306 && this.hero.getMap().getId() != 307 && this.hero.getMap().getId() != 308)) {
                return !(!this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) || currentSpawnSeconds <= 90L || currentSpawnSeconds >= 600L || nextSpawnSeconds <= 240L && nextSpawnSeconds >= -100L || this.movement.isMoving() && !this.config.WAIT_SETTINGS.ROAM_ON_SPOT || this.attack.isAttacking() || this.hero.isAttacking() || (!this.config.CLEAR_SETTINGS.MAPS_TO_CLEAN.isEmpty() || this.config.CLEAR_SETTINGS.CLEAN_IMPULSE) && !this.npcs.stream().noneMatch(npc -> npc.getEntityInfo().getUsername().equals("\\\\ Impulse II //") || npc.getEntityInfo().getUsername().equals("\\\\ Attend IX //")));
            }
            if (this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE && this.config.WAIT_SETTINGS.WAIT_ON_PORTAL && !this.portals.isEmpty() || !this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE && this.config.WAIT_SETTINGS.WAIT_ON_PORTAL && !this.portals.isEmpty()) {
                Portal closest = this.portals.stream().min(Comparator.comparing(portal -> portal.getLocationInfo().distanceTo((Locatable)this.hero))).orElse(null);
                if (closest.getLocationInfo().getCurrent().distanceTo((Locatable)this.hero) < 250.0) {
                    return !(currentSpawnSeconds <= 90L || currentSpawnSeconds >= 600L || nextSpawnSeconds <= 240L && nextSpawnSeconds >= -100L || this.movement.isMoving() && !this.config.WAIT_SETTINGS.ROAM_ON_SPOT || this.attack.isAttacking() || this.hero.isAttacking());
                }
                return false;
            }
            if (this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE && this.config.WAIT_SETTINGS.WAIT_ON_PREFERRED_ZONE && this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo()) || !this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE && this.config.WAIT_SETTINGS.WAIT_ON_PREFERRED_ZONE && this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo())) {
                return !(currentSpawnSeconds <= 90L || currentSpawnSeconds >= 600L || nextSpawnSeconds <= 240L && nextSpawnSeconds >= -100L || this.movement.isMoving() && !this.config.WAIT_SETTINGS.ROAM_ON_SPOT || this.attack.isAttacking() || this.hero.isAttacking());
            }
            if (this.config.MAP_SETTINGS.SAFETY) {
                this.refreshing = System.currentTimeMillis() + 10000L;
                return this.safety.state() == SafetyFinder.Escaping.WAITING;
            }
            return this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && !this.movement.isMoving() && !this.attack.isAttacking() && !this.hero.isAttacking();
        }
        catch (Exception e) {
            return !this.movement.isMoving() && !this.attack.isAttacking() && !this.hero.isAttacking();
        }
    }

    public void onTickTask() {
        if (!this.pastLogMessages.isEmpty() && this.gameResourcesAPI.findTranslation("server_restart_n_minutes").isPresent() && this.gameResourcesAPI.findTranslation("server_restart_n_seconds").isPresent() && this.gameResourcesAPI.findTranslation("server_restarting").isPresent() && this.gameResourcesAPI.findTranslation("server_shutdown_n_minutes").isPresent() && this.gameResourcesAPI.findTranslation("server_shutdown_n_seconds").isPresent() && this.gameResourcesAPI.findTranslation("log_boot_message").isPresent()) {
            this.pastLogMessages.forEach(this::onLogReceived);
            this.pastLogMessages.clear();
        }
    }
}
