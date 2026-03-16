package dev.shared.orbithelper.module.daily_quest.handlers.quest_executions;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import dev.shared.orbithelper.config.DailyQuestConfig.CollectionMethod;
import dev.shared.orbithelper.config.DailyQuestConfig.SellingMethod;
import dev.shared.orbithelper.module.daily_quest.DailyQuestModule;
import dev.shared.orbithelper.module.daily_quest.handlers.ShipSwitchHandler;
import dev.shared.orbithelper.module.daily_quest.model.ExecutionQuest;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.enums.PetGear;
import eu.darkbot.api.game.items.ItemCategory;
import eu.darkbot.api.game.items.ItemFlag;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.managers.OreAPI;

/**
 * Handles SELL_ORE quest execution.
 *
 * Ship switching delegated to ShipSwitchHandler.
 *
 * Flow:
 * [SWITCH_SHIP] → COLLECTING / SKYLAB_REQUEST / SKYLAB_WAIT
 * → TRAVEL_TO_SELL → MOVE_TO_REFINERY → OPEN_TRADE → SELLING
 * → [RESTORE_SHIP] → done
 */
public class SellOreHandler {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    private static final double CARGO_FILL_THRESHOLD = 0.70;
    private static final int DOCKING_DISTANCE = 300;
    private static final long DOCK_SETTLE_MS = 2_000L;
    private static final long SELL_TICK_MS = 750L;
    private static final long CARGO_COLLECT_MS = 150L;
    private static final long SKYLAB_SETTLE_MS = 5_000L;
    private static final int MAX_SKYLAB_TRANSFERS = 5;
    private static final long SKYLAB_CARGO_TIMEOUT_MS = 5_000L;
    private static final long SKYLAB_RETRY_DELAY_MS = 2_000L;
    private static final String FROM_SHIP_BOX_TYPE = "FROM_SHIP";

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------
    private enum Phase {
        IDLE,
        SWITCH_SHIP, // delegated to ShipSwitchHandler
        COLLECTING,
        SKYLAB_REQUEST,
        SKYLAB_WAIT,
        TRAVEL_TO_SELL,
        MOVE_TO_REFINERY,
        MOVE_TO_PORTAL,
        OPEN_TRADE,
        SELLING,
        CLOSE_TRADE,
        RESTORE_SHIP // delegated to ShipSwitchHandler
    }

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------
    private final DailyQuestModule module;
    private final ShipSwitchHandler shipSwitch;

    private Phase phase = Phase.IDLE;
    private long lastActionTime = 0;
    private long phaseEnterTime = 0;

    private OreAPI.Ore targetOre = null;
    private String originalHangarId = null;
    private boolean shipSwitchDone = false;

    private java.util.List<OreAPI.Ore> sellPlan = new java.util.ArrayList<>();
    private int sellIndex = 0;

    private Station.Refinery cachedRefinery = null;
    private eu.darkbot.api.game.entities.Portal cachedPortal = null;

    // Skylab transfer tracking
    private int skylabSuccessCount = 0; // successful transfers this quest
    private int cargoAtSkylabRequest = 0; // cargo when we sent the POST
    private long skylabRequestSentAt = 0; // timestamp of last POST

    // =========================================================================
    // Constructor
    // =========================================================================

    public SellOreHandler(DailyQuestModule module, ShipSwitchHandler shipSwitch) {
        this.module = module;
        this.shipSwitch = shipSwitch;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public void execute(ExecutionQuest quest) {
        if (shipSwitch.isSwitching()) {
            shipSwitch.tick();
            return;
        }

        if (phase == Phase.IDLE)
            initRun(quest);

        switch (phase) {
            case SWITCH_SHIP:
                break;
            case COLLECTING:
                handleCollecting();
                break;
            case SKYLAB_REQUEST:
                handleSkylabRequest();
                break;
            case SKYLAB_WAIT:
                handleSkylabWait();
                break;
            case TRAVEL_TO_SELL:
                handleTravelToSell();
                break;
            case MOVE_TO_REFINERY:
                handleMoveToRefinery();
                break;
            case MOVE_TO_PORTAL:
                handleMoveToPortal();
                break;
            case OPEN_TRADE:
                handleOpenTrade();
                break;
            case SELLING:
                handleSelling();
                break;
            case CLOSE_TRADE:
                handleCloseTrade();
                break;
            case RESTORE_SHIP:
                break;
            default:
                break;
        }
    }

    // =========================================================================
    // Hooks
    // =========================================================================

    public boolean checkPreTravel() {
        if (phase != Phase.IDLE)
            return false;

        String shipForSell = module.config.questTypes.sellOresSettings.shipForSell;
        if (shipForSell == null || shipForSell.isEmpty())
            return false;

        if (originalHangarId == null) {
            originalHangarId = resolveCurrentHangarId();
            if (originalHangarId == null)
                return true;
        }

        if (!originalHangarId.equals(shipForSell) && !shipSwitchDone) {
            if (!module.isShipSwitched) {
                module.isShipSwitched = true;
                module.restoreShipId = originalHangarId;
            }
            enterPhase(Phase.SWITCH_SHIP);
            shipSwitch.requestSwitch(shipForSell, () -> {
                shipSwitchDone = true;
                enterPhase(Phase.IDLE);
            });
            return true;
        }
        return false;
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    private void initRun(ExecutionQuest quest) {
        targetOre = resolveOre(quest.targetOre);
        cachedRefinery = null;

        String shipForSell = module.config.questTypes.sellOresSettings.shipForSell;
        if (shipForSell != null && !shipForSell.isEmpty()) {
            if (originalHangarId == null) {
                originalHangarId = resolveCurrentHangarId();
            }
            if (!shipForSell.equals(originalHangarId)) {
                if (!shipSwitchDone) {
                    enterPhase(Phase.SWITCH_SHIP);
                    shipSwitch.requestSwitch(shipForSell, () -> {
                        shipSwitchDone = true;
                        postInitRun();
                    });
                    return;
                }
            } else {
                shipSwitchDone = true;
            }
        }
        postInitRun();
    }

    private void postInitRun() {
        sellPlan.clear();
        sellPlan.add(OreAPI.Ore.PROMETIUM);
        sellPlan.add(OreAPI.Ore.ENDURIUM);
        sellPlan.add(OreAPI.Ore.TERBIUM);
        sellPlan.add(OreAPI.Ore.PROMETID);
        sellPlan.add(OreAPI.Ore.DURANIUM);
        sellPlan.add(OreAPI.Ore.PROMERIUM);
        if (effectiveSellingMethod() == SellingMethod.BASE) {
            sellPlan.add(OreAPI.Ore.PALLADIUM);
        }

        enterPhase(Phase.TRAVEL_TO_SELL);
    }

    /** Returns a human-readable current phase name for the status bar. */
    public String getSellPhaseStatus() {
        switch (phase) {
            case IDLE:
                return "Idle";
            case SWITCH_SHIP:
                return "Switching ship...";
            case COLLECTING:
                return "Collecting ore";
            case SKYLAB_REQUEST:
                return "Skylab: sending request...";
            case SKYLAB_WAIT:
                return "Skylab: waiting for transfer...";
            case TRAVEL_TO_SELL:
                return "Traveling to sell map";
            case MOVE_TO_REFINERY:
                return "Moving to Refinery";
            case MOVE_TO_PORTAL:
                return "Moving to Portal";
            case OPEN_TRADE:
                return "Opening trade";
            case SELLING:
                return "Selling ore...";
            case CLOSE_TRADE:
                return "Closing trade";
            case RESTORE_SHIP:
                return "Restoring ship...";
            default:
                return phase.name();
        }
    }

    // =========================================================================
    // Phase: COLLECTING
    // =========================================================================

    private void handleCollecting() {
        CollectionMethod method = module.config.questTypes.sellOresSettings.collectionMethod;

        if (method == CollectionMethod.SKYLAB) {
            if (effectiveSellingMethod() == SellingMethod.BASE) {
                enterPhase(Phase.MOVE_TO_REFINERY);
            } else {
                enterPhase(Phase.SKYLAB_REQUEST);
            }
            return;
        }

        module.travelHandler.setWorkingMap(module.travelHandler.getCurrentMap());

        if (cargoReadyToSell()) {
            enterPhase(Phase.TRAVEL_TO_SELL);
            return;
        }

        if (throttle(CARGO_COLLECT_MS))
            return;

        Box box = findNearestBox(FROM_SHIP_BOX_TYPE);
        if (box != null) {
            box.tryCollect();
            module.movement.moveTo(box);
            if (cargoReadyToSell()) {
                enterPhase(Phase.TRAVEL_TO_SELL);
            }
        } else if (!module.movement.isMoving()) {
            module.movement.moveRandom();
        }
    }

    private boolean cargoReadyToSell() {
        if (cargoFill() >= CARGO_FILL_THRESHOLD)
            return true;
        if (targetOre != null) {
            ExecutionQuest quest = module.questManager.getCurrentQuest();
            int inCargo = getOreAmount(targetOre);
            if (quest != null && inCargo >= (int) quest.remaining)
                return true;
        }
        return false;
    }

    // =========================================================================
    // Phase: SKYLAB_REQUEST — send transport request, then wait for cargo change
    // =========================================================================

    private void handleSkylabRequest() {
        // Settle delay — wait after phase entry (e.g. after ship switch or after
        // selling)
        long settleElapsed = System.currentTimeMillis() - phaseEnterTime;
        if (settleElapsed < SKYLAB_SETTLE_MS) {
            return;
        }

        // Max transfer guard
        if (skylabSuccessCount >= MAX_SKYLAB_TRANSFERS) {
            enterPhase(Phase.IDLE);
            return;
        }

        // 2s cooldown between retries
        if (throttle(SKYLAB_RETRY_DELAY_MS))
            return;

        int cargo = module.stats.getCargo();
        int maxCargo = module.stats.getMaxCargo();
        int freeSpace = maxCargo - cargo;

        int currentAmount = getOreAmount(targetOre);
        ExecutionQuest currentQuest = module.questManager.getCurrentQuest();
        int needed = currentQuest != null ? (int) Math.max(0, currentQuest.remaining - currentAmount) : 0;
        int transport = Math.min(needed, freeSpace);

        Phase afterSkylab = (effectiveSellingMethod() == SellingMethod.BASE)
                ? Phase.MOVE_TO_REFINERY
                : Phase.TRAVEL_TO_SELL;

        if (freeSpace <= 0) {
            enterPhase(afterSkylab);
            return;
        }

        if (transport <= 0) {
            enterPhase(afterSkylab);
            return;
        }

        // Build ore amounts
        int prometium = 0;
        int endurium = 0;
        int terbium = 0;
        int prometid = 0;
        int duranium = 0;
        int promerium = 0;
        if (targetOre != null) {
            switch (targetOre) {
                case PROMETIUM:
                    prometium = transport;
                    break;
                case ENDURIUM:
                    endurium = transport;
                    break;
                case TERBIUM:
                    terbium = transport;
                    break;
                case PROMETID:
                    prometid = transport;
                    break;
                case DURANIUM:
                    duranium = transport;
                    break;
                case PROMERIUM:
                    promerium = transport;
                    break;
                default:
                    break;
            }
        }

        // Fetch reload token (needed for the request to be accepted)
        String reloadToken = fetchReloadToken();

        // Build POST body
        String body = (reloadToken != null
                ? "reloadToken=" + reloadToken + "&reloadToken=" + reloadToken + "&"
                : "")
                + "action=internalSkylab&subaction=startTransport&mode=fast"
                + "&construction=TRANSPORT_MODULE"
                + "&count_prometium=" + prometium
                + "&count_endurium=" + endurium
                + "&count_terbium=" + terbium
                + "&count_prometid=" + prometid
                + "&count_duranium=" + duranium
                + "&count_xenomit=0"
                + "&count_promerium=" + promerium
                + "&count_seprom=0";

        // Record cargo before sending so SKYLAB_WAIT can detect change
        cargoAtSkylabRequest = cargo;
        skylabRequestSentAt = System.currentTimeMillis();

        postSkylab(body);
        enterPhase(Phase.SKYLAB_WAIT);
    }

    // =========================================================================
    // Phase: SKYLAB_WAIT — wait for cargo to increase (confirms transfer success)
    // =========================================================================

    private void handleSkylabWait() {
        int currentCargo = module.stats.getCargo();
        long elapsed = System.currentTimeMillis() - skylabRequestSentAt;

        if (currentCargo > cargoAtSkylabRequest) {
            skylabSuccessCount++;
            SellingMethod method = effectiveSellingMethod();
            String current = module.travelHandler.getCurrentMap();
            String faction = factionPrefix();
            if (method == SellingMethod.BASE
                    || current.equals(faction + "-1") || current.equals(faction + "-8")) {
                enterPhase(Phase.MOVE_TO_REFINERY);
            } else {
                enterPhase(Phase.MOVE_TO_PORTAL);
            }
            return;
        }

        if (elapsed >= SKYLAB_CARGO_TIMEOUT_MS) {
            enterPhase(Phase.SKYLAB_REQUEST);
        }
    }

    // =========================================================================
    // Phase: TRAVEL_TO_SELL
    // =========================================================================

    private void handleTravelToSell() {
        SellingMethod method = effectiveSellingMethod();
        CollectionMethod collect = module.config.questTypes.sellOresSettings.collectionMethod;
        String targetMap = resolveSellMap(method);
        if (targetMap == null) {
            enterPhase(Phase.IDLE);
            return;
        }

        String current = module.travelHandler.getCurrentMap();
        if (!current.equals(targetMap)) {
            module.travelHandler.setWorkingMap(targetMap);
            module.travelHandler.moveToMap(targetMap);
            return;
        }

        cachedRefinery = null;
        cachedPortal = null;
        onArrivedAtSellMap(method, collect);
    }

    private void onArrivedAtSellMap(SellingMethod method, CollectionMethod collect) {
        if (collect == CollectionMethod.SKYLAB && effectiveSellingMethod() != SellingMethod.BASE) {
            int inCargo = getOreAmount(targetOre);
            ExecutionQuest quest = module.questManager.getCurrentQuest();
            if (quest != null && inCargo < (int) quest.remaining) {
                enterPhase(Phase.SKYLAB_REQUEST);
                return;
            }
        }
        if (method == SellingMethod.BASE) {
            enterPhase(Phase.MOVE_TO_REFINERY);
        } else {
            enterPhase(Phase.MOVE_TO_PORTAL);
        }
    }

    // =========================================================================
    // Phase: MOVE_TO_PORTAL
    // =========================================================================

    private void handleMoveToPortal() {
        module.travelHandler.setWorkingMap(module.travelHandler.getCurrentMap());
        eu.darkbot.api.game.entities.Portal p = resolvePortal();
        if (p == null) {
            enterPhase(Phase.IDLE);
            return;
        }

        if (module.hero.distanceTo(p) > DOCKING_DISTANCE) {
            module.movement.moveTo(p);
            return;
        }
        module.movement.stop(false);
        if (throttle(DOCK_SETTLE_MS))
            return;
        enterPhase(Phase.OPEN_TRADE);
    }

    // =========================================================================
    // Phase: MOVE_TO_REFINERY
    // =========================================================================

    private void handleMoveToRefinery() {
        module.travelHandler.setWorkingMap(module.travelHandler.getCurrentMap());
        Station.Refinery ref = resolveRefinery();
        if (ref == null) {
            enterPhase(Phase.IDLE);
            return;
        }

        if (module.hero.distanceTo(ref) > DOCKING_DISTANCE) {
            module.movement.moveTo(ref);
            return;
        }
        module.movement.stop(false);
        if (throttle(DOCK_SETTLE_MS))
            return;

        CollectionMethod collect = module.config.questTypes.sellOresSettings.collectionMethod;
        if (collect == CollectionMethod.SKYLAB && targetOre != null) {
            int inCargo = getOreAmount(targetOre);
            ExecutionQuest quest = module.questManager.getCurrentQuest();
            if (quest != null && inCargo < (int) quest.remaining) {
                enterPhase(Phase.SKYLAB_REQUEST);
                return;
            }
        }

        enterPhase(Phase.OPEN_TRADE);
    }

    // =========================================================================
    // Phase: OPEN_TRADE
    // =========================================================================

    private void handleOpenTrade() {
        module.travelHandler.setWorkingMap(module.travelHandler.getCurrentMap());
        OreAPI oreApi = getOreApi();
        if (oreApi == null)
            return;

        SellingMethod method = effectiveSellingMethod();
        if (!openTradeForMethod(oreApi, method))
            return;
        sellIndex = 0;
        enterPhase(Phase.SELLING);
    }

    private boolean openTradeForMethod(OreAPI oreApi, SellingMethod method) {
        switch (method) {
            case BASE:
                return openBaseRefinery(oreApi);
            case PET_TRADING:
                if (!canUsePetTrader()) {
                    enterPhase(Phase.TRAVEL_TO_SELL);
                    return false;
                }
                preparePet();
                return oreApi.canSellOres();
            case HM7:
                if (!canUseTradeDrone()) {
                    enterPhase(Phase.TRAVEL_TO_SELL);
                    return false;
                }
                activateDrone();
                return oreApi.canSellOres();
            default:
                return false;
        }
    }

    private boolean openBaseRefinery(OreAPI oreApi) {
        CollectionMethod collect = module.config.questTypes.sellOresSettings.collectionMethod;
        String current = module.travelHandler.getCurrentMap();
        String faction = factionPrefix();
        Station.Refinery ref = resolveRefinery();

        if (ref != null && module.hero.distanceTo(ref) <= DOCKING_DISTANCE) {
            return oreApi.showTrade(true, ref) && oreApi.canSellOres();
        }
        if (collect == CollectionMethod.SKYLAB
                && (current.equals(faction + "-1") || current.equals(faction + "-8"))) {
            enterPhase(Phase.MOVE_TO_REFINERY);
            return false;
        }
        return oreApi.showTrade(true, ref) && oreApi.canSellOres();
    }

    // =========================================================================
    // Phase: SELLING
    // =========================================================================

    private void handleSelling() {
        OreAPI oreApi = getOreApi();
        if (oreApi == null)
            return;
        if (!oreApi.canSellOres()) {
            enterPhase(Phase.OPEN_TRADE);
            return;
        }
        if (throttle(SELL_TICK_MS))
            return;

        if (sellIndex < sellPlan.size()) {
            OreAPI.Ore ore = sellPlan.get(sellIndex);
            int amount = getOreAmount(ore);
            if (amount == 0 || (ore == OreAPI.Ore.PALLADIUM && amount < 15)) {
                sellIndex++;
                return;
            }
            oreApi.sellOre(ore);
        } else {
            enterPhase(Phase.CLOSE_TRADE);
        }
    }

    private void handleCloseTrade() {
        if (throttle(1000L))
            return;

        OreAPI oreApi = getOreApi();
        if (oreApi != null) {
            oreApi.showTrade(false, null);
        }

        ExecutionQuest quest = module.questManager.getCurrentQuest();
        if (quest == null || quest.remaining <= 0) {
            enterPhase(Phase.IDLE);
        } else {
            enterPhase(Phase.COLLECTING);
        }
    }

    // =========================================================================
    // Post-Quest (Called by ExecutionHandler when quest remaining <= 0)
    // =========================================================================

    /**
     * Called by ExecutionHandler when the quest progress reaches 0.
     * 
     * @return true if fully completed (safe to advance), false if still restoring
     *         ship.
     */
    public boolean handlePostQuest() {
        done();
        return true;
    }

    // =========================================================================
    // Phase management
    // =========================================================================

    private void enterPhase(Phase next) {
        phase = next;
        phaseEnterTime = System.currentTimeMillis();
        lastActionTime = 0;
    }

    private void done() {
        enterPhase(Phase.IDLE);
        shipSwitchDone = false;
        originalHangarId = null;
        skylabSuccessCount = 0;
        cargoAtSkylabRequest = 0;
        skylabRequestSentAt = 0;
    }

    // =========================================================================
    // Selling method helpers
    // =========================================================================

    private SellingMethod effectiveSellingMethod() {
        SellingMethod cfg = module.config.questTypes.sellOresSettings.sellingMethod;
        if (cfg == SellingMethod.PET_TRADING && !canUsePetTrader()) {
            return SellingMethod.BASE;
        }
        if (cfg == SellingMethod.HM7 && !canUseTradeDrone()) {
            return SellingMethod.BASE;
        }
        return cfg;
    }

    private String resolveSellMap(SellingMethod method) {
        CollectionMethod collect = module.config.questTypes.sellOresSettings.collectionMethod;
        String current = module.travelHandler.getCurrentMap();
        String faction = factionPrefix();

        if (collect == CollectionMethod.CARGO) {
            return resolveCargo(method, current, faction);
        }
        if (collect == CollectionMethod.SKYLAB) {
            return resolveSkylab(method, current, faction);
        }
        return current;
    }

    private String resolveCargo(SellingMethod method, String current, String faction) {
        if (method == SellingMethod.BASE) {
            return "5-2".equals(current) ? current : "5-2";
        }
        return (current.equals(faction + "-6") || current.equals(faction + "-7")) ? current : faction + "-6";
    }

    private String resolveSkylab(SellingMethod method, String current, String faction) {
        if (isFactionBaseMap(current, faction) || "5-2".equals(current))
            return current;
        if (method != SellingMethod.BASE && isFriendlyMap(current, faction))
            return current;
        return nearestBaseMap(faction);
    }

    /**
     * Returns true if the map is any base map for the given faction (x-1 through
     * x-8).
     */
    private boolean isFactionBaseMap(String mapId, String faction) {
        if (mapId == null)
            return false;
        return mapId.equals(faction + "-1") || mapId.equals(faction + "-8") || "5-2".equals(mapId);
    }

    private boolean isFriendlyMap(String mapId, String faction) {
        if (mapId == null)
            return false;
        if (mapId.startsWith("4-") || mapId.startsWith("5-"))
            return false;
        return mapId.startsWith(faction + "-");
    }

    private String nearestBaseMap(String faction) {
        String x1 = faction + "-1";
        String x8 = faction + "-8";
        String current = module.travelHandler.getCurrentMap();
        if (current.equals(x1) || current.equals(x8))
            return current;

        int distX1 = module.travelHandler.getShortestPath(current, x1);
        int distX8 = module.travelHandler.getShortestPath(current, x8);

        if (distX1 != -1 && distX8 != -1) {
            return distX8 < distX1 ? x8 : x1;
        } else if (distX8 != -1) {
            return x8;
        } else {
            return x1;
        }
    }

    // =========================================================================
    // PET & HM7
    // =========================================================================

    private boolean canUsePetTrader() {
        if (module.heroItems == null)
            return false;
        try {
            return module.heroItems.getItems(ItemCategory.PET).stream()
                    .anyMatch(i -> {
                        try {
                            return i.getId() != null && i.getId().toString().toUpperCase().contains("TRADER");
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canUseTradeDrone() {
        if (module.heroItems == null)
            return false;
        return module.heroItems.getItem(SelectableItem.Cpu.HMD_07, ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE)
                .filter(i -> i.getQuantity() > 0).isPresent();
    }

    private void preparePet() {
        if (module.pet == null)
            return;
        if (!module.pet.isValid()) {
            module.pet.setEnabled(true);
            return;
        }
        try {
            module.pet.setGear(PetGear.TRADER);
        } catch (Exception ignored) { // gear not available
        }
    }

    private void activateDrone() {
        if (module.heroItems == null)
            return;
        module.heroItems.useItem(SelectableItem.Cpu.HMD_07, ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE,
                ItemFlag.NOT_SELECTED);
    }

    // =========================================================================
    // Ore & cargo helpers
    // =========================================================================

    private OreAPI.Ore resolveOre(String name) {
        if (name == null)
            return null;
        switch (name.toLowerCase()) {
            case "prometium":
                return OreAPI.Ore.PROMETIUM;
            case "endurium":
                return OreAPI.Ore.ENDURIUM;
            case "terbium":
                return OreAPI.Ore.TERBIUM;
            case "prometid":
                return OreAPI.Ore.PROMETID;
            case "duranium":
                return OreAPI.Ore.DURANIUM;
            case "promerium":
                return OreAPI.Ore.PROMERIUM;
            default:
                return null;
        }
    }

    private int getOreAmount(OreAPI.Ore ore) {
        OreAPI api = getOreApi();
        return (api == null || ore == null) ? 0 : api.getAmount(ore);
    }

    private double cargoFill() {
        int max = module.stats.getMaxCargo();
        return max <= 0 ? 0 : (double) module.stats.getCargo() / max;
    }

    // =========================================================================
    // Entity helpers
    // =========================================================================

    private Box findNearestBox(String typeContains) {
        return module.entities.getBoxes().stream()
                .filter(b -> b.getTypeName().contains(typeContains))
                .min(Comparator.comparingDouble(b -> module.hero.distanceTo(b)))
                .orElse(null);
    }

    private Station.Refinery resolveRefinery() {
        if (cachedRefinery != null && cachedRefinery.isValid())
            return cachedRefinery;
        cachedRefinery = module.entities.getStations().stream()
                .filter(s -> s instanceof Station.Refinery && s.isValid())
                .map(s -> (Station.Refinery) s)
                .findFirst().orElse(null);
        return cachedRefinery;
    }

    private eu.darkbot.api.game.entities.Portal resolvePortal() {
        if (cachedPortal != null && cachedPortal.isValid())
            return cachedPortal;
        cachedPortal = module.entities.getPortals().stream()
                .filter(p -> p.isValid())
                .min(Comparator.comparingDouble(p -> module.hero.distanceTo(p)))
                .orElse(null);
        return cachedPortal;
    }

    // =========================================================================
    // Misc helpers
    // =========================================================================

    private String factionPrefix() {
        try {
            EntityInfo info = module.hero.getEntityInfo();
            if (info == null)
                return "1";
            switch (info.getFaction()) {
                case EIC:
                    return "2";
                case VRU:
                    return "3";
                default:
                    return "1";
            }
        } catch (Exception e) {
            return "1";
        }
    }

    private String resolveCurrentHangarId() {
        return module.shipSwitchHandler.resolveActiveHangarId();
    }

    private void postSkylab(String body) {
        if (!module.backpageAPI.isInstanceValid())
            return;
        try {
            String base = module.backpageAPI.getInstanceURI().toString().replaceAll("/$", "");
            java.net.URI uri = new java.net.URI(base + "/indexInternal.es");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Referer", base + "/indexInternal.es?action=internalSkylab");
                String sid = module.backpageAPI.getSid();
                if (sid != null) {
                    conn.setRequestProperty("Cookie", "dosid=" + sid);
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) { // HTTP error or network issue
        }
    }

    /**
     * GETs the Skylab page and extracts the reloadToken from the hidden input.
     * Returns null if unavailable.
     */
    private String fetchReloadToken() {
        if (!module.backpageAPI.isInstanceValid())
            return null;
        try {
            String base = module.backpageAPI.getInstanceURI().toString().replaceAll("/$", "");
            java.net.URI uri = new java.net.URI(base + "/indexInternal.es?action=internalSkylab");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                String sid = module.backpageAPI.getSid();
                if (sid != null)
                    conn.setRequestProperty("Cookie", "dosid=" + sid);
                try (java.io.InputStream is = conn.getInputStream()) {
                    String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("name=\"reloadToken\"\\s+value=\"([^\"]+)\"")
                            .matcher(html);
                    if (m.find())
                        return m.group(1);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) { // network or parse error
        }
        return null;
    }

    private boolean throttle(long ms) {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ms)
            return true;
        lastActionTime = now;
        return false;
    }

    private OreAPI getOreApi() {
        try {
            return module.api.requireAPI(OreAPI.class);
        } catch (Exception e) {
            return null;
        }
    }
}