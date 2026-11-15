---
name: "darkbot-feature"
description: "Scaffolds new DarkBot features for SharedPlugin (Module, Behavior, Task, config, i18n, plugin.json). Invoke when asked to add/create a feature, module, behaviour, task or config for this plugin."
---

# DarkBot Feature (SharedPlugin)

Follow the existing code, do not invent new patterns.

## 1. Layout (mandatory)

Each contributor owns one package: `dev.shared.<devname>`. Never touch another dev's package.

```
src/main/java/dev/shared/<devname>/
  behaviour[s]/<feature>/<Feature>.java      # passive, runs every tick
  module[s]/<feature>/<Feature>.java        # takes over the bot with bot.setModule(this)
  task[s]/<feature>/<Feature>.java          # background/maintenance work
  config/<Feature>Config.java                # shared configs live flat in config/
src/main/resources/dev/shared/lang/strings_en.properties
src/main/resources/plugin.json               # the "features" array
```

Reference implementations to copy from:

| Need                                    | File                                            |
| --------------------------------------- | ----------------------------------------------- |
| Small behaviour                         | CrowdAvoidance.java                             |
| Behaviour that needs a state machine    | FastTravel.java                                 |
| Behaviour extending `TemporalModule`    | same as above (`extends TemporalModule`)        |
| Task with state machine + backpage      | Autobuy.java                                    |
| Trivial task                            | CloseAdOffer.java                               |
| Full module (loot + collector + status) | Spaceball.java                                  |
| Module with many submodules             | SimpleGalaxyGate.java                           |
| Config class                            | CrowdAvoidanceConfig.java, SpaceballConfig.java |

## 2. Pick the right type

| Type       | Runs                               | Takes control?              | Use when                                              |
| ---------- | ---------------------------------- | --------------------------- | ----------------------------------------------------- |
| `Behavior` | every tick, always                 | no                          | reacting to entities/state (avoid, heal, auto-refine) |
| `Module`   | every tick, always                 | yes (`bot.setModule(this)`) | the bot should do _this_ and nothing else             |
| `Task`     | every tick or `onBackgroundTick()` | no                          | maintenance, HTTP/backpage, housekeeping              |

A class may implement several (`Spaceball implements Module, Task, Configurable<X>, InstructionProvider`).

## 3. Templates

### Behaviour

```java
package dev.shared.<devname>.behaviour;

import dev.shared.<devname>.config.<Feature>Config;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.MovementAPI;

@Feature(name = "<Feature>", description = "<One sentence shown in the UI>.")
public class <Feature> implements Behavior, Configurable<<Feature>Config> {

    private static final long RETRY_DELAY_MS = 5_000L;
    private static final double TRIGGER_RADIUS = 1500.0;

    private final BotAPI bot;
    private final HeroAPI hero;
    private final EntitiesAPI entities;
    private final MovementAPI movement;
    private <Feature>Config config;

    public <Feature>(PluginAPI api) {
        this.bot = api.requireAPI(BotAPI.class);
        this.hero = api.requireAPI(HeroAPI.class);
        this.entities = api.requireAPI(EntitiesAPI.class);
        this.movement = api.requireAPI(MovementAPI.class);
    }

    @Override
    public void setConfig(ConfigSetting<<Feature>Config> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        if (!this.isActive()) {
            return;
        }
        this.doWork();
    }

    private boolean isActive() {
        if (this.config == null || !this.config.enabled) return false;
        if (TemporalModuleDetector.using(this.bot).isMapModule()) return false; // hero is travelling
        if (CaptchaBoxDetector.hasCaptchaBoxes(this.entities)) return false;    // captcha
        return this.config.other.runMode || true;
    }

    private void doWork() {
        // one action per tick
    }
}
```

Behaviour rules:

- Guard order: `config == null || !config.enabled` → captcha → travelling/map module → under attack → work.
- Use `TemporalModuleDetector` and `CaptchaBoxDetector` (in `dev.shared.utils`) instead of ad-hoc checks.
- Never loop/wait: one action per tick, re-evaluate next tick.

### Behaviour with a state machine (also the shape for Modules)

```java
private enum State { VALIDATING, OPENING_CPU, SELECTING, DONE }

private final Timer timer = Timer.get();     // eu.darkbot.util.Timer
private State state = State.VALIDATING;

@Override
public void onTickBehavior() {
    if (!this.isActive()) {
        this.resetState();
        return;
    }
    if (this.state != State.VALIDATING) {
        this.bot.setModule(this);            // keep control while working
        return;
    }
    this.handleValidating();
}

@Override
public void onTickModule() {
    switch (this.state) {
        case OPENING_CPU: this.handleOpeningCpu(); break;
        case SELECTING:   this.handleSelecting();   break;
        default:          this.resetState();
    }
}

@Override
public String getStatus() {
    return "<Feature>: " + this.state.name();
}

@Override
public boolean canRefresh() {
    return false;
}

private void resetState() {
    if (this.state == State.VALIDATING) return;
    this.state = State.VALIDATING;
    this.timer.disarm();
}
```

- `extends TemporalModule` (eu.darkbot.shared.modules.TemporalModule) gives you `isRestrictedModule()`, `goBack()` and the module lifecycle for free. Prefer it over re-implementing.
- Use `Timer` for every wait. Never `Thread.sleep`. Add a global timeout counter and call `bot.handleRefresh()` after N consecutive failures (see `FastTravel.isGlobalTimeout`).

### Module

```java
@Feature(name = "<Feature>", description = "<...>")
public final class <Feature> implements Module, Task, Configurable<<Feature>Config>, InstructionProvider {
    @Override public void onTickModule() { /* one step per tick */ }
    @Override public void onTickStopped() { /* cleanup, auto-resume */ }
    @Override public void onTickTask() { /* empty if unused */ }
    @Override public void onBackgroundTick() { /* HTTP / backpage work */ }
    @Override public String getStatus() { return "<Feature>: ..."; }
    @Override public String getStoppedStatus() { return null; }
    @Override public boolean canRefresh() { return true; }
    @Override public String instructions() { return "Recommended settings:\n- ..."; }
}
```

- Take control with `bot.setModule(this)`; stop with `bot.setRunning(false)`; never force a refresh while the hero is under attack or below ~10% HP.
- Reuse `eu.darkbot.shared.utils.MapTraveler` for travel and `eu.darkbot.shared.utils.PortalJumper` for jumps instead of writing new travel code.

### Task

```java
@Feature(name = "<Feature>", description = "<...>")
public final class <Feature> implements Task, Configurable<<Feature>Config> {
    @Override public void onTickTask() { /* runs on the foreground tick */ }
    @Override public void onBackgroundTick() { /* runs off the game thread; do HTTP here */ }
}
```

- `onTickTask()` runs on the game thread — never block it. HTTP/backpage work belongs in `onBackgroundTick()`.

## 4. Config class

One class per feature, in `dev.shared.<devname>.config`. Group related options in `public static` nested classes.

```java
package dev.shared.<devname>.config;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.annotations.Percentage;

@Configuration("<devname>.<feature>")
public class <Feature>Config {

    @Option("general.enabled")
    public boolean enabled = false;

    @Option("<devname>.<feature>.radius")
    @Number(min = 100, step = 50, max = 2000)
    public int radius = 300;

    @Option("<devname>.<feature>.threshold")
    @Percentage
    public double threshold = 0.8;

    @Option("<devname>.<feature>.mode")
    @Dropdown(options = ModeOptions.class)
    public String mode = ModeOptions.AUTO;

    public static class ModeOptions implements Dropdown.Options<String> {
        public static final String AUTO = "Auto";
        public static final String FOLLOW = "Follow";
        @Override public List<String> options() { return Arrays.asList(AUTO, FOLLOW); }
    }
}
```

Rules:

- Key format: `<devname>.<feature>.<group>.<field>` in snake_case. Field in camelCase.
- Always `@Option("general.enabled")` for the master toggle.
- `@Configuration("<devname>.<feature>")` gives the config panel its title.
- `@Number(min, max, step)`, `@Percentage`, `@Dropdown(options = X.class)`, `@StringLength`, `@Button` are available — check the annotation package before inventing validation.
- Profile dropdowns extend `eu.darkbot.shared.config.ProfileNames` (see `SpaceballConfig.ProfileOptions`).

## 5. Registration (forgetting this = dead feature)

1. Add the fully qualified class name to the `features` array in plugin.json:
   ```json
   "dev.shared.<devname>.behaviour.<feature>.<Feature>"
   ```
2. Add a `# ----- <devname> : <Feature> -----` block with every `@Option` key to strings_en.properties. Other languages come from Crowdin — do not edit them by hand.
3. Bump `version` in `plugin.json`.

## 6. Checklist before finishing

- [ ] `api.requireAPI(X.class)` in the constructor for every dependency; no `null` fields except `config`.
- [ ] One public class per file, filename == class name.
- [ ] No generic `catch (Exception)`; catch the specific type (e.g. `IOException`) and log with `System.out.println("<Feature>: ...")` **only** on state changes, never per tick spam.
- [ ] Guards present: `config == null`, captcha, travelling, under attack.
- [ ] Every wait uses `Timer`; a stuck state has a global timeout.
- [ ] Method bodies under ~20 lines, helpers extracted, `for...of` instead of `forEach`.
- [ ] Registered in `plugin.json` + English strings present.
- [ ] `gradlew.bat build` passes.

## 7. Hard rules (from CONTRIBUTING.md)

- Prefer `eu.darkbot.api.*` (DarkBotAPI) over `com.github.manolo8.darkbot.*`. Only drop to DarkBot internals when the API genuinely lacks the call, and keep it isolated.
- No external URLs for feature data — use the in-game backpage through `BackpageHelper`.
- No obfuscated code, no copy-paste from other plugins.
- Java 11, no new dependencies.
