package dev.shared.do_gamer.module.simple_galaxy_gate.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.shared.do_gamer.module.simple_galaxy_gate.SimpleGalaxyGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.AbgGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.AnyGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.DeltaGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.DseGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.EpsilonGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.GateHandler;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.HadesGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.KuiperGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.LowGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.TrinityTrialsGate;
import dev.shared.do_gamer.module.simple_galaxy_gate.gate.ZetaGate;
import eu.darkbot.api.game.galaxy.GalaxyGate;
import eu.darkbot.api.managers.StarSystemAPI;

public final class Maps {
    private Maps() {
        // Prevent instantiation
    }

    public static final List<Integer> ABG_IDS = List.of(51, 52, 53); // Alpha, Beta, Gamma gate IDs
    private static List<MapInfo> ggMaps;
    private static double mapCenterX = Defaults.MAP_CENTER_X;
    private static double mapCenterY = Defaults.MAP_CENTER_Y;
    private static double toleranceDistance = Defaults.TOLERANCE_DISTANCE;
    private static final Map<Integer, GateHandler> GATE_HANDLER_CACHE = new ConcurrentHashMap<>();

    public static double getMapCenterX() {
        return mapCenterX;
    }

    public static void setMapCenterX(double mapCenterX) {
        Maps.mapCenterX = mapCenterX;
    }

    public static double getMapCenterY() {
        return mapCenterY;
    }

    public static void setMapCenterY(double mapCenterY) {
        Maps.mapCenterY = mapCenterY;
    }

    public static double getToleranceDistance() {
        return toleranceDistance;
    }

    public static void setToleranceDistance(double toleranceDistance) {
        Maps.toleranceDistance = toleranceDistance;
    }

    private static final class MapInfo {
        public final int id;
        public final String name;
        public final GalaxyGate buildGate;
        public final List<String> accessBy;
        public final Supplier<GateHandler> gateHandlerSupplier;

        public MapInfo(int id, String name, GalaxyGate buildGate, List<String> accessBy,
                Supplier<GateHandler> gateHandlerSupplier) {
            this.id = id;
            this.name = name;
            this.buildGate = buildGate;
            this.accessBy = accessBy;
            this.gateHandlerSupplier = gateHandlerSupplier;
        }
    }

    static {
        List<MapInfo> list = new ArrayList<>();

        list.add(new MapInfo(0, "Alpha/Beta/Gamma", GalaxyGate.ALPHA, StarSystemAPI.HOME_MAPS, AbgGate::new));
        list.add(new MapInfo(55, "Delta", GalaxyGate.DELTA, StarSystemAPI.HOME_MAPS, DeltaGate::new));
        list.add(new MapInfo(70, "Epsilon", GalaxyGate.EPSILON, StarSystemAPI.HOME_MAPS, EpsilonGate::new));
        list.add(new MapInfo(71, "Zeta", GalaxyGate.ZETA, StarSystemAPI.HOME_MAPS, ZetaGate::new));
        list.add(new MapInfo(74, "Kappa", GalaxyGate.KAPPA, StarSystemAPI.HOME_MAPS, null));
        list.add(new MapInfo(75, "Lambda", GalaxyGate.LAMBDA, StarSystemAPI.HOME_MAPS, null));
        list.add(new MapInfo(76, "Kronos", null, StarSystemAPI.HOME_MAPS, null));
        list.add(new MapInfo(203, "Hades", GalaxyGate.HADES, StarSystemAPI.HOME_MAPS, HadesGate::new));
        list.add(new MapInfo(300, "Kuiper", GalaxyGate.KUIPER, StarSystemAPI.HOME_MAPS, KuiperGate::new));
        list.add(new MapInfo(200, "LoW", null, List.of("1-3", "2-3", "3-3"), LowGate::new));
        list.add(new MapInfo(499, "Trinity Trials", null, StarSystemAPI.BASE_MAPS, TrinityTrialsGate::new));
        list.add(new MapInfo(473, "DSE", null, StarSystemAPI.HOME_MAPS, DseGate::new));

        ggMaps = Collections.unmodifiableList(list);
    }

    /**
     * Finds mapInfo by gate ID.
     */
    private static MapInfo findMapInfo(int id) {
        // Map ID 0 represents Alpha/Beta/Gamma group
        int actualId = ABG_IDS.contains(id) ? 0 : id;
        return ggMaps.stream().filter(mi -> mi.id == actualId).findFirst().orElse(null);
    }

    /**
     * Gets the map name for the specified gate ID.
     */
    public static String mapNameForGate(Integer gateId) {
        if (gateId == null) {
            return null;
        }
        MapInfo info = findMapInfo(gateId);
        return info != null ? info.name : "Unknown";
    }

    /**
     * Checks if the specified gate ID is accessible from the current map.
     */
    public static boolean isGateOnCurrentMap(Integer gateId, StarSystemAPI startSystem) {
        String currentMapName = startSystem.getCurrentMap().getShortName();
        if (gateId == null || currentMapName == null) {
            return false;
        }
        MapInfo info = findMapInfo(gateId);
        if (info == null) {
            return false;
        }
        return info.accessBy.contains(currentMapName);
    }

    /**
     * Resolves the GalaxyGate enum for the specified gate ID.
     */
    public static GalaxyGate resolveBuildGate(Integer gateId) {
        if (gateId == null) {
            return null;
        }
        MapInfo info = findMapInfo(gateId);
        return info == null ? null : info.buildGate;
    }

    /**
     * Gets the GateHandler instance for the specified gate ID.
     */
    public static GateHandler getGateHandler(Integer gateId, SimpleGalaxyGate module) {
        final int cacheKey = gateId == null ? -1 : gateId;
        GateHandler handler = GATE_HANDLER_CACHE.computeIfAbsent(cacheKey, key -> createGateHandler(gateId));
        handler.setModule(module);
        return handler;
    }

    private static GateHandler createGateHandler(Integer gateId) {
        GateHandler handler = null;
        if (gateId != null) {
            MapInfo info = findMapInfo(gateId);
            if (info != null) {
                if (info.gateHandlerSupplier != null) {
                    handler = info.gateHandlerSupplier.get();
                } else {
                    handler = new GateHandler();
                }
            }
        }
        if (handler == null) {
            handler = new AnyGate();
        }
        return handler;
    }

    public static List<Integer> gateOptions() {
        return ggMaps.stream().map(mi -> mi.id).collect(Collectors.toList());
    }

}
