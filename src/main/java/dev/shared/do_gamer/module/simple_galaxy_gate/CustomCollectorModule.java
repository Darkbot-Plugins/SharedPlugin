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

    private static final long DEFAULT_FAKE_BOX_TIMEOUT_MS = 300_000L; // 5 minutes in milliseconds

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
            if (this.shouldSkip(box)) {
                continue;
            }

            String hash = box.getHash();
            FakeEntity.FakeBox fake = this.fakeBoxes.get(hash);

            if (fake == null || !fake.isValid()) {
                this.fakeBoxes.put(hash, this.createFakeBox(box));
            } else {
                this.updateFakeBox(fake, box);
            }
        }

        this.cleanupFakeBoxes();
    }

    /**
     * Determines if a box should be skipped (not marked as fake)
     */
    private boolean shouldSkip(Box box) {
        return box == null
                || FakeEntity.isFakeEntity(box)
                || !box.isValid()
                || box.isCollected()
                || this.isResource(box.getTypeName()); // Skip cargo boxes (they despawn in 30 seconds)
    }

    /**
     * How long a fake box should remain alive before being removed
     */
    private long getFakeBoxTimeoutMs() {
        if (this.config == null) {
            return DEFAULT_FAKE_BOX_TIMEOUT_MS;
        }
        return this.config.other.fakeBoxTimeoutMinutes * 60_000L;
    }

    /**
     * Creates a fake box entity to represent a real box in the game map
     */
    private FakeEntity.FakeBox createFakeBox(Box box) {
        return this.entities.fakeEntityBuilder()
                .location(box.getLocationInfo())
                .keepAlive(this.getFakeBoxTimeoutMs())
                .removeOnSelect(true)
                .box(box.getInfo());
    }

    /**
     * Updates the location and timeout of an existing fake box to match the real
     * box
     */
    private void updateFakeBox(FakeEntity.FakeBox fake, Box box) {
        fake.setLocation(box.getLocationInfo());
        fake.setTimeout(this.getFakeBoxTimeoutMs());
    }

    /**
     * Cleans up fake boxes that are no longer valid or have been collected
     */
    private void cleanupFakeBoxes() {
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
