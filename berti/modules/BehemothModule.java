package dev.shared.berti.modules;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.managers.AuthAPI;
import dev.shared.berti.modules.BL.BLModule;
import dev.shared.berti.modules.configs.BLConfig;
import dev.shared.berti.types.enums.Mode;
import eu.darkbot.shared.utils.PortalJumper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

@Feature(name="Behemoth Module", description="Module for killing Behemoth")
public class BehemothModule
extends BLModule
implements Configurable<BLConfig> {
    public BehemothModule(Main main, PluginAPI api) {
        super(main, api, (PortalJumper)api.requireInstance(PortalJumper.class));
        this.currentMode = Mode.BEHE;
    }

    @Override
    public void onTickModule() {
        try {
            if (!this.config.PVP_HELPER_SETTINGS.FOCUS_ENEMY_FIRST) {
                this.npcs.stream().filter(npc -> Objects.equals(npc.getEntityInfo().getUsername(), "\\\\ Mindfire Behemoth //")).findFirst().ifPresent(npc -> {
                    this.attackMainNPCFirst((Npc)npc);
                    this.behemoth = npc;
                });
            } else if (this.canAttackEnemy()) {
                this.AttackEnemy();
                this.lastCloakPurchasedTime = System.currentTimeMillis() - 6500L;
                return;
            }
            super.onTickModule();
            if (this.config.MAP_SETTINGS.SAFETY && this.checkDangerousAndCurrentMap()) {
                this.mainBehemothLogic();
            } else if (this.checkMap()) {
                this.mainBehemothLogic();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void mainBehemothLogic() throws IOException {
        this.SetCurrentMapPredictedSpawnTime();
        this.HandleNPC(this.behemoth);
        this.PetStuff();
        this.CustomEventStuff();
        this.DeathCountStuff();
        this.NPCSpawnStuff(this.behemoth, 900000L);
        if (!this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && !this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.behemoth, 900000L);
        } else if (this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && !this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.impulse, 900000L);
        } else if (!this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.attend, 900000L);
        } else if (this.config.CLEAR_SETTINGS.CLEAN_IMPULSE && this.clearAttendOnCurrentMap()) {
            this.MapChangeStuff(this.impulseAndAttend, 900000L);
        }
        this.ServerRestartStuff();
        this.ChangeMapStuff();
        this.beheSettings();
        this.boxSettings();
    }

    public void setConfig(ConfigSetting<BLConfig> configSetting) {
        this.config = (BLConfig)configSetting.getValue();
    }
}
