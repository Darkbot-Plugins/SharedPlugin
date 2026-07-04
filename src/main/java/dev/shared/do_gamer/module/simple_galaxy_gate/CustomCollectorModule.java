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

    private static final long FAKE_BOX_TIMEOUT_MS = 300_000L; // 5 minutes in milliseconds
    private static final long CARGO_BOX_TIMEOUT_MS = 30_000L; // 30 seconds in milliseconds

    private final EntitiesAPI entities;
    private final Map<String, FakeEntity.FakeBox> fakeBoxes = new HashMap<>();
    private final PetGearHelper petGearHelper;
    private SimpleGalaxyGateConfig config;

    CustomCollectorModule(PluginAPI api) {
        super(api);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.petGearHelper = new PetGearHelper(api);
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
                this.collectBox();
                return true;
            }
        }
        return !this.hasNoBox();
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
            if (box == null
                    || FakeEntity.isFakeEntity(box)
                    || !box.isValid()
                    || box.isCollected()) {
                continue; // Skip invalid, fake, or collected boxes
            }

            String hash = box.getHash();
            FakeEntity.FakeBox fake = this.fakeBoxes.get(hash);
            long timeout = this.isResource(box.getTypeName()) ? CARGO_BOX_TIMEOUT_MS : FAKE_BOX_TIMEOUT_MS;

            if (fake == null || !fake.isValid()) {
                fake = this.entities.fakeEntityBuilder()
                        .location(box.getLocationInfo())
                        .keepAlive(timeout)
                        .removeOnSelect(true)
                        .box(box.getInfo());
                this.fakeBoxes.put(hash, fake);
            } else {
                fake.setLocation(box.getLocationInfo());
                fake.setTimeout(timeout);
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
        super.collectBox();
    }

    /**
     * Tries to activate PET collect gear based on
     * the current configuration and box conditions.
     */
    public void tryActivatePetCollectGear() {
        if (this.config == null || !this.petGearHelper.isEnabled() || this.count() < 1) {
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
            this.petGearHelper.tryUse(PetGear.LOOTER);
        }
    }
}
