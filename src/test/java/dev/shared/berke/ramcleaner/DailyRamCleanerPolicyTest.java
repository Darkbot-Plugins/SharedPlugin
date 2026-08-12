package dev.shared.berke.ramcleaner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DailyRamCleanerPolicyTest {
    @Test
    void cleansOnlyAtOrAboveTheThreshold() {
        assertFalse(DailyRamCleanerPolicy.shouldClean(1199, 1200, true, false));
        assertTrue(DailyRamCleanerPolicy.shouldClean(1200, 1200, true, false));
    }

    @Test
    void respectsCombatProtection() {
        assertFalse(DailyRamCleanerPolicy.shouldClean(1800, 1200, true, true));
        assertTrue(DailyRamCleanerPolicy.shouldClean(1800, 1200, false, true));
    }
}
