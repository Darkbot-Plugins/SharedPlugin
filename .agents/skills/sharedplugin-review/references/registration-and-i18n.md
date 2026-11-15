# Registration and i18n

A feature can compile, look correct, and still be dead because it is not registered. A config option can work and still show a raw key like `do_gamer.foo.bar` in the UI. Check both on every review that adds or changes a feature.

## `plugin.json`

Located at `src/main/resources/plugin.json`.

```json
{
  "name": "SharedPlugin",
  "author": "Several Devs",
  "version": "0.12.23",
  "minVersion": "1.131.6 beta 3",
  "supportedVersion": "1.132.0 alpha 2",
  "basePackage": "dev.shared",
  "features": [
    "dev.shared.do_gamer.behaviour.CrowdAvoidance"
  ],
  "update": "https://raw.githubusercontent.com/...",
  "download": "https://github.com/.../releases/latest/download/SharedPlugin.jar"
}
```

Rules:

- `features` is a flat array of fully qualified class names. Every class annotated with `@Feature` must appear exactly once.
- **Order does not matter.** Do not ask to sort the array; the current order is historical.
- The `update` and `download` URLs are part of the plugin manifest. They are not feature data and must not be flagged as external URLs.
- Removing a feature means removing it from `features`. Leaving the entry behind produces a load error for a class that no longer exists.
- `version` is bumped in `plugin.json`. Pushing that file to `main` triggers `release-on-version-change.yml`, which creates a tag and a release. **Do not require a version bump in a PR**; an uncoordinated bump can publish a release early. If a PR bumps it, ask whether the maintainer wants that.

## `@Feature`

```java
@Feature(name = "Crowd Avoidance", description = "Detects crowded areas around the ship and moves away from them.")
public class CrowdAvoidance implements Behavior, Configurable<CrowdAvoidanceConfig> { ... }
```

- `name` is shown in the feature list. `description` is one sentence explaining what it does.
- The class must be `public` and the file name must match it.
- The FQCN is `package + "." + file name`. A nested or renamed class breaks registration silently.

## Config classes

Config annotations seen in this repository: `@Option`, `@Number`, `@Percentage`, `@Dropdown`, `@Readonly`, `@Editor`, `@Configuration`.

- `@Configuration("<key>")` is optional. Most config classes do not use it, and its key does not need a translation entry. Do not flag its absence.
- `@Option("key")` on a field is what the user sees. The key must exist in `strings_en.properties`.
- `@Option("")` plus `@Readonly` and `@Editor(...)` is the pattern for embedded HTML instructions (`ConfigHtmlInstructions`). An empty key is valid there; do not flag it.
- Nested `public static class` groups are the convention. They do not need their own file or `@Configuration`.
- Public fields are the convention. Do not request getters or setters.
- `@Dropdown(options = X.class)` points to a `Dropdown.Options<T>` implementation. Profile dropdowns extend `eu.darkbot.shared.config.ProfileNames`.
- `@Number(min, max, step)` bounds numeric options. A missing bound is a MINOR; a bound that permits a value which breaks the feature is a MAJOR.

## Key naming

Existing keys follow `<dev>.<feature>...` with pragmatic casing:

```
do_gamer.crowd_avoidance.radius
do_gamer.autobuy.checkInterval
do_gamer.autobuy.booster.CD_B01
halizeur.log_overlay.enabled
orbithelper.fast_travel.max_jump_attempt_time
```

- Keep the same root as the rest of the feature. A new root for one option is a MINOR.
- Do not enforce snake_case. `checkInterval` and `CD_B01` are accepted.
- Renaming or removing a key is a MAJOR: it loses the translation and the user's saved value. Adding a key is fine.
- A `.desc` suffix is optional and used for longer explanations (`do_gamer.ore_seller.mode.desc`).

## `strings_en.properties`

Located at `src/main/resources/dev/shared/lang/strings_en.properties`. Format is `key=value`, with `#` comments.

- Every `@Option` key needs an entry. Missing keys show up raw in the UI. Check with a quick grep.
- Only edit the English file. `crowdin.yml` maps `strings_en.properties` to `strings_<locale>.properties`; Crowdin owns the other 16 languages. A manual edit to another language is a MAJOR.
- Values are user-facing: sentence case, no trailing period on labels, clear wording. A label like `Enable` is better than `enable flag`.
- Keep the key stable when editing the value. Changing wording is safe; changing the key is not.

## Deleting or renaming a feature

| Action | Required |
| --- | --- |
| Delete a feature | Remove the class, remove the FQCN from `features`. Old translations may stay. |
| Rename a class | Rename the file, update `features`, update the config key root only if the feature name changed. |
| Move a feature | Update the FQCN in `features`; the package is part of the name. |
| Move config to a new package | No `plugin.json` change; config is discovered from the feature. |

## Fast checks

```powershell
# Registration: every @Feature FQCN is listed in plugin.json
Select-String -Path src\main\resources\plugin.json -Pattern 'dev\.shared\..*\.MyFeature'

# i18n: every @Option key exists in English
Select-String -Path src\main\resources\dev\shared\lang\strings_en.properties -Pattern '^do_gamer\.my_feature\.'
```

Run these greps manually; there is no automated collector. Report `[MISSING]`, `[STALE]` and `[MISSING STRING]` findings by hand.
