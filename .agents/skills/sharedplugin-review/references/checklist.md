# SharedPlugin review checklist

Rules are grouped by review pass. Each row says what to flag and the default severity; use judgment, and downgrade when the consequence is hypothetical. "Don't flag" exists because this repository has history — flagging it again is noise.

## 1. Hard project rules

| Rule | Severity | Flag when |
| --- | --- | --- |
| No external URLs for feature data | BLOCKER | Any HTTP call or URL used to fetch gameplay data, configs, prices, maps or images. Use the in-game backpage through `BackpageHelper` instead. |
| No obfuscation | BLOCKER | Encoded strings, reflection used to hide a call, minified or generated code, unusual classloading. |
| Feature authored by the contributor | BLOCKER | Code that looks copied from another plugin (identical comments, names, or logic) and the author cannot explain it. Ask; do not accuse. |
| No new runtime dependencies | BLOCKER | A new entry in `build.gradle.kts`, a new jar, or an external binary. The project depends only on DarkBotAPI and DarkBot. |
| Respect package ownership | BLOCKER | A change inside `dev.shared.<dev>` that is not the PR author's package, without a stated agreement. Shared code under `dev.shared.utils` needs extra justification. |
| No `System.exit` / JVM shutdown | BLOCKER | The plugin stops the JVM. Stop the bot or the feature instead. |
| No secrets in the repo | BLOCKER | Tokens, passwords, cookies, session ids, personal paths. |
| No captcha bypass | BLOCKER | Logic that solves, skips or automates the captcha. Detect it and pause, never defeat it. |
| Do not weaken safety guards | BLOCKER | Removing or bypassing attack, HP, captcha or travel checks to make a feature "always run". |

**Don't flag:** the `update` and `download` URLs in `plugin.json`, links in comments or Javadoc, or the base repository URLs.

## 2. Runtime safety

| Rule | Severity | Flag when |
| --- | --- | --- |
| No forced refresh or stop under threat | BLOCKER | `bot.handleRefresh()` or `bot.setRunning(false)` on a path that can run while under attack, below a safe HP threshold, or with a captcha pending. |
| No blocking waits | BLOCKER | `Thread.sleep`, `wait`, `join`, blocking queues, or a busy loop. Use `Timer` and return from the tick. |
| Item use is validated | MAJOR | `useItem` without `ItemFlag.AVAILABLE/READY/USABLE` checks, or without a quantity check when the item is consumable. |
| Null and empty checks on game data | MAJOR | Dereferencing `getTarget()`, `getEntityInfo()`, `getClosestEntity()` or a dropdown selection without a guard. |
| Time units are consistent | MAJOR | Millisecond constants passed where seconds are expected (or the reverse), `* 1000` vs `* 1_000L` confusion, `int` overflow in a duration. |
| Retries are bounded | MAJOR | A retry counter that never resets, or a loop that can repeat forever on a stuck state. Add a global timeout and escalate to refresh. |
| Purchases and sales are guarded | MAJOR | Buying, selling, refining or equipping without validating item id, amount, cargo space or available credits. |
| Divisions and indexes are safe | MAJOR | Division by a game-provided value, `charAt`/`get(0)` on a possibly empty list, parsing of a backpage field without a fallback. |
| Collections do not grow unbounded | MAJOR | A map or list keyed by entity, timestamp or response that is never evicted. |

**Don't flag:** `handleRefresh` behind a working guard (captcha check, attack check, timeout counter). Name the guard in the report when you accept it.

## 3. Build and compatibility

| Rule | Severity | Flag when |
| --- | --- | --- |
| Java 11 only | BLOCKER | `record`, `sealed`, text blocks (`"""`), switch expressions (`case X ->`), pattern matching for `instanceof`. `var`, `List.of`, `isBlank`, `Optional.isEmpty` are fine. |
| The project compiles | BLOCKER | `.\gradlew.bat compileJava` fails. Paste the first error, not the whole log. |
| No raw types or unchecked warnings | MINOR | `List`, `Map` without generics, unchecked casts added by the change. |
| Resources are closed | MAJOR | A stream, reader, HTTP response, file handle or `Closeable` not in try-with-resources. |
| No static mutable state | MAJOR | A static non-final collection or field holding session state. It survives feature reloads and leaks across bot sessions. |
| `equals`/`hashCode` for map keys | MAJOR | A custom object used as a `HashMap`/`HashSet` key without both methods. |

## 4. Tick and thread model

| Rule | Severity | Flag when |
| --- | --- | --- |
| The game thread stays free | BLOCKER | File, network, backpage, sleep or heavy parsing inside `onTickBehavior`, `onTickModule` or `onTickTask`. |
| Background work runs on the background tick | MAJOR | HTTP or backpage work inside a foreground tick instead of `onBackgroundTick`. |
| One action per tick | MAJOR | A method that moves, attacks, uses an item and changes state in the same tick. Re-evaluate on the next tick. |
| No owned threads | MAJOR | `new Thread(...)` or an executor without a clear lifecycle and shutdown. |
| Shared state is safe | MAJOR | Fields written on `onBackgroundTick` and read on the game thread without `volatile`, `synchronized`, or an immutable snapshot. |
| Waits use `Timer` | MINOR | A new wait implemented with a counter or timestamp when a `Timer` would be clearer. `System.currentTimeMillis` for cooldowns is accepted. |

## 5. Feature lifecycle and guards

| Rule | Severity | Flag when |
| --- | --- | --- |
| Config is null-safe | MAJOR | `onTick*` reads `config` before checking `config == null` or the enabled flag. |
| Guard order | MAJOR | Enabled check is missing, or captcha/travel/attack guards are skipped and the feature can act during them. |
| Shared detectors are used | MINOR | Ad-hoc captcha or module detection instead of `CaptchaBoxDetector` and `TemporalModuleDetector`. |
| `onTickStopped` cleans up | MAJOR | A Module leaves timers armed, GUIs open, or control taken after it is stopped, and does not restore the previous module. |
| `canRefresh` is honest | MAJOR | Returns `true` while the feature cannot safely resume, or `false` when it can. |
| Status is useful | NIT | `getStatus()` returns a constant or null where the UI expects progress. `getStoppedStatus()` returning `null` is accepted. |
| Behaviours do not stop the bot | BLOCKER | A `Behavior` calls `bot.setRunning(false)` or forces a refresh outside an explicit emergency path with guards. |
| Modules restore control | MAJOR | `bot.setModule(this)` without a path back (`goBack`, previous module, or `onTickStopped`). |

## 6. Module ownership and control

| Rule | Severity | Flag when |
| --- | --- | --- |
| Control is taken deliberately | MAJOR | A Behaviour calls `bot.setModule(this)` without a state machine that keeps control only while working. |
| No module fighting | MAJOR | The feature re-takes control every tick while another module is legitimately active. |
| `TemporalModule` is reused | MINOR | Travel-like behaviour re-implements `isRestrictedModule`, `goBack` or the module lifecycle instead of extending `TemporalModule`. |
| Existing navigation is reused | MINOR | New travel code where `MapTraveler` or `PortalJumper` already solves it. |

## 7. Config and defaults

| Rule | Severity | Flag when |
| --- | --- | --- |
| Safe defaults | MAJOR | A destructive, expensive or account-risky action is enabled by default (auto-buy, auto-sell, auto-refresh, forced travel). |
| Master toggle exists | MINOR | A new feature has no `enabled` option and cannot be turned off. |
| Numeric bounds | MINOR | A numeric option has no `@Number(min, max, step)`, or the range allows a value that breaks the feature. |
| Percentages use `@Percentage` | NIT | A 0–100 option is a bare number. |
| No config for constants | MINOR | An option that must never change (item id, map id, internal delay) is exposed to the user. |
| Existing keys are not renamed | MAJOR | An `@Option` key is renamed or removed, which silently loses the translation and the user's saved value. |
| Keys stay namespaced | MINOR | A key outside `<dev>.<feature>.` or one that collides with another feature. Match the surrounding casing; it is not uniformly snake_case. |

## 8. Registration and i18n

| Rule | Severity | Flag when |
| --- | --- | --- |
| `@Feature` is registered | BLOCKER | The class is not in the `features` array of `plugin.json`. A feature that is not registered never loads. |
| Deleted features are unregistered | MAJOR | A removed class is still listed in `plugin.json`. |
| English strings exist | MAJOR | An `@Option` key has no entry in `strings_en.properties`; the UI shows the raw key. |
| Only English is edited | MAJOR | Another language file is changed by hand. Crowdin owns the rest. |
| File name matches the public class | MAJOR | `Foo.java` declares `public class Bar`. |
| Package matches the folder | MAJOR | `package dev.shared.a.b` in a folder that is not `dev/shared/a/b`. |
| Feature name and description are user-facing | NIT | The description is empty, says "test", or does not explain what the feature does. |

See `references/registration-and-i18n.md` for the exact key format and the manual checks.

## 9. Naming and structure

| Rule | Severity | Flag when |
| --- | --- | --- |
| One public class per file | MAJOR | Two public top-level types in one file, or a file name that does not match. |
| Naming conventions | MINOR | Classes not PascalCase, methods/variables not camelCase, constants not `UPPER_CASE_WITH_UNDERSCORES`. |
| Feature layout | MINOR | A feature is not under `behaviour[s]/`, `module[s]`, `task[s]`, with its config in `config/` as the repo does today. |
| Import order | NIT | `java.*` is not first, or the remaining root packages are not alphabetical (`com`, `dev`, `eu`) with a blank line between groups. Do not reorder a file the change does not touch. |
| `@Override` is present | NIT | An interface or base-class method is overridden without it. |
| No dead code | MINOR | Commented-out blocks, unused fields/imports, unreachable branches, leftover debug prints added by the change. |
| Methods stay focused | MINOR | A new or heavily modified method is over ~20 lines or nests more than 3 levels. Legacy length alone is not a finding. |

## 10. Error handling and logging

| Rule | Severity | Flag when |
| --- | --- | --- |
| Catch the specific type | MAJOR | A new `catch (Exception)` / `catch (Throwable)` without a documented boundary reason. HTTP and parse boundaries may justify it; then the log must say what failed. |
| Do not swallow | MAJOR | An empty catch, or a catch that continues as if the operation succeeded. |
| Log with context | MINOR | Log text without the feature name, the failing action, or the useful value. Use `System.out.println("<Feature>: ...")`. |
| Do not log every tick | MINOR | A log line on a path that runs each tick, or on a retry that repeats every tick. Log state changes instead. |
| No stack traces | MINOR | `printStackTrace()` instead of a message. |
| Failure does not kill the feature | MAJOR | An exception leaves the state machine stuck, the timer armed forever, or the module in control. |

## 11. Performance and data structures

| Rule | Severity | Flag when |
| --- | --- | --- |
| No per-tick allocation storms | MAJOR | New lists, streams, lambdas or regex per tick over a large entity collection. Setup and config code is fine. |
| Right collection | MINOR | `LinkedList` for indexed access, `ArrayList` for membership checks, `HashMap` iterated in a hot path where a set would do. |
| Presize when the size is known | NIT | A collection built from a known list or count without an initial capacity. |
| Entity state is not cached across ticks | MAJOR | A field holds a `Ship`, `Npc`, `Box`, `Portal` or `Station` and is used later without revalidation. Entities change on map load; re-read from `EntitiesAPI` or invalidate on map change. |
| Expensive lookups are cached per tick | MINOR | The same `getClosestEntity`/`getPortals` scan repeated several times in one tick. |
| Avoid O(n²) over entities | MAJOR | A nested loop over all entities on every tick. |

## Cross-cutting evidence rules

- A finding without `path:line` is a question, not a finding.
- A "will not compile" claim needs a reproduced compiler error.
- A "thread unsafe" claim needs the two call sites: the writer and the reader.
- A "this API does not exist" claim needs the API check, not memory.
- If the same issue appears ten times, report it once with a list of locations.
