package dev.shared.maxim.petfuel;

import dev.shared.maxim.petfuel.config.PetFuelConfig;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.InstructionProvider;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.game.items.Item;
import eu.darkbot.api.game.items.ItemCategory;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.StatsAPI;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Feature(name = "Auto PET Fuel Buyer", description = "Automatically buys PET fuel when it reaches a configured amount.")
public final class AutoPetFuelBuyer implements Task, Configurable<PetFuelConfig>, InstructionProvider {

    private static final String PET_FUEL_ITEM_ID = "resource_pet-fuel";
    private static final String SHOP_ENDPOINT = "ajax/shop.php";
    private static final String SHOP_CATEGORY = "petGear";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BackpageAPI backpage;
    private final HeroItemsAPI heroItems;
    private final StatsAPI stats;
    private final JLabel statusLabel = new JLabel("Auto PET Fuel Buyer: waiting");

    private PetFuelConfig config;
    private long nextActionAt;

    public AutoPetFuelBuyer(PluginAPI api) {
        this.backpage = api.requireAPI(BackpageAPI.class);
        this.heroItems = api.requireAPI(HeroItemsAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<PetFuelConfig> setting) {
        this.config = setting.getValue();
    }

    @Override
    public JComponent beforeConfig() {
        return this.statusLabel;
    }

    @Override
    public void onTickTask() {
        // Backpage requests run on the background tick.
    }

    @Override
    public void onBackgroundTick() {
        if (this.config == null || !this.config.enabled) {
            this.setStatus("disabled");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextActionAt) {
            return;
        }

        if (!this.isBackpageReady()) {
            this.setStatus("waiting for valid SID");
            this.scheduleRetry(now);
            return;
        }

        Optional<Double> currentFuel = this.readCurrentFuel();
        if (!currentFuel.isPresent()) {
            this.setStatus("waiting for PET fuel inventory");
            this.scheduleRetry(now);
            return;
        }

        PetFuelPlanner.Plan plan = PetFuelPlanner.plan(
                currentFuel.get(),
                this.config.minFuel,
                this.config.buyAmount,
                this.config.minUridiumReserve,
                this.stats.getTotalUridium());

        switch (plan.action) {
            case SKIP_ENOUGH_FUEL:
                this.setStatus(String.format("fuel %,.0f, next check in %d min", currentFuel.get(), this.config.checkIntervalMinutes));
                this.scheduleNextCheck(now);
                return;
            case SKIP_NOT_ENOUGH_URI:
                this.setStatus(String.format("not enough uri, needs ~%,.0f", plan.uridiumCost));
                this.scheduleNextCheck(now);
                return;
            case BUY:
                this.tryBuy(plan, now);
                return;
            default:
                this.scheduleRetry(now);
        }
    }

    private boolean isBackpageReady() {
        return this.backpage.isInstanceValid()
                && this.backpage.getSidStatus() != null
                && this.backpage.getSidStatus().contains("OK");
    }

    private Optional<Double> readCurrentFuel() {
        try {
            for (Item item : this.heroItems.getItems(ItemCategory.PET)) {
                if (PET_FUEL_ITEM_ID.equals(item.getId())) {
                    return Optional.of(item.getQuantity());
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private void tryBuy(PetFuelPlanner.Plan plan, long now) {
        try {
            this.buyPetFuel(plan.amountToBuy);
            this.setStatus(String.format("bought %,d PET fuel for ~%,.0f uri", plan.amountToBuy, plan.uridiumCost));
            this.log("Bought %,d PET fuel for ~%,.0f uri", plan.amountToBuy, plan.uridiumCost);
            this.scheduleNextCheck(now);
        } catch (IOException e) {
            this.setStatus("shop request failed, retrying");
            this.log("PET fuel shop request failed: %s", e.getMessage());
            this.scheduleRetry(now);
        }
    }

    private void buyPetFuel(int amount) throws IOException {
        HttpURLConnection connection = this.backpage.postHttp(SHOP_ENDPOINT, 3_000)
                .setParam("action", "purchase")
                .setParam("category", SHOP_CATEGORY)
                .setParam("itemId", PET_FUEL_ITEM_ID)
                .setParam("amount", amount)
                .setParam("selectedName", "")
                .setParam("level", "")
                .getConnection();

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode);
        }
    }

    private void scheduleNextCheck(long now) {
        this.nextActionAt = now + Math.max(1, this.config.checkIntervalMinutes) * 60_000L;
    }

    private void scheduleRetry(long now) {
        this.nextActionAt = now + Math.max(5, this.config.retryDelaySeconds) * 1_000L;
    }

    private void setStatus(String status) {
        this.statusLabel.setText("[" + LocalTime.now().format(TIME_FORMAT) + "] Auto PET Fuel Buyer: " + status);
    }

    private void log(String message, Object... args) {
        if (this.config != null && this.config.verboseLogging) {
            System.out.println("[Auto PET Fuel Buyer] " + String.format(message, args));
        }
    }
}
