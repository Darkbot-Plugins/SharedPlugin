package dev.shared.berke.ramcleaner;

/** Pure decision logic kept separate so RAM cleanup rules can be unit tested. */
final class DailyRamCleanerPolicy {
    private DailyRamCleanerPolicy() {
    }

    static boolean shouldClean(long memoryUsageMb, int thresholdMb,
                               boolean avoidCombat, boolean inCombat) {
        if (memoryUsageMb < thresholdMb) return false;
        return !avoidCombat || !inCombat;
    }
}
