package dev.shared.do_gamer.module.simple_galaxy_gate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import dev.shared.do_gamer.module.simple_galaxy_gate.config.SimpleGalaxyGateConfig;
import dev.shared.do_gamer.utils.PetGearHelper;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.FakeEntity;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.shared.modules.CollectorModule;

public final class CustomCollectorModule extends CollectorModule {

    private static final long FAKE_BOX_TIMEOUT_MS = 300_000L;

    private final EntitiesAPI entities;
    private final Map<String, FakeEntity.FakeBox> fakeBoxes = new HashMap<>();
    private final PetGearHelper petGearHeper;
    private SimpleGalaxyGateConfig config;

    CustomCollectorModule(PluginAPI api) {
        super(api);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.petGearHeper = new PetGearHelper(api);
    }

    public void setModuleConfig(SimpleGalaxyGateConfig config) {
        this.config = config;
    }

    @Override
    public void onTickModule() {
        if (this.isNotWaiting()) {
            this.pet.setEnabled(true);
            this.findBox();
            this.tryCollectNearestBox();
        }
    }

    /**
     * Attempt to collect box if available
     */
    public boolean collectIfAvailable() {
        if (this.isNotWaiting()) {
            this.findBox();
            Box box = this.currentBox;
            if (box != null && box.isValid()) {
                this.tryCollectNearestBox();
                return true;
            }
        }
        return false;
    }

    @Override
    public void findBox() {
        super.findBox();
        this.markVisibleBoxesAsFake();
    }

    /**
     * Checks if there are no boxes available to collect
     */
    public boolean hasNoBox() {
        this.findBox(); // Recheck for boxes
        return this.currentBox == null;
    }

    /**
     * Marks all currently visible boxes as fake to avoid disappearing them
     */
    private void markVisibleBoxesAsFake() {
        for (Box box : new ArrayList<>(this.entities.getBoxes())) {
            if (box == null || FakeEntity.isFakeEntity(box) || !box.isValid() || box.isCollected()) {
                continue; // Skip invalid or already collected boxes
            }

            String hash = box.getHash();
            FakeEntity.FakeBox fake = this.fakeBoxes.get(hash);

            if (fake == null || !fake.isValid()) {
                fake = this.entities.fakeEntityBuilder()
                        .location(box.getLocationInfo())
                        .keepAlive(FAKE_BOX_TIMEOUT_MS)
                        .removeOnSelect(true)
                        .box(box.getInfo());
                this.fakeBoxes.put(hash, fake);
            } else {
                fake.setLocation(box.getLocationInfo());
                fake.setTimeout(FAKE_BOX_TIMEOUT_MS);
            }
        }

        // Clean up invalid or collected fake boxes
        Iterator<Map.Entry<String, FakeEntity.FakeBox>> iterator = this.fakeBoxes.entrySet().iterator();
        while (iterator.hasNext()) {
            FakeEntity.FakeBox fake = iterator.next().getValue();
            if (fake == null || !fake.isValid() || fake.isCollected()) {
                iterator.remove();
            }
        }
    }

    /**
     * Counts currently tracked fake boxes
     */
    public int count() {
        return this.fakeBoxes.size();
    }

    /**
     * Returns the collection of currently tracked fake boxes
     */
    public Collection<FakeEntity.FakeBox> getBoxes() {
        return this.fakeBoxes.values();
    }

    @Override
    protected void collectBox() {
        this.tryActivatePetCollectGear();
        // If PET collect gear is enabled, use PET to collect the box.
        if (this.petGearHeper.isUsing(PetGear.LOOTER)) {
            double distance = this.hero.distanceTo(this.currentBox);
            if (distance < 250.0) {
                // Try to select to prevent ghost boxes
                if (this.currentBox.trySelect(false)) {
                    this.currentBox.setCollected();
                }
            } else {
                // Move towards the box to allow pet to collect it.
                this.movement.moveTo(this.currentBox);
            }
            return;
        }
        super.collectBox();
    }

    /**
     * Tries to activate PET collect gear based on
     * the current configuration and box conditions.
     */
    public void tryActivatePetCollectGear() {
        if (this.config == null || !this.petGearHeper.isEnabled()) {
            return;
        }

        boolean activate;
        switch (this.config.other.petCollect) {
            case ANY:
                activate = true;
                break;

            case ODD:
                int priority = this.currentBox.getInfo().getPriority();
                activate = Math.abs(priority) % 2 == 1; // Activate for odd priorities
                break;

            default:
                activate = false;
                break;
        }

        if (activate) {
            this.petGearHeper.tryUse(PetGear.LOOTER);
        }
    }
}
