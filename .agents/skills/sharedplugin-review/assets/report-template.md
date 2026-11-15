# Code review: <scope>

**Verdict:** `APPROVE` | `COMMENT` | `REQUEST CHANGES` | `BLOCK`
**Scope:** <PR #209 | branch `feature/x` vs `origin/main` | working tree>
**Files reviewed:** <N> (<M> Java, <K> resources)
**Build:** `.\gradlew.bat compileJava` — passed | failed | not run (reason)
**Confidence:** high | medium | low — <what is uncertain>

## Summary

<2–4 sentences. What the change does, the single biggest risk, and what would make it mergeable. No praise padding.>

## Findings index

| # | Severity | Location | Issue |
| --- | --- | --- | --- |
| 1 | MAJOR | `path:line` | <one line> |

## Findings

### BLOCKER

_None._

### MAJOR

#### 1. <Consequence in one sentence, phrased as the failure the user will see>

- **Where:** `src/main/java/dev/shared/<dev>/<...>.java:<line>`
- **Evidence:**

```java
<smallest excerpt that proves it>
```

- **Why:** <the mechanism: which tick, which state, what breaks. Reference the checklist section if useful.>
- **Fix:**

```diff
- <old line>
+ <new line>
```

- **Confidence:** high | medium | low — <what would confirm it>

### MINOR

- `path:line` — <issue>. <one-line fix>.

### NITS

- `path:line` — <issue>.

## Verified and OK

Only list what you actually checked. This is the evidence that the review was not a skim.

- [x] Feature registered in `plugin.json` (or: no `@Feature` added)
- [x] Every new `@Option` key exists in `strings_en.properties`
- [x] No external URLs used for feature data
- [x] No blocking calls on the game thread
- [x] Guards present: `config == null`, enabled, captcha, travelling, attack
- [x] Waits use `Timer` and have a global timeout
- [x] Behaviour/Module restores control in `onTickStopped`
- [x] Java 11 compatible
- [x] Shared state is thread-safe across `onBackgroundTick`

## Questions for the author

1. <A question that changes the verdict, not a rhetorical one.>

---

## Skill feedback

Which findings were wrong, and which real problem did I miss?

---

## Example of a well-formed finding

#### 1. The feature can refresh the game while the hero is under attack

- **Where:** `src/main/java/dev/shared/orbithelper/behaviours/fast_travel/FastTravel.java:368`
- **Evidence:**

```java
if (++this.consecutiveGlobalTimeouts >= MAX_CONSECUTIVE_GLOBAL_TIMEOUTS) {
    this.bot.handleRefresh();
}
```

- **Why:** the timeout branch is reached from `onTickBehavior`, which also runs while the hero is being attacked. `handleRefresh()` reloads the client and loses the fight in progress. The guard at the top of `onTickBehavior` checks `attack.isAttacking()`, but `isUnderAttack()` can be true on the tick the timeout fires, and the counter is not reset by it.
- **Fix:**

```diff
- if (++this.consecutiveGlobalTimeouts >= MAX_CONSECUTIVE_GLOBAL_TIMEOUTS) {
-     this.bot.handleRefresh();
- }
+ if (++this.consecutiveGlobalTimeouts >= MAX_CONSECUTIVE_GLOBAL_TIMEOUTS
+         && !this.isUnderAttack()
+         && !this.attack.isAttacking()) {
+     this.bot.handleRefresh();
+ }
```

- **Confidence:** medium — the guard combination is visible in the file, but the exact ordering of `isUnderAttack()` versus the timeout increment depends on the caller path, which I did not run.
