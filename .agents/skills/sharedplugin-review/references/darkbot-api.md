# DarkBotAPI reference for reviewers

Use this when a change touches entities, managers, modules, behaviours, tasks, timers or navigation. The goal is to judge the change against the API that actually exists, not against memory.

## 1. API over internals

| Layer | Package | Stance |
| --- | --- | --- |
| Public API | `eu.darkbot.api.*` | Preferred. Extensions, managers, config, entities, items. |
| Public utilities | `eu.darkbot.util.*` | Preferred. `Timer`, math helpers. |
| Shared implementation | `eu.darkbot.shared.*` | Accepted. `TemporalModule`, `MapTraveler`, `PortalJumper`, `ProfileNames`. Verify the class still exists in the pinned DarkBot commit before requiring it. |
| DarkBot internals | `com.github.manolo8.darkbot.*` | Legacy. Only for something the API genuinely lacks, isolated behind a small adapter, with a comment explaining why. |

The repository already has direct internals in `PetGearHelper`, `BackpageHelper`, `Autobuy`, `SimpleGalaxyGate`, `SolarisInc`, `AutoRefin`, `CloseAdOffer`, `GateNpcFlag`, `LowGate` and `ZetaGate`. Their presence is not a defect. A **new** import is a finding when an API equivalent exists; when it is genuinely missing, ask for the reason and isolation instead of demanding a rewrite.

## 2. Managers used in this repo

Bind them once in the constructor with `api.requireAPI(X.class)`; never inside a tick, never in a static field.

| Manager | Use for |
| --- | --- |
| `BotAPI` | Running state, current module, `setModule`, `handleRefresh`, tick time. |
| `HeroAPI` | The hero ship: map, position, mode, target, effects, HP/shield, entity info. |
| `EntitiesAPI` | Npcs, players, ships, boxes, portals, stations, mines, relays, barriers. |
| `MovementAPI` | `moveTo`, `isMoving`, `jumpPortal`, preferred zone, distances. |
| `AttackAPI` | Target, lock, attack, stop attack, blacklist, status. |
| `HeroItemsAPI` | Item lookup and use with `ItemFlag`s. |
| `ConfigAPI` | Reading and writing bot configuration, profiles. |
| `StarSystemAPI` | Map and star-system data. |
| `StatsAPI` | Session stats. |
| `GameScreenAPI` | GUI visibility and clicks. |
| `GroupAPI` | Group membership. |
| `PetAPI` | PET state, gear and repair. |
| `RepairAPI` | Repair logic. |
| `OreAPI` | Ore and refining data. |
| `BackpageAPI` | Backpage session state. |
| `EventBrokerAPI` | Subscribing to game events. |
| `ExtensionsAPI` | Installed features and extension lookup. |
| `GalaxySpinnerAPI` | Galaxy gate spinner. |
| `EscortAPI`, `GauntletPlutusAPI`, `EternalBlacklightGateAPI` | Event-specific gate data. |

`requireAPI` throws when the manager is missing; `getAPI` returns null. Prefer `requireAPI` in the constructor so a missing dependency fails loudly at load time instead of becoming a null pointer mid-fight.

## 3. Feature lifecycle contracts

| Interface / method | Thread | Contract to check |
| --- | --- | --- |
| `Behavior.onTickBehavior` | game | Every tick, always. Must be cheap and non-blocking. |
| `Module.onTickModule` | game | Only while this module is the active one. Takes over the bot. |
| `Module.onTickStopped` | game | Cleanup, restore control, disarm timers, close GUIs. |
| `Task.onTickTask` | game | Every tick, foreground. Same rules as a Behaviour. |
| `Task.onBackgroundTick` | background | Off the game thread. HTTP and backpage work belongs here. |
| `Configurable.setConfig` | load | Receives a `ConfigSetting`; call `getValue()` and keep the config object. Can be called again. |
| `InstructionProvider.instructions` | UI | Static text; no state mutation. |
| `canRefresh` | UI | `true` only when the feature can safely resume after a refresh. |
| `getStatus` / `getStoppedStatus` | UI | Human-readable progress. `getStoppedStatus` may return null. |

A class may implement several at once. `Spaceball` implements `Module`, `Task`, `Configurable` and `InstructionProvider`; that is normal, not a smell.

## 4. Timer

```java
private final Timer timer = Timer.get();   // or Timer.get(long initialDelay)

if (this.timer.isArmed() && !this.timer.isActive()) {
    return;                                // still waiting
}
this.timer.disarm();
// act
```

Real API: `get()`, `get(long)`, `getRandom(long, long)`, `activate(long)`, `tryActivate(long)`, `isActive()`, `isInactive()`, `isArmed()`, `disarm()`, `tryDisarm()`, `getRemainingFuse()`.

- Use `Timer` for any wait. A counter that increments every tick and is compared to a magic number is a MINOR, not a blocker, but it hides intent.
- `System.currentTimeMillis()` is accepted for cooldown comparisons; the repo uses it widely. Do not rewrite it into a `Timer` unless the code is being changed anyway.
- A wait without a global timeout is a MAJOR: the feature can stay stuck forever. Escalate with `bot.handleRefresh()` after N consecutive failures.

## 5. Entities

`EntitiesAPI` exposes `getNpcs()`, `getPlayers()`, `getShips()`, `getBoxes()`, `getPortals()`, `getStations()`, `getMines()`, `getRelays()`, `getBarriers()`, `getMists()`, `getObstacles()`, `getAll()`.

- These return live collections for the current tick. Re-read them each tick; do not store the collection.
- Do not hold an `Entity`, `Ship`, `Npc`, `Box`, `Portal` or `Station` in a field and use it many ticks later. Map changes invalidate them. If caching is unavoidable, invalidate on map change and revalidate before use.
- Nullable accessors exist (`getEntityInfo()`, `getTarget()`, `getLocalTarget()`). Guard them.
- `EntityInfo.isEnemy()`, `getClanId()`, `getClanTag()` are the supported checks for friendly/enemy decisions.

## 6. Items

```java
this.items.getItem(Special.EMP_01, ItemFlag.AVAILABLE, ItemFlag.READY, ItemFlag.USABLE)
        .filter(item -> item.getQuantity() > 0)
        .ifPresent(item -> this.items.useItem(Special.EMP_01, ItemFlag.USABLE, ItemFlag.READY));
```

- `getItem` returns `Optional`; `useItem` returns an `ItemUseResult`. Check one of them before assuming the item fired.
- Pass the flags that match intent. `ItemFlag.NOT_SELECTED` matters for items that must not be equipped.
- Rate-limit retries; a `useItem` every tick on a cooldown item is wasted work and log spam.

## 7. Movement, attack, hero

- `MovementAPI.moveTo(x, y)` returns void and does not wait. One move per tick, then re-evaluate.
- `MovementAPI.jumpPortal(portal)` is the supported jump. Do not simulate clicks when this exists.
- `AttackAPI.setTarget(lockable)`, `tryLockAndAttack()`, `stopAttack()`, `setBlacklisted(ms)`, `isAttacking()`, `hasTarget()`.
- `HeroAPI.setRunMode()`, `setRoamMode()`, `setAttackMode(npc)` change ship mode. They return a boolean; ignoring the result is acceptable but the mode may not have changed.
- Before forcing a refresh or stopping, check `attack.isAttacking()`, hero HP/shield, captcha and travel state. A refresh in the middle of a fight is a bug, not a feature.

## 8. Shared utilities already in this repository

Prefer these over new ad-hoc code. `dev.shared.utils`:

| Class | Purpose |
| --- | --- |
| `CaptchaBoxDetector.hasCaptchaBoxes(entities)` | Detects the captcha boxes. |
| `TemporalModuleDetector.using(bot).isMapModule()` / `.isTemporal()` / `.isTemporalNotMap()` | Travel/module state. |
| `CustomSafetyFinder` | Safe-spot search around the hero. |
| `ConfigHtmlInstructions` | Renders instructions in the config panel. |

`dev.shared.do_gamer.utils`: `BackpageHelper` (hangar data and changes), `PetGearHelper` (PET gear and passive mode), `ServerTimeHelper` (server clock).

`eu.darkbot.shared.utils`: `MapTraveler` for travel, `PortalJumper` for jumps.

A change that reimplements one of these is a MINOR at least, and a MAJOR when the reimplementation drops a guard the shared version has.

## 9. Verifying a signature

Do not guess. Any of these settles it:

```powershell
# 1. Sources jar, if present
Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\eu.darkbot.DarkBotAPI" -Recurse -Filter "*-sources.jar"

# 2. Exact signatures from the pinned jar
javap -classpath "<path-to-darkbot-api-0.9.11.jar>" eu.darkbot.api.managers.HeroAPI
javap -classpath "<path-to-DarkBot-e787b48c23.jar>" eu.darkbot.util.Timer

# 3. Compile the project; the compiler is the final authority
.\gradlew.bat compileJava
```

Pinned versions live in `build.gradle.kts`: `eu.darkbot.DarkBotAPI:darkbot-impl:0.9.11` and `eu.darkbot:DarkBot:e787b48c23`. This repository targets Java 11, so the compile step catches unsupported syntax too.

## 10. Mistakes that look correct

| Looks fine | Why it is not |
| --- | --- |
| `api.requireAPI(X.class)` inside `onTickBehavior` | Repeated lookup and a late failure. Bind once in the constructor. |
| `api.requireAPI(SomeImpl.class)` | `requireAPI` only accepts API **interfaces** and throws otherwise. Use the interface type. |
| `api.getAPI(X.class)` for a required dependency | `getAPI` catches the failure, prints a stack trace and returns null, so the null pointer moves to the first use. Use `requireAPI` and fail at load time. |
| `this.entities.getNpcs().size() >= 1` then `get(0)` | The collection is not a `List` and the entity may disappear next tick. Iterate and re-check. |
| Caching a `Ship` field and calling `getX()` next tick | The entity can be stale after a map load. |
| `bot.handleRefresh()` after a timeout counter reset | If the counter resets, the refresh never triggers. Count consecutive failures and reset only on success. |
| `System.currentTimeMillis()` compared against a config value in seconds | Unit mismatch. |
| A `Behavior` calling `bot.setModule(this)` every tick | It fights every other module. Take control only while working, and restore it. |
| `catch (Exception)` around the whole tick | Hides the failure and often leaves the state machine stuck. |
