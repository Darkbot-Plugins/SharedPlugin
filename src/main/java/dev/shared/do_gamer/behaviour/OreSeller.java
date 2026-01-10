package dev.shared.do_gamer.behaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.shared.do_gamer.config.OreSellerConfig;
import dev.shared.do_gamer.config.OreSellerConfig.SellModeOptions;
import dev.shared.do_gamer.config.OreSellerConfig.TradeMapOptions;
import dev.shared.do_gamer.utils.CaptchaBoxDetector;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.api.utils.ItemNotEquippedException;
import eu.darkbot.shared.modules.TemporalModule;
import eu.darkbot.shared.utils.MapTraveler;
import eu.darkbot.shared.utils.PortalJumper;
import eu.darkbot.shared.utils.SafetyFinder;
import eu.darkbot.util.Timer;

@Feature(name = "Ore Seller", description = "Sells ores at base, via PET trader gear, or using the HM7 trade drone when cargo is full")
public class OreSeller extends TemporalModule implements Behavior, Configurable<OreSellerConfig> {

    private static final Logger logger = Logger.getLogger(OreSeller.class.getName());

    private final HeroAPI hero;
    private final MovementAPI movement;
    private final EntitiesAPI entities;
    private final OreAPI oreApi;
    private final StatsAPI stats;
    private final PetAPI pet;
    private final StarSystemAPI starSystem;
    private final MapTraveler traveler;
    private final HeroItemsAPI items;
    private final AttackAPI attacker;
    private final PortalJumper portalJumper;
    private final SafetyFinder safetyFinder;

    private OreSellerConfig config;
    private ActiveMode activeMode = ActiveMode.NONE;
    private State state = State.IDLE;
    private State postSafetyState;
    private Station.Refinery targetRefinery;
    private List<OreAPI.Ore> sellPlan = Collections.emptyList();
    private int sellIndex;
    private final EnumMap<TimerSlot, Timer> timers = new EnumMap<>(TimerSlot.class);
    private Boolean previousPetEnabled;
    private PetGear previousPetGear;
    private GameMap desiredBaseMap;
    private String desiredBaseMapName;
    private static final int BASE_DOCKING_DISTANCE = 300;
    private static final int MIN_PALLADIUM_STACK = 15;
    private static final int SELL_INTERVAL_MS = 750;
    private static final double MIN_TRIGGER_PERCENT = 0.05;
    private static final double MAX_TRIGGER_PERCENT = 0.99;
    private static final long MIN_ACTIVATION_DELAY_MS = 250L;
    private static final long TRAVEL_LOAD_DELAY_MS = 3_000L;
    private static final long DOCKING_LOAD_DELAY_MS = 2_000L;
    private static final long TRADE_WINDOW_POPULATE_DELAY_MS = 1_000L;
    private static final long CLOSE_TRADE_DELAY_MS = 1_000L;

    private enum ActiveMode {
        NONE,
        BASE,
        PET,
        DRONE
    }

    private enum State {
        IDLE,
        TRAVEL_TO_BASE,
        MOVE_TO_REFINERY,
        OPEN_TRADE,
        SELLING,
        CLOSE_TRADE,
        SAFE_POSITIONING,
        PET_PREPARING,
        DRONE_PREPARING
    }

    private enum TimerSlot {
        SELL_DELAY,
        FAIL_SAFE,
        COOL_DOWN,
        LOAD,
        CLOSE_TRADE
    }

    public OreSeller(PluginAPI api) {
        super(api.requireAPI(BotAPI.class));
        this.hero = api.requireAPI(HeroAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
        this.attacker = api.requireAPI(AttackAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.oreApi = api.requireAPI(OreAPI.class);
        this.stats = api.requireAPI(StatsAPI.class);
        this.pet = api.requireAPI(PetAPI.class);
        this.starSystem = api.requireAPI(StarSystemAPI.class);
        this.items = api.requireAPI(HeroItemsAPI.class);
        ConfigAPI configApi = api.requireAPI(ConfigAPI.class);

        EventBrokerAPI events = api.requireAPI(EventBrokerAPI.class);
        this.portalJumper = new PortalJumper(api);
        this.traveler = new MapTraveler(this.pet, this.hero, this.starSystem, this.movement,
                this.portalJumper, this.entities, events);
        this.safetyFinder = new SafetyFinder(this.hero, this.attacker, this.items, this.movement,
                this.starSystem, configApi, this.entities, this.traveler, this.portalJumper);
        events.registerListener(this.traveler);
        events.registerListener(this.safetyFinder);

        for (TimerSlot slot : TimerSlot.values()) {
            this.timers.put(slot, Timer.get());
        }
    }

    @Override
    public void setConfig(ConfigSetting<OreSellerConfig> setting) {
        this.config = setting.getValue();
    }

    @Override
    public void onTickBehavior() {
        if (!this.isReadyForBehavior()) {
            return;
        }

        if (this.activeMode != ActiveMode.NONE) {
            if (this.bot.getModule() != this) {
                this.bot.setModule(this);
            }
            return;
        }

        if (this.isCooldownActive() || this.bot.getModule() == this) {
            return;
        }

        ActiveMode desiredMode = this.pickMode();
        if (desiredMode == ActiveMode.NONE || !this.shouldTriggerSelling()) {
            return;
        }

        List<OreAPI.Ore> plan = this.buildSellPlan(desiredMode);
        if (plan.isEmpty() || !this.hasOreStock(plan)) {
            return;
        }

        this.startSequence(desiredMode, plan);
    }

    @Override
    public boolean canRefresh() {
        // Prevent refresh while active
        return !this.isActive();
    }

    @Override
    public void onTickModule() {
        if (!this.isActive()) {
            return;
        }

        if (!this.isReadyForBehavior()) {
            this.finish(false);
            return;
        }

        Timer failSafe = this.timer(TimerSlot.FAIL_SAFE);
        if (failSafe.isArmed()) {
            if (this.isFailSafeExemptState()) {
                // Reset the fail-safe timer in exempt states
                long timeout = this.resolveFailSafeMillis(this.activeMode);
                failSafe.activate(timeout);
            } else if (failSafe.isInactive()) {
                logger.warning("Ore seller timed out");
                this.finish(false);
                return;
            }
        }

        switch (this.state) {
            case TRAVEL_TO_BASE:
                this.handleTravelToBase();
                break;
            case MOVE_TO_REFINERY:
                this.handleMoveToRefinery();
                break;
            case OPEN_TRADE:
                this.handleOpenTrade();
                break;
            case SELLING:
                this.handleSelling();
                break;
            case CLOSE_TRADE:
                this.handleCloseTrade();
                break;
            case SAFE_POSITIONING:
                this.handleSafePositioning();
                break;
            case PET_PREPARING:
                this.handlePetPreparing();
                break;
            case DRONE_PREPARING:
                this.handleDronePreparing();
                break;
            default:
                this.finish(true);
                break;
        }
    }

    @Override
    public String getStatus() {
        if (this.state == State.IDLE || this.activeMode == ActiveMode.NONE) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(this.describeMode());
        parts.add(this.describeState());

        String extra = this.describeExtraContext();
        if (extra != null && !extra.isEmpty()) {
            parts.add(extra);
        }

        return "Ore Seller: " + String.join(" | ", parts);
    }

    /**
     * Describes the currently active selling mode.
     */
    private String describeMode() {
        switch (this.activeMode) {
            case BASE:
                return "Base trade";
            case PET:
                return "PET trader";
            case DRONE:
                return "HM7 trade drone";
            default:
                return this.humanizeEnum(this.activeMode);
        }
    }

    /**
     * Describes the current state within the state machine.
     */
    private String describeState() {
        switch (this.state) {
            case TRAVEL_TO_BASE:
                return "Traveling to base";
            case MOVE_TO_REFINERY:
                return "Moving to refinery";
            case OPEN_TRADE:
                return "Opening trade";
            case SELLING:
                return "Selling";
            case CLOSE_TRADE:
                return "Closing trade";
            case SAFE_POSITIONING:
                return "Moving to safe position";
            case PET_PREPARING:
                return "Preparing PET trade";
            case DRONE_PREPARING:
                return "Preparing drone trade";
            default:
                return this.humanizeEnum(this.state);
        }
    }

    /**
     * Provides extra contextual information for certain modes.
     */
    private String describeExtraContext() {
        switch (this.activeMode) {
            case BASE:
                if (this.desiredBaseMapName != null && !this.desiredBaseMapName.isEmpty()) {
                    return "target " + this.desiredBaseMapName;
                }
                break;
            case PET:
                if (!this.pet.isActive()) {
                    return "waiting on PET";
                }
                PetGear gear = this.pet.getGear();
                if (gear != null && gear != PetGear.TRADER) {
                    return "switching to trader gear";
                }
                return "PET ready";
            case DRONE:
                return this.timer(TimerSlot.LOAD).isActive() ? "waiting on drone" : "drone ready";
            default:
                break;
        }
        return null;
    }

    private String humanizeEnum(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String raw = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isReadyForBehavior() {
        // Ensure module is enabled in config
        if (this.config == null || !this.config.enabled) {
            return false;
        }

        // Keep inactive in GG maps when in base mode or any NPCs are present
        if (this.isGGMap() && (SellModeOptions.BASE.equals(this.config.mode) || this.hasActiveNpc())) {
            return false;
        }

        // Keep inactive while attacking
        if (this.attacker.hasTarget() && this.attacker.isAttacking()) {
            return false;
        }

        // Keep inactive if captcha boxes detected
        return !CaptchaBoxDetector.hasCaptchaBoxes(this.entities);
    }

    /**
     * Determines if this module currently controls the bot and has a mode selected.
     */
    private boolean isActive() {
        return this.bot.getModule() == this && this.activeMode != ActiveMode.NONE;
    }

    /**
     * Checks whether the cooldown timer is active before starting a new run.
     */
    private boolean isCooldownActive() {
        Timer cooldown = this.timer(TimerSlot.COOL_DOWN);
        return cooldown.isArmed() && cooldown.isActive();
    }

    /**
     * Checks if the current map is a GG map.
     */
    private boolean isGGMap() {
        GameMap currentMap = this.starSystem.getCurrentMap();
        return currentMap != null && currentMap.isGG();
    }

    /**
     * Checks if have any NPCs present on the current map (useful for GG maps).
     */
    private boolean hasActiveNpc() {
        return this.entities != null && !this.entities.getNpcs().isEmpty();
    }

    /**
     * Selects the appropriate selling mode based on config and availability.
     */
    private ActiveMode pickMode() {
        String mode = this.config.mode;

        switch (mode) {
            case SellModeOptions.PET:
                return this.canUsePetTrader() ? ActiveMode.PET : ActiveMode.NONE;
            case SellModeOptions.DRONE:
                return this.canUseTradeDrone() ? ActiveMode.DRONE : ActiveMode.NONE;
            case SellModeOptions.BASE:
                return ActiveMode.BASE;
            default:
                return ActiveMode.NONE;
        }
    }

    /**
     * Confirms the PET trader gear is equipped and usable.
     */
    private boolean canUsePetTrader() {
        return this.pet.hasGear(PetGear.TRADER);
    }

    /**
     * Confirms the HM7 trade drone item is ready for use.
     */
    private boolean canUseTradeDrone() {
        return this.items.getItem(SelectableItem.Cpu.HMD_07,
                ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE)
                .filter(item -> item.getQuantity() > 0).isPresent();
    }

    /**
     * Initializes transient state to begin a selling run.
     */
    private void startSequence(ActiveMode mode, List<OreAPI.Ore> plan) {
        this.activeMode = mode;
        this.sellPlan = plan == null ? Collections.emptyList() : plan;
        this.sellIndex = 0;
        this.targetRefinery = null;
        this.desiredBaseMap = null;
        this.desiredBaseMapName = null;
        this.postSafetyState = null;
        long failSafe = this.resolveFailSafeMillis(mode);
        this.timer(TimerSlot.FAIL_SAFE).activate(failSafe);
        this.timer(TimerSlot.SELL_DELAY).disarm();
        this.previousPetEnabled = null;
        this.previousPetGear = null;
        this.timer(TimerSlot.LOAD).disarm();

        boolean prepared;
        switch (mode) {
            case BASE:
                prepared = this.prepareBaseModeState();
                break;
            case PET:
                prepared = this.prepareNonBaseSellingState(State.PET_PREPARING);
                break;
            case DRONE:
                prepared = this.prepareNonBaseSellingState(State.DRONE_PREPARING);
                break;
            default:
                prepared = false;
                break;
        }

        if (!prepared) {
            this.abortStart();
            return;
        }

        this.bot.setModule(this);
    }

    /**
     * Returns the per-mode timeout in milliseconds with a consistent minimum
     * threshold.
     */
    private long resolveFailSafeMillis(ActiveMode mode) {
        int seconds;
        switch (mode) {
            case PET:
                seconds = this.config.pet.maxWaitSeconds;
                break;
            case DRONE:
                seconds = this.config.drone.maxWaitSeconds;
                break;
            case BASE:
            default:
                seconds = this.config.base.maxWaitSeconds;
                break;
        }
        return seconds * 1000L;
    }

    /**
     * Resets transient state when a run cannot begin.
     */
    private void abortStart() {
        this.timer(TimerSlot.FAIL_SAFE).disarm();
        this.timer(TimerSlot.SELL_DELAY).disarm();
        this.timer(TimerSlot.LOAD).disarm();
        this.timer(TimerSlot.CLOSE_TRADE).disarm();
        this.postSafetyState = null;
        if (this.safetyFinder != null) {
            this.safetyFinder.setRefreshing(false);
        }
        this.activeMode = ActiveMode.NONE;
        this.state = State.IDLE;
        this.sellPlan = Collections.emptyList();
        this.targetRefinery = null;
    }

    /**
     * Determines travel needs and sets up the base selling state.
     */
    private boolean prepareBaseModeState() {
        this.desiredBaseMap = this.resolveDesiredBaseMap();
        if (this.desiredBaseMap == null) {
            logger.warning("Unable to resolve target base map for ore selling");
            return false;
        }

        if (Objects.equals(this.starSystem.getCurrentMap(), this.desiredBaseMap)) {
            this.state = State.MOVE_TO_REFINERY;
        } else {
            if (this.previousPetEnabled == null) {
                this.previousPetEnabled = this.pet.isEnabled();
            }
            this.state = State.TRAVEL_TO_BASE;
            this.traveler.setTarget(this.desiredBaseMap);
        }
        return true;
    }

    /**
     * Sets up the non-base selling state with safety positioning.
     */
    private boolean prepareNonBaseSellingState(State nextState) {
        if (this.isGGMap()) {
            this.state = nextState;
            this.movement.stop(false);
            return true; // No need for safety finder in GG maps
        }
        if (this.safetyFinder == null) {
            logger.warning("Safety finder unavailable for ore selling");
            return false;
        }
        this.postSafetyState = nextState;
        this.state = State.SAFE_POSITIONING;
        this.safetyFinder.setRefreshing(true);
        return true;
    }

    /**
     * Handles traveling to the configured base map.
     */
    private void handleTravelToBase() {
        if (this.desiredBaseMap == null) {
            this.desiredBaseMap = this.resolveDesiredBaseMap();
            if (this.desiredBaseMap == null) {
                this.finish(false);
                return;
            }
            if (this.previousPetEnabled == null) {
                this.previousPetEnabled = this.pet.isEnabled();
            }
            this.traveler.setTarget(this.desiredBaseMap);
        }

        if (Objects.equals(this.starSystem.getCurrentMap(), this.desiredBaseMap)) {
            if (this.wait(this.timer(TimerSlot.LOAD), TRAVEL_LOAD_DELAY_MS)) {
                return;
            }
            this.state = State.MOVE_TO_REFINERY;
            return;
        }

        this.timer(TimerSlot.LOAD).disarm();

        this.traveler.tick();
    }

    /**
     * Moves the hero to the refinery station on the current map.
     */
    private void handleMoveToRefinery() {
        Station.Refinery refinery = this.resolveRefinery();
        if (refinery == null) {
            logger.warning("No refinery found on current map for ore selling");
            this.finish(false);
            return;
        }

        double distance = this.hero.distanceTo(refinery);
        if (distance > BASE_DOCKING_DISTANCE) {
            this.movement.moveTo(refinery);
            return;
        }

        this.movement.stop(false);
        if (this.wait(this.timer(TimerSlot.LOAD), DOCKING_LOAD_DELAY_MS)) {
            return;
        }
        this.state = State.OPEN_TRADE;
    }

    /**
     * Handles safe positioning before non-base selling.
     */
    private void handleSafePositioning() {
        if (this.safetyFinder == null || this.postSafetyState == null) {
            this.finish(false);
            return;
        }

        SafetyFinder.Escaping escapeState = this.safetyFinder.state();
        if (escapeState != SafetyFinder.Escaping.WAITING && escapeState != SafetyFinder.Escaping.NONE) {
            this.safetyFinder.setRefreshing(true);
        } else if (escapeState == SafetyFinder.Escaping.WAITING) {
            this.safetyFinder.setRefreshing(false);
        }

        if (!this.safetyFinder.tick()) {
            return;
        }

        if (this.safetyFinder.state() != SafetyFinder.Escaping.NONE) {
            return;
        }

        this.safetyFinder.setRefreshing(false);
        this.movement.stop(false);
        this.state = this.postSafetyState;
        this.postSafetyState = null;
    }

    /**
     * Determines if the current state is exempt from fail-safe timeout.
     */
    private boolean isFailSafeExemptState() {
        switch (this.state) {
            case TRAVEL_TO_BASE:
            case MOVE_TO_REFINERY:
            case SAFE_POSITIONING:
                return true;
            default:
                return false;
        }
    }

    /**
     * Convenience accessor for slot-based timers.
     */
    private Timer timer(TimerSlot slot) {
        return this.timers.get(slot);
    }

    /**
     * Simple helper that arms a timer and reports whether the caller should keep
     * waiting.
     */
    private boolean wait(Timer timer, long durationMs) {
        if (timer == null) {
            return false;
        }
        if (!timer.isArmed()) {
            timer.activate(durationMs);
            return true;
        }
        if (timer.isActive()) {
            return true;
        }
        timer.disarm();
        return false;
    }

    /**
     * Finds the refinery station on the current map.
     */
    private Station.Refinery resolveRefinery() {
        if (this.targetRefinery != null && this.targetRefinery.isValid()) {
            return this.targetRefinery;
        }

        this.targetRefinery = this.entities.getStations().stream()
                .filter(station -> station instanceof Station.Refinery)
                .map(station -> (Station.Refinery) station)
                .filter(Station::isValid)
                .findFirst()
                .orElse(null);

        return this.targetRefinery;
    }

    /**
     * Opens the refinery window and waits for the UI to be ready for selling.
     */
    private void handleOpenTrade() {
        Station.Refinery refinery = this.resolveRefinery();
        if (refinery == null) {
            this.finish(false);
            return;
        }

        if (!this.oreApi.showTrade(true, refinery)) {
            return; // Animation in progress
        }

        if (!this.oreApi.canSellOres()) {
            return;
        }

        this.beginSellingAfterTradeWindow();
    }

    /**
     * Sequentially sells ores according to the precomputed plan.
     */
    private void handleSelling() {
        if (!this.oreApi.canSellOres()) {
            this.handleCannotSellOres();
            return;
        }

        this.ensurePetTraderGearDuringSelling();

        if (this.hero.isMoving()) {
            this.movement.stop(false);
        }

        if (this.sellIndex >= this.sellPlan.size()) {
            if (this.wait(this.timer(TimerSlot.CLOSE_TRADE), CLOSE_TRADE_DELAY_MS)) {
                return;
            }
            this.state = State.CLOSE_TRADE;
            return;
        }

        this.timer(TimerSlot.CLOSE_TRADE).disarm();

        if (this.timer(TimerSlot.SELL_DELAY).isActive()) {
            return;
        }

        OreAPI.Ore ore = this.sellPlan.get(this.sellIndex);
        int amount = this.oreApi.getAmount(ore);
        if (amount == 0 || (ore == OreAPI.Ore.PALLADIUM && amount < MIN_PALLADIUM_STACK)) {
            this.sellIndex++; // Move to next ore
            return;
        }

        this.oreApi.sellOre(ore);
        this.timer(TimerSlot.SELL_DELAY).activate(SELL_INTERVAL_MS);
    }

    /**
     * Ensures the PET trader gear remains equipped during selling.
     */
    private void ensurePetTraderGearDuringSelling() {
        if (this.activeMode != ActiveMode.PET || !this.pet.isActive()) {
            return; // Only relevant in PET mode
        }

        try {
            this.pet.setGear(PetGear.TRADER);
        } catch (ItemNotEquippedException ignored) {
            // Ignored exception, we just wanted to ensure trader gear is equipped
        }
    }

    /**
     * Handles the case when ores cannot be sold, updating state accordingly.
     */
    private void handleCannotSellOres() {
        if (this.activeMode == ActiveMode.BASE) {
            this.state = State.OPEN_TRADE;
        } else if (this.activeMode == ActiveMode.PET) {
            this.state = State.PET_PREPARING;
        } else if (this.activeMode == ActiveMode.DRONE) {
            this.state = State.DRONE_PREPARING;
        } else {
            this.finish(false);
        }
        this.timer(TimerSlot.CLOSE_TRADE).disarm();
    }

    /**
     * Closes the trade window after finishing the sell plan.
     */
    private void handleCloseTrade() {
        if (!this.oreApi.showTrade(false, null)) {
            return;
        }
        this.finish(true);
    }

    /**
     * Prepares the PET for trading by equipping the trader gear and enabling it.
     */
    private void handlePetPreparing() {
        if (!this.canUsePetTrader()) {
            this.finish(false);
            return;
        }

        long delay = Math.max(MIN_ACTIVATION_DELAY_MS, this.config.pet.activationDelayMs);
        Timer loadTimer = this.timer(TimerSlot.LOAD);
        if (loadTimer.isActive()) {
            return;
        }
        loadTimer.disarm();

        if (this.oreApi.canSellOres()) {
            this.beginSellingAfterTradeWindow();
            return;
        }

        if (this.previousPetEnabled == null) {
            this.previousPetEnabled = this.pet.isEnabled();
        }

        if (this.config.pet.keepEnabled && !this.pet.isEnabled()) {
            this.pet.setEnabled(true);
            loadTimer.activate(delay);
            return;
        }

        if (!this.pet.isActive()) {
            return;
        }

        PetGear gear = this.pet.getGear();
        if (gear == PetGear.TRADER) {
            try {
                this.pet.setGear(PetGear.PASSIVE); // Unequip trader gear
                loadTimer.activate(delay);
            } catch (ItemNotEquippedException ignored) {
                // Ignored exception, we just wanted to unequip trader gear
            }
            return;
        }

        if (this.previousPetGear == null) {
            this.previousPetGear = gear;
        }

        try {
            this.pet.setGear(PetGear.TRADER);
            loadTimer.activate(delay);
        } catch (ItemNotEquippedException e) {
            logger.log(Level.WARNING, "Failed to equip PET trader gear for ore selling", e);
            this.finish(false);
        }
    }

    /**
     * Prepares the HM7 trade drone for selling by activating it.
     */
    private void handleDronePreparing() {
        if (!this.canUseTradeDrone()) {
            this.finish(false);
            return;
        }

        if (this.oreApi.canSellOres()) {
            this.beginSellingAfterTradeWindow();
            return;
        }

        long delay = Math.max(MIN_ACTIVATION_DELAY_MS, this.config.drone.activationDelayMs);
        this.items.useItem(SelectableItem.Cpu.HMD_07, delay,
                ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE, ItemFlag.NOT_SELECTED);
    }

    /**
     * Arms the sell delay and advances to the selling state after the trade UI is
     * ready.
     */
    private void beginSellingAfterTradeWindow() {
        this.timer(TimerSlot.SELL_DELAY).activate(TRADE_WINDOW_POPULATE_DELAY_MS);
        this.state = State.SELLING;
    }

    /**
     * Determines if current cargo exceeds the user defined threshold.
     */
    private boolean shouldTriggerSelling() {
        return this.getCargoPercent() >= this.normalizeTriggerThreshold();
    }

    /**
     * Calculates the current cargo fill percentage.
     */
    private double getCargoPercent() {
        int max = this.stats.getMaxCargo();
        if (max <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) this.stats.getCargo() / max);
    }

    /**
     * Clamps the trigger threshold to a safe range.
     */
    private double normalizeTriggerThreshold() {
        double value = this.config.triggerPercent;
        return Math.max(MIN_TRIGGER_PERCENT, Math.min(MAX_TRIGGER_PERCENT, value));
    }

    /**
     * Builds a list of ores to sell based on config and mode specific rules.
     */
    private List<OreAPI.Ore> buildSellPlan(ActiveMode mode) {
        List<OreAPI.Ore> plan = new ArrayList<>();
        if (this.config.ores == null) {
            return plan;
        }

        for (OreAPI.Ore ore : OreAPI.Ore.values()) {
            if (!ore.isSellable()) {
                continue;
            }
            if (this.shouldSellOre(ore, mode)) {
                plan.add(ore);
            }
        }
        return plan;
    }

    /**
     * Determines if a specific ore should be sold based on config and mode.
     */
    private boolean shouldSellOre(OreAPI.Ore ore, ActiveMode mode) {
        if (this.config.ores == null || ore == null) {
            return false;
        }
        switch (ore) {
            case PROMETIUM:
                return this.config.ores.prometium;
            case ENDURIUM:
                return this.config.ores.endurium;
            case TERBIUM:
                return this.config.ores.terbium;
            case PROMETID:
                return this.config.ores.prometid;
            case DURANIUM:
                return this.config.ores.duranium;
            case PROMERIUM:
                return this.config.ores.promerium;
            case SEPROM:
                return this.config.ores.seprom;
            case PALLADIUM:
                return this.shouldSellPalladium(mode);
            case OSMIUM:
                return this.config.ores.osmium;
            default:
                return false;
        }
    }

    /**
     * Special handling for Palladium selling rules.
     */
    private boolean shouldSellPalladium(ActiveMode mode) {
        if (!this.config.ores.palladium) {
            return false;
        }

        if (mode != ActiveMode.BASE) {
            return false;
        }

        if (!this.isConfiguredBaseFiveTwo()) {
            return false;
        }

        int amount = this.oreApi.getAmount(OreAPI.Ore.PALLADIUM);
        return amount >= MIN_PALLADIUM_STACK;
    }

    /**
     * Utility helper that checks whether the 5-2 base is selected.
     */
    private boolean isConfiguredBaseFiveTwo() {
        String map = this.config.base != null ? this.config.base.map : null;
        if (map == null) {
            return false;
        }
        return TradeMapOptions.FIVE_TWO.equalsIgnoreCase(map);
    }

    /**
     * Checks if there is any ore stock to sell in the provided plan.
     */
    private boolean hasOreStock(List<OreAPI.Ore> plan) {
        for (OreAPI.Ore ore : plan) {
            int amount = this.oreApi.getAmount(ore);
            if (amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleans up transient state after finishing a selling run.
     */
    private void finish(boolean success) {
        if (!success) {
            logger.fine("Ore seller aborted before finishing run");
        }
        this.oreApi.showTrade(false, null);
        if (this.previousPetEnabled != null || this.previousPetGear != null) {
            this.restorePetSettings();
        }

        this.timer(TimerSlot.FAIL_SAFE).disarm();
        this.timer(TimerSlot.SELL_DELAY).disarm();
        this.timer(TimerSlot.LOAD).disarm();
        this.timer(TimerSlot.CLOSE_TRADE).disarm();
        this.postSafetyState = null;
        if (this.safetyFinder != null) {
            this.safetyFinder.setRefreshing(false);
        }
        this.activeMode = ActiveMode.NONE;
        this.state = State.IDLE;
        this.targetRefinery = null;
        this.sellPlan = Collections.emptyList();
        this.sellIndex = 0;
        this.desiredBaseMap = null;
        this.desiredBaseMapName = null;

        long cooldown = Math.max(0, this.config.cooldownSeconds) * 1000L;
        Timer cooldownTimer = this.timer(TimerSlot.COOL_DOWN);
        if (cooldown > 0) {
            cooldownTimer.activate(cooldown);
        } else {
            cooldownTimer.disarm();
        }

        this.goBack();
    }

    /**
     * Brings the PET back to the gear/enabled state it had before selling.
     */
    private void restorePetSettings() {
        if (this.previousPetGear != null) {
            try {
                this.pet.setGear(this.previousPetGear);
            } catch (ItemNotEquippedException ignored) {
                // Nothing to do
            }
        }
        if (this.previousPetEnabled != null) {
            this.pet.setEnabled(this.previousPetEnabled);
        }
        this.previousPetEnabled = null;
        this.previousPetGear = null;
    }

    /**
     * Resolves the desired base map based on config and faction.
     */
    private GameMap resolveDesiredBaseMap() {
        if (this.starSystem == null || this.config == null || this.config.base == null) {
            return null;
        }

        String selected = this.config.base.map != null ? this.config.base.map : TradeMapOptions.X1;
        String mapName = this.mapNameForSelection(selected);
        if (mapName == null || mapName.isEmpty()) {
            return null;
        }

        this.desiredBaseMapName = mapName;
        return this.starSystem.getOrCreateMap(mapName);
    }

    /**
     * Converts the map selection enum value into an actual map string.
     */
    private String mapNameForSelection(String selection) {
        if (selection == null) {
            return null;
        }
        switch (selection) {
            case TradeMapOptions.X8:
                return this.getFactionPrefix() + "-8";
            case TradeMapOptions.FIVE_TWO:
                return "5-2";
            case TradeMapOptions.X1:
            default:
                return this.getFactionPrefix() + "-1";
        }
    }

    /**
     * Resolves the proper x- map prefix based on the hero faction.
     */
    private String getFactionPrefix() {
        EntityInfo info = this.hero.getEntityInfo();
        EntityInfo.Faction faction = info != null ? info.getFaction() : null;
        if (faction == null) {
            return "1";
        }
        switch (faction) {
            case EIC:
                return "2";
            case VRU:
                return "3";
            case MMO:
            default:
                return "1";
        }
    }

}