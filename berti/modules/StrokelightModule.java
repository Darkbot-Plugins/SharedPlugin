package dev.shared.berti.modules;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.Locatable;
import dev.shared.berti.modules.BL.BLModule;
import dev.shared.berti.modules.configs.BLConfig;
import dev.shared.berti.types.enums.Mode;
import eu.darkbot.shared.utils.PortalJumper;
import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Feature(name="Strokelight Module", description="Module for killing Strokelight")
public class StrokelightModule
extends BLModule
implements Configurable<BLConfig> {
    public StrokelightModule(Main main, PluginAPI api) {
        super(main, api, (PortalJumper)api.requireInstance(PortalJumper.class));
        this.currentMode = Mode.STROKE;
    }

    @Override
    public void onTickModule() {
        try {
            if (!this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_FIRST) {
                this.npcs.stream().filter(npc -> Objects.equals(npc.getEntityInfo().getUsername(), "\\\\ Strokelight Barrage //")).findFirst().ifPresent(npc -> {
                    this.attackMainNPCFirst((Npc)npc);
                    this.strokeLight = npc;
                });
            } else if (this.canAttackEnemy()) {
                this.AttackEnemy();
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            super.onTickModule();
            if (this.config.MAP_SETTINGS.SAFETY && this.checkDangerousAndCurrentMap()) {
                this.mainStrokelightLogic();
            } else if (this.checkMap()) {
                this.mainStrokelightLogic();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void mainStrokelightLogic() throws IOException {
        this.SetCurrentMapPredictedSpawnTime();
        this.HandleNPC(this.strokeLight);
        this.CustomEventStuff();
        this.DeathCountStuff();
        this.NPCSpawnStuff(this.strokeLight, 900000L);
        if (!this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && !this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.strokeLight, 900000L);
        } else if (this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && !this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.impulse, 900000L);
        } else if (!this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.abideAndSteadfast, 900000L);
        } else if (this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.impulseAbideAndSteadfast, 900000L);
        }
        this.PetStuff();
        this.ChangeMapStuff();
        this.beheSettings();
        this.boxSettings();
    }

    @Override
    public void HandleNPC(Npc mainNpc) throws IOException {
        Box box = this.currentBox = this.boxesCheckList.isEmpty() ? null : (Box)this.boxesCheckList.get(0);
        if (mainNpc == null && this.hero.getMap().getId() != ((Integer)this.workingMap.getValue()).intValue()) {
            this.ChangeWorkmap();
            this.ablFirst = true;
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_FIRST && this.canAttackEnemy()) {
            this.AttackEnemy();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (mainNpc != null) {
            this.mainNPCSpawned = true;
            this.AttackNPC(mainNpc);
            this.SetFormation();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_AFTER_BIG_NPCS && this.canAttackEnemy()) {
            this.AttackEnemy();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
        } else if (this.canAttackNPC(this.abideAndSteadfast)) {
            if (!this.attack.hasTarget() || !this.LocationWithinBoundaries(this.attack.getTarget().getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.AttackNPC(this.abideAndSteadfast);
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
        } else if (this.config.CLEAR_SETTINGS.CLEAN_AGGRESSIVE_NPC && this.canAttackNPC(this.agressiveNpc) && !this.hero.isInvisible()) {
            if (!this.attack.hasTarget()) {
                this.AttackNPC(this.agressiveNpc);
            } else {
                this.AttackNPC((Npc)this.attack.getTargetAs(Npc.class));
            }
        } else if (this.shouldCollect()) {
            this.CollectBoxes();
        } else if (this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_LAST && this.canAttackEnemy()) {
            this.AttackEnemy();
            this.lastCloakPurchasedTime = System.currentTimeMillis() - 7500L;
        } else {
            this.attack.setTarget(null);
            if (this.config.WAIT_SETTINGS.WAIT_FOR_GROUP && this.groupMembers.stream().anyMatch(groupMember -> groupMember.getMapId() != this.hero.getMap().getId())) {
                this.portals.stream().min(Comparator.comparing(portal -> portal.getLocationInfo().distanceTo((Locatable)this.hero))).ifPresent(closest -> {
                    if (closest.getLocationInfo().getCurrent().distanceTo((Locatable)this.hero) < 100.0) {
                        this.movement.stop(false);
                    } else {
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

    @Override
    public void SetFormation() {
        if (this.strokeLight != null && this.strokeLight.getHealth().hpPercent() <= this.config.COLLECT_SETTING.COLLECT_NPC) {
            this.hero.setMode((ShipMode)this.config.COLLECT_SETTING.COLLECT_CONFIG);
            this.forceTime = System.currentTimeMillis() + (long)this.config.COLLECT_SETTING.FORCE * 1000L;
            this.force = true;
        }
        if (this.isForceOver()) {
            if ((this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS) || this.movement.isInPreferredZone((Locatable)this.hero.getLocationInfo())) && this.currentMapMainNPCRespawnTime - System.currentTimeMillis() <= (long)this.config.CUSTOM_EVENT.SPAWN_BEACON_X_SEC_BEFORE_MAIN_NPC * 1000L && TimeUnit.MILLISECONDS.toSeconds(this.currentMapMainNPCRespawnTime - System.currentTimeMillis()) >= 0L || this.hero.getLocalTarget() != null && this.hero.distanceTo((Locatable)this.hero.getLocalTarget()) < 1500.0) {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
            } else if (this.LocationWithinBoundaries(this.hero.getLocationInfo(), true, this.config.MAP_SETTINGS.NPC_RADIUS)) {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ATTACK_CONFIG);
            } else {
                this.hero.setMode((ShipMode)this.config.CONFIG_SETTINGS.ROAM_CONFIG);
            }
            this.force = false;
            this.forceTime = 0L;
        }
    }

    @Override
    public void setConfig(Locatable direction) {
        this.SetFormation();
    }

    public void setConfig(ConfigSetting<BLConfig> configSetting) {
        this.config = (BLConfig)configSetting.getValue();
    }
}
