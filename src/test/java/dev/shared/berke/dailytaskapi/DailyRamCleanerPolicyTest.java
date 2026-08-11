package dev.shared.berke.dailytaskapi;

public final class DailyRamCleanerPolicyTest {
    public static void main(String[] args) {
        assertFalse(DailyRamCleanerPolicy.shouldClean(1199, 1200, true, false),
                "below threshold");
        assertTrue(DailyRamCleanerPolicy.shouldClean(1200, 1200, true, false),
                "at threshold while idle");
        assertFalse(DailyRamCleanerPolicy.shouldClean(1800, 1200, true, true),
                "combat protection");
        assertTrue(DailyRamCleanerPolicy.shouldClean(1800, 1200, false, true),
                "combat protection disabled");
        System.out.println("DailyRamCleanerPolicyTest: OK");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
