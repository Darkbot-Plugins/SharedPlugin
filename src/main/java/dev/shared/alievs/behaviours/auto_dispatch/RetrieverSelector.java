package dev.shared.alievs.behaviours.auto_dispatch;

import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.StatsAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chooses which retriever to hire: honours the preferred retriever, the default retriever, the cost limits,
 * the account balance and a cooldown for retrievers whose hire failed.
 */
public class RetrieverSelector {

    private static final long FAIL_COOLDOWN_MS = 30 * 60_000L;

    private final StatsAPI stats;
    private final Map<String, Long> failedUntil = new HashMap<>();
    private final Set<String> logged = new HashSet<>();
    private final Set<String> warnedPriority = new HashSet<>();

    public RetrieverSelector(StatsAPI stats) {
        this.stats = stats;
    }

    public Optional<DispatchAPI.Retriever> select(Collection<? extends DispatchAPI.Retriever> available,
                                                  AutoDispatchConfig config) {
        long now = System.currentTimeMillis();
        List<DispatchAPI.Retriever> candidates = new ArrayList<>(available.size());
        for (DispatchAPI.Retriever retriever : available) {
            logOnce(retriever);
            if (isHireable(retriever, config, now)) candidates.add(retriever);
        }
        if (candidates.isEmpty()) return Optional.empty();

        // Preferred retriever first; if it isn't affordable, fall back to the configured default retriever.
        String priority = normalize(config.priority);
        if (!priority.isEmpty()) {
            Optional<DispatchAPI.Retriever> match = findByName(candidates, priority);
            if (match.isPresent()) return match;
            warnUnhireablePriority(priority, available);
        }
        String defaultName = normalize(config.defaultRetriever);
        if (!defaultName.isEmpty()) {
            return findByName(candidates, defaultName);
        }
        return Optional.empty();
    }

    private static Optional<DispatchAPI.Retriever> findByName(Collection<DispatchAPI.Retriever> candidates,
                                                              String normalizedName) {
        for (DispatchAPI.Retriever retriever : candidates) {
            if (normalize(retriever.getName()).equals(normalizedName)) return Optional.of(retriever);
        }
        return Optional.empty();
    }

    /** Logs once why the preferred retriever could not be hired (over the limit/balance or on cooldown). */
    @SuppressWarnings("java:S106")
    private void warnUnhireablePriority(String normalizedName, Collection<? extends DispatchAPI.Retriever> available) {
        if (!warnedPriority.add(normalizedName)) return;
        for (DispatchAPI.Retriever retriever : available) {
            if (normalize(retriever.getName()).equals(normalizedName)) {
                System.out.println("[AutoDispatch] Priority " + retriever.getName() + " not hired: cost "
                        + describeCost(retriever) + " is over the limit/balance or on cooldown"
                        + " (raise Max uridium/credits to allow it)");
                break;
            }
        }
    }

    public void markFailed(DispatchAPI.Retriever retriever) {
        failedUntil.put(normalize(retriever.getName()), System.currentTimeMillis() + FAIL_COOLDOWN_MS);
    }

    public static String describeCost(DispatchAPI.Retriever retriever) {
        return retriever.getCostList().stream()
                .map(cost -> cost.getAmount() + " " + cost.getLootId())
                .collect(Collectors.joining(", "));
    }

    private boolean isHireable(DispatchAPI.Retriever retriever, AutoDispatchConfig config, long now) {
        return retriever.isAvailable()
                && failedUntil.getOrDefault(normalize(retriever.getName()), 0L) <= now
                && isAffordable(retriever, config);
    }

    private boolean isAffordable(DispatchAPI.Retriever retriever, AutoDispatchConfig config) {
        for (DispatchAPI.Cost cost : retriever.getCostList()) {
            if (cost.getAmount() > 0 && !canPay(cost, config)) return false;
        }
        return true;
    }

    private boolean canPay(DispatchAPI.Cost cost, AutoDispatchConfig config) {
        String id = Optional.ofNullable(cost.getLootId()).orElse("").toLowerCase(Locale.ROOT);
        int amount = cost.getAmount();

        if (id.contains("uri")) return withinLimit(amount, config.maxUridium, stats.getTotalUridium());
        if (id.contains("credit")) return withinLimit(amount, config.maxCredits, stats.getTotalCredits());
        // Permits are validated by the game itself; any other currency is never spent blindly
        return id.contains("permit");
    }

    private static boolean withinLimit(int amount, int limit, double owned) {
        // Must be within the per-hire limit and actually affordable. A zero balance counts as unaffordable,
        // so we skip the hire instead of attempting it and triggering a 30-minute cooldown on rejection.
        return amount <= limit && amount <= owned;
    }

    // Console logging matches the rest of SharedPlugin (see AGENTS.md); silence Sonar's logger rule.
    @SuppressWarnings("java:S106")
    private void logOnce(DispatchAPI.Retriever retriever) {
        if (logged.add(normalize(retriever.getName()))) {
            System.out.println("[AutoDispatch] Found retriever " + retriever.getName()
                    + " tier " + retriever.getTier() + ", cost: " + describeCost(retriever));
        }
    }

    private static String normalize(String name) {
        return Optional.ofNullable(name).orElse("").replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
