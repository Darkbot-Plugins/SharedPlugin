package dev.shared.berti.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.NpcExtra;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.game.other.LocationInfo;
import eu.darkbot.api.game.other.Lockable;
import eu.darkbot.api.managers.AuthAPI;
import dev.shared.berti.modules.BL.BLModule;
import dev.shared.berti.modules.configs.BLConfig;
import dev.shared.berti.types.enums.BLMaps;
import dev.shared.berti.types.enums.CustomEvent;
import dev.shared.berti.types.enums.Mode;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import dev.shared.berti.modules.configs.BLConfig;

@Feature(name="Invoke Module", description="Module for killing Invokes")
public class InvokeModule
extends BLModule
implements Configurable<BLConfig> {
    protected HashMap<Long, Integer> mapIdToRespawnTime = new HashMap();
    protected final String MAIN_NPC_NAME = "\\ Invoke XVI //";
    NpcInfo getOrCreateInvokeNpcInfo;
    protected int invokeKillCount = 0;



    public InvokeModule(Main main, PluginAPI api) {
        super(main, api, (PortalJumper)api.requireInstance(PortalJumper.class));
        this.currentMode = Mode.INVOKE;
        this.getOrCreateInvokeNpcInfo = this.conf.getOrCreateNpcInfo("\\\\ Invoke XVI //");
    }

    @Override
    public void onTickModule() {
        try {
            if (!this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_FIRST) {
                this.npcs.stream().filter(npc -> Objects.equals(npc.getEntityInfo().getUsername(), "\\\\ Invoke XVI //")).findFirst().ifPresent(npc -> {
                    this.attackMainNPCFirst((Npc)npc);
                    this.strokeLight = npc;
                });
            } else if (this.canAttackEnemy()) {
                this.AttackEnemy();
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            super.onTickModule();
            if (this.config.MAP_SETTINGS.SAFETY) {
                if (this.checkDangerousAndCurrentMap()) {
                    this.mainInvokeLogic();
                }
            } else if (this.checkMap()) {
                this.mainInvokeLogic();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void mainInvokeLogic() throws IOException {
        this.SetCurrentMapPredictedSpawnTime();
        this.AddPredictedInvokeSpawnTime();
        this.RemoveSpawnedInvokesFromList();
        this.HandleNPC(this.invoke);
        this.CustomEventStuff();
        this.DeathCountStuff();
        this.NPCSpawnStuff(this.invoke, 300000L);
        this.MapChangeStuff();
        this.PetStuff();
        this.ChangeMapStuff();
        this.beheSettings();
        this.boxSettings();
    }

    @Override
    public void NPCSpawnStuff(Npc mainNpc, long timeToAdd) throws IOException {
        if (this.mainNPCSpawned && mainNpc == null) {
            this.mainNPCKilled = true;
            this.mainNPCDeathTime = System.currentTimeMillis();
            this.ablFirst = true;
            this.invokeKillCount++;
            if (this.config.CLOAK_SETTING.BUY_CLOAK_ON_MAIN_NPC_DEATH) {
                this.buyCloak();
            }
        }
    }

    @Override
    public void CustomEventStuff() {
        if (this.attack.hasTarget() && this.invoke != null && this.pet.distanceTo((Locatable)this.invoke) < 1000.0) {
            this.DmgBeacon();
        }
        if ((this.behemoth != null || this.invoke != null || this.strokeLight != null) && this.config.CUSTOM_EVENT.CUSTOM_EVENTS.contains((Object)CustomEvent.SPEARHEAD) && (this.attack.isAttacking() || this.hero.isAttacking())) {
            this.itemsAPI.useItem((SelectableItem)SelectableItem.Ability.SPEARHEAD_TARGET_MARKER, new ItemFlag[0]);
        }
    }

    public void AddPredictedInvokeSpawnTime() {
        if (this.mainNPCSpawned && this.mainNPCKilled) {
            this.mainNPCSpawned = false;
            this.mainNPCKilled = false;
            long nextSpawn = System.currentTimeMillis() + 300000L;
            this.mapIdToRespawnTime.put(nextSpawn, this.hero.getMap().getId());
        }
    }

    public void RemoveSpawnedInvokesFromList() {
        if (this.mapIdToRespawnTime != null) {
            this.mapIdToRespawnTime.entrySet().removeIf(invokeRespawn -> (Long)invokeRespawn.getKey() <= System.currentTimeMillis());
        }
    }

    @Override
    public void HandleNPC(Npc mainNpc) throws IOException {
        boolean hasNonMainTarget;
        this.currentBox = this.boxesCheckList.isEmpty() ? null : (Box)this.boxesCheckList.get(0);
        NpcInfo invokeNpcInfo = this.pet.getLocatorNpcs().stream().filter(npc -> npc.hasExtraFlag((Enum)NpcExtra.PET_LOCATOR) && npc.getPriority() == this.getOrCreateInvokeNpcInfo.getPriority()).findFirst().orElse(null);
        double distanceToInvoke = 0.0;
        Location invokeLoc = null;
        boolean bl = hasNonMainTarget = this.attack.hasTarget() && !Objects.equals(this.attack.getTarget().getEntityInfo().getUsername(), "\\\\ Invoke XVI //");
        if (!this.mainNPCSpawned && !this.mainNPCKilled && this.pet.getLocatorNpcLoc().isPresent() && invokeNpcInfo != null) {
            invokeLoc = (Location)this.pet.getLocatorNpcLoc().get();
            distanceToInvoke = invokeLoc.distanceTo((Locatable)this.hero);
        }
        if (this.hero.getMap().getId() != ((Integer)this.workingMap.getValue()).intValue()) {
            this.ChangeWorkmap();
            this.ablFirst = true;
        } else {
            boolean canCollectNow;
            if (this.config.WAIT_SETTINGS.WAIT_FOR_GROUP && this.groupMembers.stream().anyMatch(groupMember -> groupMember.getMapId() != this.hero.getMap().getId())) {
                this.portals.stream().min(Comparator.comparing(portal -> portal.getLocationInfo().distanceTo((Locatable)this.hero))).ifPresent(closest -> {
                    if (closest.getLocationInfo().getCurrent().distanceTo((Locatable)this.hero) < 100.0) {
                        this.movement.stop(false);
                    } else if (this.isNotWaiting()) {
                        this.movement.moveTo((Locatable)closest);
                    }
                });
                return;
            }
            boolean bl2 = canCollectNow = this.shouldCollect() && this.currentBox != null && this.hero.distanceTo((Locatable)this.currentBox) < (double)this.main.config.COLLECT.RADIUS;
            if (mainNpc != null && !this.mainNPCKilled) {
                this.mainNPCSpawned = true;
                this.AttackNPC(mainNpc);
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            if (canCollectNow) {
                this.CollectBoxes();
                return;
            }
            if (invokeNpcInfo != null && invokeLoc != null && (this.movement.isInPreferredZone((Locatable)invokeLoc) || this.LocationWithinBoundaries((LocationInfo)invokeLoc, true, this.config.MAP_SETTINGS.NPC_RADIUS)) && (!this.attack.hasTarget() || hasNonMainTarget) && distanceToInvoke > 0.0 && distanceToInvoke > (double)((Integer)this.npcDistanceIgnore.getValue()).intValue()) {
                if (this.isForceOver()) {
                    this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
                }
                this.movement.moveTo((Locatable)invokeLoc);
                return;
            }
            if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_AFTER_BIG_NPCS && this.canAttackEnemy()) {
                this.AttackEnemy();
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            if (this.canAttackNPC(this.impulse)) {
                if (!this.attack.hasTarget()) {
                    this.AttackNPC(this.impulse);
                } else {
                    this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
                }
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            if (this.canAttackNPC(this.anyNPC)) {
                if (!this.attack.hasTarget()) {
                    this.AttackNPC(this.anyNPC);
                } else {
                    this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
                }
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            if (this.config.CLEAR_SETTINGS.CLEAN_AGGRESSIVE_NPC && this.canAttackNPC(this.agressiveNpc) && !this.hero.isInvisible()) {
                if (!this.attack.hasTarget()) {
                    this.AttackNPC(this.agressiveNpc);
                } else {
                    this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
                }
                return;
            }
            if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_LAST && this.canAttackEnemy()) {
                this.AttackEnemy();
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            if (this.shouldCollect()) {
                this.CollectBoxes();
                return;
            }
            this.SetFormation();
            this.RoamToNPCSpot();
            this.BuyCloakIfUncloaked();
        }
    }

    @Override
    public void CollectBoxes() {
        double distance = this.hero.distanceTo((Locatable)this.currentBox);
        if (distance < 700.0 && this.isNotWaiting()) {
            if (!this.currentBox.tryCollect()) {
                return;
            }
            this.waitingUntil = System.currentTimeMillis() + (long)this.currentBox.getInfo().getWaitTime() + (long)Math.min(1000, this.currentBox.getRetries() * 100) + this.hero.timeTo(distance) + 30L;
        } else {
            this.movement.moveTo((Locatable)this.currentBox);
        }
    }

    public boolean LocationWithinBoundaries(Location targetLocation, boolean npc, double additionalRadius) {
        if (this.centerLoc == null) {
            return false;
        }
        double radius = npc ? additionalRadius : 50.0;
        return targetLocation.distanceTo((Locatable)this.centerLoc) <= radius;
    }

    @Override
    public void RoamToNPCSpot() {
        if (this.config.MAP_SETTINGS.SAFETY && (this.safety.state() == SafetyFinder.Escaping.REFRESH || this.safety.state() == SafetyFinder.Escaping.REPAIR)) {
            return;
        }
        if (System.currentTimeMillis() - this.lastMoveToSpot <= (long)(1000 + new Random().nextInt(1000))) {
            return;
        }
        if (this.LocationWithinBoundaries(this.hero.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && System.currentTimeMillis() >= this.lastSpotTime + 30000L && this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) <= 20.0 && this.isNotWaiting() || this.currentMapMainNPCRespawnTime - System.currentTimeMillis() <= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L && this.currentMode != Mode.INVOKE || this.config.WAIT_SETTINGS.ROAM_ON_SPOT) {
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo(this.config.MAP_SETTINGS.NPC_RADIUS));
            this.lastMoveToSpot = System.currentTimeMillis();
        } else if (!this.LocationWithinBoundaries(this.hero.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && this.isNotWaiting()) {
            this.movement.moveTo((Locatable)this.DetermineLocationToMoveTo(this.config.MAP_SETTINGS.NPC_RADIUS));
            this.lastMoveToSpot = System.currentTimeMillis();
        }
        if (this.hero.getLocationInfo().getCurrent().distanceTo((Locatable)this.lastSpot) > 1.0) {
            this.lastSpot = this.movement.getDestination();
            this.lastSpotTime = System.currentTimeMillis();
            this.lastMoveToSpot = System.currentTimeMillis();
        }
    }

    @Override
    public void AttackNPC(Npc npc) {
        if (npc == null || !npc.isValid() || !this.shouldKill(npc)) {
            if (this.attack.hasTarget()) {
                this.attack.setTarget(null);
                this.attack.stopAttack();
            }
            this.SetFormation();
            this.RoamToNPCSpot();
            return;
        }
        this.attack.setTarget((Lockable)Objects.requireNonNullElse(this.rocket, npc));
        this.attack.tryLockAndAttack();
        this.SetFormation();
        this.ignoreInvalidTarget();
        if (this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo()) || this.LocationWithinBoundaries(this.hero.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
            this.moveToAnSafePosition();
        } else {
            this.RoamToNPCSpot();
        }
    }

    @Override
    public boolean canAttackNPC(Npc target) {
        return !(target == null || !this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo()) && !this.LocationWithinBoundaries(this.hero.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS) || !this.movement.isInPreferredZone((Locatable)target.getLocationInfo()) && !this.LocationWithinBoundaries(target.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS));
    }

    @Override
    public void setConfig(Locatable direction) {
        this.SetFormation();
    }

    @Override
    public void SetFormation() {
        if (this.invoke != null && this.invoke.getHealth().hpPercent() <= this.config.COLLECT_SETTING.COLLECT_NPC) {
            this.forceTime = System.currentTimeMillis() + (long)this.config.COLLECT_SETTING.FORCE * 1000L;
            this.force = true;
            this.hero.setMode((ShipMode)this.config.COLLECT_SETTING.COLLECT_CONFIG);
        }
        if (this.isForceOver()) {
            if (this.attack.hasTarget() && this.hero.distanceTo((Locatable)this.attack.getTarget()) < 1000.0) {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
            } else {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
            }
            this.force = false;
            this.forceTime = 0L;
        }
    }

    public void MapChangeStuff() throws IOException {
        long currentMapNextSpawn = -1L;
        long currentMapSpawnSize = 0L;
        if (System.currentTimeMillis() - this.mainNPCDeathTime > 3500L) {
            if (!this.mapIdToRespawnTime.isEmpty() && this.mapIdToRespawnTime.entrySet().stream().filter(t -> ((Integer)t.getValue()).intValue() == this.hero.getMap().getId()).min(Map.Entry.comparingByKey()).isPresent()) {
                currentMapNextSpawn = (Long)this.mapIdToRespawnTime.entrySet().stream().filter(t -> ((Integer)t.getValue()).intValue() == this.hero.getMap().getId()).min(Map.Entry.comparingByKey()).get().getKey();
                currentMapSpawnSize = this.mapIdToRespawnTime.entrySet().stream().filter(t -> ((Integer)t.getValue()).intValue() == this.hero.getMap().getId()).count();
            }
            if (currentMapNextSpawn - System.currentTimeMillis() > 60000L && this.invokeKillCount >= this.config.CUSTOM_EVENT.MAP_SWITCH_COUNT) {
                if (this.config.CLEAR_SETTINGS.MAPS_TO_CLEAN.contains(this.mapIdToBLMap.get(this.hero.getMap().getId()))) {
                    if (this.impulse == null) {
                        if (this.config.CLOAK_SETTING.BUY_CLOAK_ON_NPCS_CLEARED) {
                            this.buyCloak();
                        }
                        this.changeMap = true;
                    }
                } else {
                    this.changeMap = true;
                }
                this.invokeKillCount = 0;
            }
        }
    }

    protected String GetInvokeStatusMessage() {
        try {
            String workMap = ((BLMaps)((Object)this.mapIdToBLMap.get(this.hero.getMap().getId()))).getName();
            String nextMap = "";
            nextMap = this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.getNextMap()) ? ((BLMaps)((Object)this.mapIdToBLMap.get(this.getNextMap().getMapId()))).getName() : (this.config.MAP_SETTINGS.MAPS_TO_VISIT_LIST.contains((Object)this.getPreviousMap()) ? ((BLMaps)((Object)this.mapIdToBLMap.get(this.getPreviousMap().getMapId()))).getName() : workMap);
            long currentMapNextSpawn = -1L;
            if (!this.mapIdToRespawnTime.isEmpty() && this.mapIdToRespawnTime.entrySet().stream().filter(t -> ((Integer)t.getValue()).intValue() == this.hero.getMap().getId()).min(Map.Entry.comparingByKey()).isPresent()) {
                currentMapNextSpawn = (Long)this.mapIdToRespawnTime.entrySet().stream().filter(t -> ((Integer)t.getValue()).intValue() == this.hero.getMap().getId()).min(Map.Entry.comparingByKey()).get().getKey();
            }
            currentMapNextSpawn = currentMapNextSpawn - System.currentTimeMillis() < 0L ? -1L : (currentMapNextSpawn -= System.currentTimeMillis());
            String output = ". Cur: " + workMap + ". Nxt:" + nextMap + ". Box:" + this.oneBLBoxCount + ":" + this.twoBLBoxCount + ":" + this.threeBLBoxCount + ". Spn:" + TimeUnit.MILLISECONDS.toSeconds(currentMapNextSpawn);
            if (this.config.MAP_SETTINGS.ROTATION) {
                return "3-2-1" + output;
            }
            return "1-2-3" + output;
        }
        catch (Exception e) {
            e.printStackTrace();
            return "Unable to get status message";
        }
    }

    @Override
    public String getStatus() {
        return this.GetInvokeStatusMessage();
    }

    public void setConfig(ConfigSetting<BLConfig> configSetting) {
        this.config = (BLConfig)configSetting.getValue();
    }

    @Override
    public boolean canRefresh() {
        long currentMapNextSpawn = -1L;
        if (!this.mapIdToRespawnTime.isEmpty() && this.mapIdToRespawnTime.entrySet().stream().min(Map.Entry.comparingByKey()).isPresent()) {
            currentMapNextSpawn = (Long)this.mapIdToRespawnTime.entrySet().stream().min(Map.Entry.comparingByKey()).get().getKey();
            currentMapNextSpawn = TimeUnit.MILLISECONDS.toSeconds(currentMapNextSpawn - System.currentTimeMillis());
        }
        try {
            if (this.config.MAP_SETTINGS.REFRESH_IN_MAIN_NPC_ZONE && ((Integer)this.workingMap.getValue() == 306 || (Integer)this.workingMap.getValue() == 307 || (Integer)this.workingMap.getValue() == 308)) {
                return this.LocationWithinBoundaries(this.hero.getLocationInfo().getCurrent(), true, this.config.MAP_SETTINGS.NPC_RADIUS) && currentMapNextSpawn > 60L && currentMapNextSpawn < 180L && !this.movement.isMoving() && !this.attack.isAttacking() && !this.hero.isAttacking() && this.npcs.stream().noneMatch(npc -> npc.getEntityInfo().getUsername().equals("\\\\ Impulse II //") || npc.getEntityInfo().getUsername().equals("\\\\ Attend IX //"));
            }
            if (this.config.MAP_SETTINGS.SAFETY) {
                this.refreshing = System.currentTimeMillis() + 10000L;
                return this.safety.state() == SafetyFinder.Escaping.WAITING;
            }
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}
