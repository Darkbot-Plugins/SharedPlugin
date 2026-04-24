package dev.shared.do_gamer.module.simple_galaxy_gate.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.shared.do_gamer.utils.ConfigHtmlInstructions;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.annotations.Readonly;
import eu.darkbot.api.config.types.PercentRange;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.shared.config.ProfileNames;

public class SimpleGalaxyGateConfig {
    private static final String LINE_BREAK = "<br><br>";

    /**
     * Instructions and explanations for the player,
     * displayed at the top of the config.
     */
    public static class Instructions extends ConfigHtmlInstructions {
        @Override
        public String getEditorValue() {
            StringBuilder html = new StringBuilder();
            html.append(buildList("Ship config and formation:",
                    "Offensive config: used for attacking NPCs in the gate.",
                    "Roam config: used for moving between far targets.",
                    "Run config: used at the <b>end of wave / gate</b> when there are no NPCs."));
            html.append(LINE_BREAK);
            html.append(buildList("NPC auto populate:",
                    "NPCs may be <b>automatically</b> configured using built-in gate presets.",
                    "Auto-populated values: radius, and optionally priority and flags.",
                    "NPCs with the <b>Kill</b> checkbox enabled are never overridden."));
            html.append(LINE_BREAK);
            html.append(buildList("NPC extra flags:",
                    "Kamikaze: allows Kamikaze for this NPC when the feature is enabled.",
                    "Stick to Target: to stick to the current target, don't switch away."));
            html.append(LINE_BREAK);
            html.append(buildList("Gate-specific notes:",
                    "DSE gate requires <b>manual</b> action to select ship and reset waves."));
            return html.toString();
        }
    }

    @Option("")
    @Readonly
    @Editor(Instructions.class)
    public String instructions = null;

    @Option("do_gamer.simple_galaxy_gate.gate")
    @Dropdown(options = GalaxyGateDropdown.class)
    public Integer gateId = null;

    @Option("do_gamer.simple_galaxy_gate.any_gate")
    public AnyGateSettings anyGate = new AnyGateSettings();

    @Option("do_gamer.simple_galaxy_gate.kamikaze")
    public KamikazeSettings kamikaze = new KamikazeSettings();

    @Option("do_gamer.simple_galaxy_gate.builder")
    public BuilderSettings builder = new BuilderSettings();

    @Option("do_gamer.simple_galaxy_gate.other")
    public OtherSettings other = new OtherSettings();

    /////////////////////////////////////////////////////////////////
    //////// Dropdown Options and Sub-Settings Classes //////////////
    /////////////////////////////////////////////////////////////////

    public static class GalaxyGateDropdown implements Dropdown.Options<Integer> {
        @Override
        public List<Integer> options() {
            List<Integer> options = new ArrayList<>();
            options.add(null);
            options.addAll(Maps.gateOptions());
            return options;
        }

        @Override
        public String getText(Integer option) {
            if (option == null) {
                return " -any- ";
            }
            String mapName = Maps.mapNameForGate(option);
            if (mapName != null) {
                if (mapName.equals("DSE")) {
                    return "DSE (manual)";
                }
                return mapName;
            }
            return String.valueOf(option);
        }

        @Override
        public String getTooltip(Integer option) {
            if (option != null) {
                String mapName = Maps.mapNameForGate(option);
                if (mapName != null && mapName.equals("DSE")) {
                    return "Requires manual action to select ship and reset waves.";
                }
            }
            return null;
        }
    }

    /**
     * Settings applied when "-any-" gate is selected
     */
    public static class AnyGateSettings {
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return "NPC radius <b>overrides</b> any attack radius. Set to 0 to use custom radius.";
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("do_gamer.simple_galaxy_gate.any_gate.npc_radius")
        @Number.Disabled(value = 0)
        @Number(min = 0, max = 1000, step = 10)
        public int npcRadius = 560;

        @Option("do_gamer.simple_galaxy_gate.any_gate.kill_all_npcs")
        public boolean killAllNpcs = true;

        @Option("do_gamer.simple_galaxy_gate.any_gate.jump_to_next_map")
        public boolean jumpToNextMap = false;

        @Option("do_gamer.simple_galaxy_gate.any_gate.move_to_center")
        public boolean moveToCenter = false;
    }

    /**
     * Settings for kamikaze behavior in the gate
     */
    public static class KamikazeSettings {
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return this.buildList(null,
                        "Kamikaze is only used for NPCs marked with the <b>Kamikaze</b> flag.",
                        "PET <b>must</b> have equipped Kamikaze gear in <b>both</b> configs.",
                        "Kamikaze will only trigger if PET HP is below <b>20%</b>.",
                        "Cooldown depends on Kamikaze gear <b>level</b>. Set the equipped one.");
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("general.enabled")
        public boolean enabled = false;

        @Option("do_gamer.simple_galaxy_gate.kamikaze.min_npcs")
        @Number(min = 1, max = 20, step = 1)
        public int minNpcs = 5;

        @Option("do_gamer.simple_galaxy_gate.kamikaze.cooldown")
        @Dropdown(options = CooldownLevelDropdown.class)
        public CooldownLevel cooldown = CooldownLevel.LEVEL_3;

        @Option("do_gamer.simple_galaxy_gate.kamikaze.hp_range")
        @Percentage
        public PercentRange hpRange = PercentRange.of(0.6, 0.95);

        @Option("do_gamer.simple_galaxy_gate.kamikaze.shield_range")
        @Percentage
        public PercentRange shieldRange = PercentRange.of(0.4, 0.95);

        @Option("do_gamer.simple_galaxy_gate.kamikaze.ship_mode")
        public ShipMode shipMode = ShipMode.of(HeroAPI.Configuration.FIRST, null);

        /**
         * Enum representing cooldown levels for kamikaze behavior.
         */
        public enum CooldownLevel {
            LEVEL_1(120, "Level 1 (2min)"),
            LEVEL_2(60, "Level 2 (1min)"),
            LEVEL_3(30, "Level 3 (30sec)");

            public final int seconds;
            public final String label;

            CooldownLevel(int seconds, String label) {
                this.seconds = seconds;
                this.label = label;
            }
        }

        /**
         * Dropdown options for selecting kamikaze cooldown levels based on gear
         */
        public static class CooldownLevelDropdown implements Dropdown.Options<CooldownLevel> {
            @Override
            public List<CooldownLevel> options() {
                return List.of(CooldownLevel.values());
            }

            @Override
            public String getText(CooldownLevel option) {
                return option.label;
            }
        }
    }

    /**
     * Settings for the Galaxy Gate Builder
     */
    public static class BuilderSettings {
        public static final int WAIT_TIME_SPIN_1 = 100;
        public static final int WAIT_TIME_SPIN_5 = 200;
        public static final int WAIT_TIME_SPIN_10 = 250;
        public static final int WAIT_TIME_SPIN_100 = 500;

        @Option("general.enabled")
        public boolean enabled = false;

        @Option("do_gamer.simple_galaxy_gate.builder.use_ee_only")
        public boolean useExtraEnergyOnly = false;

        @Option("do_gamer.simple_galaxy_gate.builder.use_multi_at")
        @Dropdown(options = MultiAtDropdown.class)
        public int useMultiAt = 2;

        @Option("do_gamer.simple_galaxy_gate.builder.max_spin_cost")
        @Number(min = 55, max = 100)
        public int maxSpinCost = 100;

        @Option("do_gamer.simple_galaxy_gate.builder.min_uri")
        @Number(min = 1_000, max = 100_000_000, step = 10_000)
        public int minUriBalance = 1_000;

        @Option("do_gamer.simple_galaxy_gate.builder.spins_5")
        @Number.Disabled(value = 0.0)
        @Percentage
        public double spins5 = 0.0;

        @Option("do_gamer.simple_galaxy_gate.builder.spins_10")
        @Number.Disabled(value = 0.0)
        @Percentage
        public double spins10 = 0.0;

        @Option("do_gamer.simple_galaxy_gate.builder.spins_100")
        @Number.Disabled(value = 0.0)
        @Percentage
        public double spins100 = 0.0;

        @Option("do_gamer.simple_galaxy_gate.builder.speed")
        @Dropdown(options = BuilderSpeedDropdown.class)
        public BuilderSpeed speed = BuilderSpeed.NORMAL;

        @Option("do_gamer.simple_galaxy_gate.builder.build_until")
        @Dropdown(options = BuildUntilDropdown.class)
        public BuildUntil buildUntil = BuildUntil.PLACED;

        @Option("do_gamer.simple_galaxy_gate.builder.switch_ship")
        public SwitchShipSettings switchShip = new SwitchShipSettings();

        ///////////////////////////////////////////////////////////////////
        //////// Dropdown Options and Sub-Settings Classes ////////////////
        ///////////////////////////////////////////////////////////////////

        public static class MultiAtDropdown implements Dropdown.Options<Integer> {
            @Override
            public List<Integer> options() {
                return List.of(2, 3, 4, 5, 6);
            }

            @Override
            public String getText(Integer option) {
                return option == null ? "" : "x" + option;
            }
        }

        /**
         * Build until options for the builder
         */
        public enum BuildUntil {
            PLACED("1 of 2"),
            COMPLETED("2 of 2");

            public final String label;

            BuildUntil(String label) {
                this.label = label;
            }
        }

        public static class BuildUntilDropdown implements Dropdown.Options<BuildUntil> {
            @Override
            public List<BuildUntil> options() {
                return List.of(BuildUntil.values());
            }

            @Override
            public String getText(BuildUntil option) {
                return option.label;
            }
        }

        /**
         * Speed options for the builder
         */
        public enum BuilderSpeed {
            VERY_FAST(1, "Very Fast"),
            FAST(2, "Fast"),
            NORMAL(3, "Normal"),
            SLOW(4, "Slow"),
            VERY_SLOW(5, "Very Slow");

            public final int multiplier;
            public final String label;

            BuilderSpeed(int multiplier, String label) {
                this.multiplier = multiplier;
                this.label = label;
            }
        }

        public static class BuilderSpeedDropdown implements Dropdown.Options<BuilderSpeed> {
            @Override
            public List<BuilderSpeed> options() {
                return List.of(BuilderSpeed.values());
            }

            @Override
            public String getText(BuilderSpeed option) {
                return option.label;
            }
        }

        /**
         * Settings for switching ships during gate building
         */
        public static class SwitchShipSettings {
            @Option("general.enabled")
            public boolean enabled = false;

            @Option("do_gamer.simple_galaxy_gate.builder.switch_ship.ship_for_gate")
            @Dropdown(options = ShipDropdown.class)
            public String shipForGate = null;

            @Option("do_gamer.simple_galaxy_gate.builder.switch_ship.ship_for_build")
            @Dropdown(options = ShipDropdown.class)
            public String shipForBuild = null;
        }

        /**
         * Dropdown options for selecting ships
         */
        public static class ShipDropdown implements Dropdown.Options<String> {
            private static Map<String, String> ships = new LinkedHashMap<>();

            public static Map<String, String> getShips() {
                return ships;
            }

            @Override
            public List<String> options() {
                return getShips().keySet().stream().collect(Collectors.toList());
            }

            @Override
            public String getText(String option) {
                String name = getShips().getOrDefault(option, null);
                if (name == null) {
                    return "";
                }
                return name.replaceFirst("ship_", "").replace("-plus", " plus").toUpperCase();
            }

            public static String getShipName(String hangarId) {
                return getShips().getOrDefault(hangarId, null);
            }
        }
    }

    public static class OtherSettings {
        public static class Instructions extends ConfigHtmlInstructions {
            @Override
            public String getEditorValue() {
                return this.buildList(null,
                        "These settings affect general features, not specific gates.",
                        "Stick to any target: don't switch away from any gate target.",
                        "Switch profile: after the gate ends or when no resources remain.");
            }
        }

        @Option("")
        @Readonly
        @Editor(Instructions.class)
        public String instructions = null;

        @Option("do_gamer.simple_galaxy_gate.other.stick_to_any_target")
        public boolean stickToAnyTarget = false;

        @Option("do_gamer.simple_galaxy_gate.other.pet_collect")
        @Dropdown(options = PetCollectDropdown.class)
        public PetCollectType petCollect = PetCollectType.NONE;

        @Option("do_gamer.simple_galaxy_gate.other.bot_profile")
        @Dropdown(options = ProfileOptions.class)
        public String botProfile = null;

        @Option("do_gamer.simple_galaxy_gate.other.only_when_not_available")
        public boolean onlyWhenNotAvailable = false;

        @Option("do_gamer.simple_galaxy_gate.other.stuck_in_gate_timer")
        @Number.Disabled(value = 0)
        @Number(min = 0, max = 5, step = 1)
        public int stuckInGateTimerMinutes = 1;

        @Option.Ignore() // Only for dev needs, not user-facing
        @Option("do_gamer.simple_galaxy_gate.other.debug_info")
        @Dropdown(options = DebugInfoDropdown.class)
        public DebugInfoType debugInfo = DebugInfoType.NONE;

        /**
         * Types of pet collect behavior
         */
        public enum PetCollectType {
            NONE("Disabled"),
            ANY("Any box exists"),
            ODD("Odd priority box exists");

            public final String label;

            PetCollectType(String label) {
                this.label = label;
            }
        }

        /**
         * Dropdown options for selecting pet collect type
         */
        public static class PetCollectDropdown implements Dropdown.Options<PetCollectType> {
            @Override
            public List<PetCollectType> options() {
                return List.of(PetCollectType.values());
            }

            @Override
            public String getText(PetCollectType option) {
                return option.label;
            }
        }

        /**
         * Dropdown options for selecting bot profiles
         */
        public static class ProfileOptions extends ProfileNames {
            public ProfileOptions(ConfigAPI api) {
                super(api);
            }

            @Override
            public Collection<String> options() {
                // Start with an explicit null option so the dropdown allows no-selection.
                List<String> list = new ArrayList<>();
                list.add(null);
                Collection<String> parent = super.options();
                if (parent != null) {
                    list.addAll(parent);
                }
                return list;
            }
        }

        /**
         * Types of debug information to display
         */
        public enum DebugInfoType {
            NONE,
            POSITION,
            PORTALS;

            public String label() {
                return name().toLowerCase();
            }
        }

        /**
         * Dropdown options for selecting debug info type
         */
        public static class DebugInfoDropdown implements Dropdown.Options<DebugInfoType> {
            @Override
            public List<DebugInfoType> options() {
                return List.of(DebugInfoType.values());
            }

            @Override
            public String getText(DebugInfoType option) {
                return option.label();
            }
        }
    }
}