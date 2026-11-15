# Learnings

This file is the memory of the review skill. `checklist.md` holds the project's general rules; this file holds what this maintainer actually decided, including corrections to the checklist.

It is append-only. Never delete an entry. If a decision changes, add a new dated entry and mark the old one `Superseded by <date> — <topic>`.

## How to add an entry

After the user reviews a report, convert their feedback into an entry:

```md
## 2026-11-15 — short topic
- **Context:** what was reviewed (PR #, feature, file).
- **Observation:** what the skill got wrong, missed, or over-flagged.
- **Decision:** the rule to apply from now on.
- **Applies to:** paths, feature types, or "all reviews".
- **Supersedes:** optional, previous date + topic.
```

Promotion rule: when the same correction appears twice, copy it into `checklist.md` as a row and note the promotion here. The checklist is what the reviewer reads every time; this file is the trail.

Severity matters: record whether the user considered something a blocker, a real bug, or noise. "The user ignored it" is a signal to downgrade, not to delete.

## Project decisions (verified in this repository)

These were checked against the code, not assumed. They exist so the skill does not re-flag settled patterns.

### 2026-11-15 — Logging channel
- **Context:** `System.out.println` appears in 19 places across features.
- **Observation:** the project has no logger dependency and no intention to add one.
- **Decision:** `System.out.println("<Feature>: ...")` is the accepted logging channel. Do not propose SLF4J or `java.util.logging`. Do flag per-tick spam and missing context.
- **Applies to:** all reviews.

### 2026-11-15 — Direct DarkBot internals are legacy, not automatically wrong
- **Context:** `com.github.manolo8.darkbot.*` is imported by `PetGearHelper`, `BackpageHelper`, `Autobuy`, `SimpleGalaxyGate`, `SolarisInc`, `AutoRefin`, `CloseAdOffer`, `GateNpcFlag`, `LowGate` and `ZetaGate`.
- **Observation:** these imports are accepted where the API lacks the call (hangar internals, `GuiManager`, `NpcExtraFlag`).
- **Decision:** only flag **new** direct imports, and only when an API equivalent exists (`javap` the pinned jar). Require isolation and a reason, not a rewrite. Never report the legacy files just because the change touches them.
- **Applies to:** API pass.

### 2026-11-15 — `@Configuration` is optional
- **Context:** only 3 of 14 config classes use `@Configuration`; 11 work without it.
- **Observation:** an early draft treated it as mandatory.
- **Decision:** do not flag its absence. Do not require its key in `strings_en.properties` (e.g. `autobuy=` does not exist).
- **Applies to:** config pass.

### 2026-11-15 — Config key casing is pragmatic
- **Context:** keys include `do_gamer.autobuy.checkInterval` and `do_gamer.autobuy.booster.CD_B01`.
- **Observation:** the "snake_case only" rule is not followed and has not caused problems.
- **Decision:** require the `<dev>.<feature>` prefix and an English string. Match the surrounding casing. Only a new root or a collision is a finding.
- **Applies to:** config pass.

### 2026-11-15 — `@Option("")` with `@Editor` is valid
- **Context:** `AutobuyConfig` uses `@Option("")` + `@Readonly` + `@Editor(Instructions.class)` for embedded HTML instructions.
- **Observation:** an empty key looks like a bug but is the supported instructions pattern.
- **Decision:** do not flag an empty `@Option` when it is paired with `@Editor`.
- **Applies to:** config pass.

### 2026-11-15 — `System.currentTimeMillis()` is accepted
- **Context:** used for cooldowns in `FastTravel`, `BackpageHelper`, `OreSeller`, `FixPetStuck`, `Autobuy`, `KamikazeHandler` and others.
- **Observation:** `Timer` and `currentTimeMillis` coexist.
- **Decision:** only flag blocking waits and unbounded retries. Do not rewrite working cooldown comparisons.
- **Applies to:** lifecycle pass.

### 2026-11-15 — Long legacy files are not findings
- **Context:** `OreSeller` is 1051 lines, `SimpleGalaxyGateConfig` 803, `SimpleGalaxyGate` 780, `Spaceball` 760, `Autobuy` 717.
- **Observation:** the 20-line guidance is aspirational and not enforced retroactively.
- **Decision:** judge only methods the change adds or substantially modifies. Never report a legacy file's size as a finding.
- **Applies to:** quality pass.

### 2026-11-15 — `dev.shared.exampleDev` is intentionally unregistered
- **Context:** `@Feature` classes exist in the example package but not in `plugin.json`.
- **Observation:** it is sample code for contributors.
- **Decision:** do not report missing registration for `dev.shared.exampleDev`.
- **Applies to:** registration pass.

### 2026-11-15 — Public config fields are the convention
- **Context:** every config class exposes `public` fields with annotations.
- **Observation:** encapsulation is provided by the annotation processor, not getters.
- **Decision:** never request getters/setters or private fields for config.
- **Applies to:** config pass.

### 2026-11-15 — `plugin.json` feature order is historical
- **Context:** the `features` array is not alphabetical.
- **Observation:** reordering creates noisy diffs and no benefit.
- **Decision:** do not ask to sort. Check membership only.
- **Applies to:** registration pass.

### 2026-11-15 — `forEach` is fine outside hot paths
- **Context:** 9 uses, mostly in setup, state reset and one-shot scans.
- **Observation:** only per-tick lambda allocation over large entity lists matters.
- **Decision:** flag `forEach` only when the change puts it on a per-tick path over a large collection, and then as MINOR.
- **Applies to:** performance pass.

### 2026-11-15 — Registration and i18n are currently complete
- **Context:** 14 `@Feature` classes and 14 `plugin.json` entries match exactly. 157 distinct `@Option` keys all exist in `strings_en.properties`.
- **Observation:** a missing entry is therefore a real defect introduced by the change, not pre-existing debt.
- **Decision:** keep both checks strict; report `[MISSING]` and `[MISSING STRING]` findings as MAJOR/BLOCKER as the checklist says.
- **Applies to:** registration pass.

### 2026-11-15 — `getStoppedStatus()` may return null
- **Context:** `Spaceball` and `SimpleGalaxyGate` implement it; returning null is accepted by the UI.
- **Observation:** an early draft treated null as a defect.
- **Decision:** do not flag a null `getStoppedStatus()`. Do flag a `getStatus()` that never changes if it misleads the user.
- **Applies to:** lifecycle pass.

### 2026-11-15 — No `# -----` section blocks required in strings
- **Context:** only the Halizeur block uses that comment style; the other features do not.
- **Observation:** it is cosmetic, not a convention.
- **Decision:** do not require section comments in `strings_en.properties`.
- **Applies to:** i18n pass.

### 2026-11-15 — Import grouping
- **Context:** files order imports as `java.*`, then `com.*`, `dev.*`, `eu.*` alphabetically, with blank lines between root groups.
- **Observation:** consistent across `CrowdAvoidance`, `FastTravel` and `Autobuy`.
- **Decision:** treat import order as NIT, and only mention it when the change adds imports out of order.
- **Applies to:** quality pass.

### 2026-11-15 — Version bumps are not required in PRs
- **Context:** pushing `plugin.json` to `main` triggers `release-on-version-change.yml`.
- **Observation:** a PR that bumps the version can publish a release as soon as it merges.
- **Decision:** do not require a bump. If a PR includes one, ask whether it is coordinated; do not treat it as a defect by default.
- **Applies to:** registration pass.

### 2026-11-15 — No `gh` CLI in this environment
- **Context:** `gh --version` fails; the remote is GitHub over SSH.
- **Observation:** PR review works without `gh`: fetch `refs/pull/<n>/head` with plain `git`.
- **Decision:** use `--pr N` (or `--base <ref>` for an already-fetched branch). Do not depend on `gh` commands in instructions.
- **Applies to:** scope step.

### 2026-11-15 — Java 17 is installed, the project targets 11
- **Context:** `java -version` reports 17; `build.gradle.kts` sets source/target 11; CI uses JDK 11.
- **Observation:** a local compile on 17 still rejects Java 12+ syntax because of `-source 11`.
- **Decision:** use `.\gradlew.bat compileJava` as the compatibility check. Do not infer compatibility from the local JDK version.
- **Applies to:** build pass.

## Rejected rules

Rules the maintainer explicitly does not want enforced. Format:

```md
## YYYY-MM-DD — rejected: <rule>
- **Reason:** ...
- **Instead:** ...
```

_(none yet)_

## Recurring findings

Issues that keep coming back across contributors. When a pattern appears in three reviews, it is a candidate for a contributor note in `CONTRIBUTING.md`, not just a review comment.

_(none yet)_

## Open questions

Things to ask the maintainer when the next relevant change appears.

_(none yet)_
