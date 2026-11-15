---
name: sharedplugin-review
description: "Reviews code changes in the SharedPlugin DarkBot repository before merge — local git diffs, GitHub pull requests, or a feature folder — and returns a severity-ranked report with proposed patches. Checks DarkBotAPI usage, tick safety, lifecycle guards, threading, plugin.json and strings_en registration, config, exception handling, performance and PR hygiene. Use this whenever the user asks to review, audit, vet, check or approve a change, diff, commit, PR, feature or Java file in this repository, including casual requests like 'revisa estos cambios', 'revisar PR', 'review my branch', 'can this merge?' or 'mira si esto está bien', even when the word review is not used."
---

# SharedPlugin Code Review

Review changes to this plugin the way the maintainer would: find what can break the bot or the build, then get out of the way. A review that misses a crash is useless; a review buried in naming nits is worse, because it hides the crash.

## The stance

- **Review the change, not the file.** Pre-existing code is context, not a finding. Only report something old when the change makes it reachable, worse, or contradicts it.
- **Every finding needs a consequence.** Name the scenario: which tick, which state, what the user sees. If you cannot describe it, it is a question, not a finding.
- **Never edit, commit or push.** Proposed patches belong in the report. The user decides what to apply.
- **The repo outranks this skill.** `CONTRIBUTING.md` and `agents.md` are the source of truth. `references/learnings.md` records decisions the maintainer already made; when it conflicts with `references/checklist.md`, learnings win.

## Workflow

Follow the steps in order. Each one exists to prevent a specific failure mode.

### 1. Fix the scope

Use the collector first. It is read-only, needs no `gh`, and produces the diff, the changed-file map, registration status, i18n coverage and regex signals in one pass.

| Situation | Command |
| --- | --- |
| Uncommitted work | `python .agents/skills/sharedplugin-review/scripts/collect_review_context.py --working` |
| Branch vs main | `python .agents/skills/sharedplugin-review/scripts/collect_review_context.py --base origin/main` |
| GitHub PR number | `python .agents/skills/sharedplugin-review/scripts/collect_review_context.py --pr 209` |
| Single commit | `python .agents/skills/sharedplugin-review/scripts/collect_review_context.py --base <sha>^ --head <sha>` |
| Whole feature or files | `python .agents/skills/sharedplugin-review/scripts/collect_review_context.py --files <paths>` |

- If the user pastes a PR URL, extract the number. If they name a branch or commit, use `--base`.
- When the diff is very large, run with `--no-diff` first, then read the files that matter. `--json` is useful for filtering.
- Read the PR title and description (`git log -1 --format=%B`, or ask the user). Intent decides whether an odd-looking change is a bug or a deliberate workaround.
- Note who owns the touched packages. A change spanning two `dev.shared.<dev>` packages needs both developers' agreement; the collector reports it.

### 2. Build a mental model before judging

Read each changed Java file end to end, not just the hunks. Then read:

- its config class and the `plugin.json` entry;
- the shared helpers it calls (`dev.shared.utils`, `BackpageHelper`, `PetGearHelper`, `MapTraveler`, `PortalJumper`);
- one existing feature of the same type (Behavior / Module / Task) when the pattern is unclear.

Identify the feature type early. It determines which lifecycle rules apply, so it prevents most false positives.

### 3. Run five passes, in this order

| Pass | Question | Read |
| --- | --- | --- |
| 1. Safety and correctness | Can this crash, hang, waste items, get the user banned, or break the build? | `references/checklist.md` §1–3 |
| 2. Lifecycle and threading | Does it respect the tick, the module ownership and the background thread? | `references/checklist.md` §4–6 |
| 3. API and project rules | Is it using DarkBotAPI, the shared utils and allowed data sources? | `references/darkbot-api.md` |
| 4. Config, registration, i18n | Can the user enable it, and will the UI show real text? | `references/registration-and-i18n.md`, `references/checklist.md` §7–8 |
| 5. Quality | Naming, structure, duplication, dead code, readability. | `references/checklist.md` §9–11 |

Severity follows blast radius. A per-tick `Thread.sleep` outranks a missing `final`, and both outrank import order. Finishing the correctness passes first keeps the report ordered by what actually matters.

### 4. Verify before reporting

- **Compile** with `.\gradlew.bat compileJava` (or `build`). Only claim a compile failure you reproduced; if you did not run it, say so in the report.
- **API**: confirm the method exists in `eu.darkbot.api` before calling it misuse. Check an existing call site or the dependency jar. Never invent a signature.
- **Registration and i18n**: re-check with the collector or a quick grep. The collector validates `@Feature` classes against `plugin.json` and `@Option` keys against `strings_en.properties`.
- **Thread**: identify the caller. A method only blocks the game thread if a tick actually calls it.
- **Behavior you cannot run**: phrase it as a question with the scenario. A confident wrong claim costs more than an honest question.

### 5. Write the report

Use `assets/report-template.md` exactly. The rules that matter:

- Sort by severity, then by path.
- Open each finding with the consequence in one sentence.
- Quote the smallest code excerpt that proves it.
- Give a diff for every BLOCKER and MAJOR. MINOR and NIT can be prose.
- Mark confidence when the claim depends on runtime behavior you did not observe.
- Group nits. More than five lines of nits means the report is unreadable.
- Always include a short "Verified and OK" list, so the author knows what you actually checked.
- If nothing is wrong, say so plainly and approve. Do not manufacture findings to look thorough.

| Verdict | When |
| --- | --- |
| `BLOCK` | A project rule is violated (external URL, obfuscation, copied feature), or the build does not compile. |
| `REQUEST CHANGES` | At least one BLOCKER or MAJOR finding. |
| `COMMENT` | Only MINOR/NIT findings or open questions. |
| `APPROVE` | No actionable findings. |

### 6. Close the loop

End the report with one line:

```
Skill feedback: which findings were wrong, and which real problem did I miss?
```

When the user answers, turn the answer into a durable rule:

1. Append a dated entry to `references/learnings.md` using the format in that file.
2. If the same correction appears twice, promote it into `references/checklist.md`.
3. If the user rejects a rule, record it under "Rejected rules" and stop flagging it.
4. Never delete history. Mark superseded entries instead.

This is how the skill improves: the checklist encodes the project's general rules, learnings encode what this maintainer actually wants.

## Severity

| Level | Meaning | Examples |
| --- | --- | --- |
| `BLOCKER` | Must not merge. Breaks the build, the bot, or a hard project rule. | External URL, `Thread.sleep` in a tick, unregistered feature, forced refresh under attack, `System.exit`. |
| `MAJOR` | Will cause a real defect or maintenance pain. Fix before merge. | Generic `catch`, missing lifecycle guards, shared state without synchronization, wrong API, missing i18n keys, resource leak. |
| `MINOR` | Should fix, does not block. | Method over ~20 lines, poor naming, duplicated logic, noisy logging, unsafe default. |
| `NIT` | Taste or consistency. | Import order, missing `@Override`, comment wording. |

## Preventing false positives

These are already true in this repository. Flagging them wastes the author's time and makes the report look automated.

- Direct `com.github.manolo8.darkbot` imports exist in legacy files and are accepted. Only new direct imports need justification, and only when an API equivalent exists.
- `System.out.println` is the project's logging channel. Do not propose a logging framework or SLF4J.
- `@Configuration` is optional. Most config classes do not use it.
- Config keys are not uniformly snake_case (`checkInterval`, `CD_B01` exist). Match the surrounding file.
- `System.currentTimeMillis()` is accepted for cooldowns. Only actual blocking waits are a problem.
- Long classes and methods exist (`OreSeller` is over 1000 lines). Judge the change, not the legacy file.
- `dev.shared.exampleDev` is intentionally absent from `plugin.json`.
- Public config fields are the convention. Do not ask for getters and setters.
- `plugin.json` feature order is not alphabetical. Do not ask to sort it.
- `forEach` is fine in setup code. It only matters in a per-tick hot path.

Read `references/learnings.md` before writing the first finding. It is short and it is the most current source of truth about this maintainer's preferences.

## Language and tone

Write in English unless the user asks otherwise. Be direct and specific. Address the code, not the author. No praise padding, no restating what the code does, no hedging when the evidence is clear.

## References

| File | Read it when |
| --- | --- |
| `references/checklist.md` | Every review. The full rule set, with when to flag and when not to. |
| `references/darkbot-api.md` | The change touches entities, managers, modules, behaviours, tasks, timers or navigation. |
| `references/registration-and-i18n.md` | A feature, config class, `@Option` key or `plugin.json` entry is added or changed. |
| `references/learnings.md` | Before the first finding, and after the user gives feedback. |
| `assets/report-template.md` | When writing the report. Copy the structure exactly. |
