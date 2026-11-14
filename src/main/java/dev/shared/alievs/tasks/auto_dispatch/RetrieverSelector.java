package dev.shared.alievs.tasks.auto_dispatch;

import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.StatsAPI;

import java.util.ArrayList;
import java.util.Arrays;
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
 * Chooses which retriever to hire: honours the priority list, the cost limits,
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

        List<String> priority = parsePriority(config.priority);
        for (String name : priority) {
            for (DispatchAPI.Retriever retriever : candidates) {
                if (normalize(retriever.getName()).equals(name)) return Optional.of(retriever);
            }
        }
        warnUnhireablePriority(priority, available, candidates);
        // No priority retriever is affordable (e.g. out of uridium/permits): use the configured default retriever
        // so a free slot is still filled with a cheap fallback instead of being left empty.
        String fallback = normalize(config.fallback);
        if (!fallback.isEmpty()) {
            for (DispatchAPI.Retriever retriever : candidates) {
                if (normalize(retriever.getName()).equals(fallback)) return Optional.of(retriever);
            }
        }
        return Optional.empty();
    }

    /** Logs once why a priority retriever could not be hired (over the limit/balance or on cooldown). */
    @SuppressWarnings("java:S106")
    private void warnUnhireablePriority(List<String> priority, Collection<? extends DispatchAPI.Retriever> available,
                                        Collection<DispatchAPI.Retriever> candidates) {
        Set<String> hireable = new HashSet<>();
        for (DispatchAPI.Retriever retriever : candidates) hireable.add(normalize(retriever.getName()));
        for (String name : priority) {
            if (hireable.contains(name) || !warnedPriority.add(name)) continue;
            for (DispatchAPI.Retriever retriever : available) {
                if (normalize(retriever.getName()).equals(name)) {
                    System.out.println("[AutoDispatch] Priority " + retriever.getName() + " not hired: cost "
                            + describeCost(retriever) + " is over the limit/balance or on cooldown"
                            + " (raise Max uridium/credits to allow it)");
                    break;
                }
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

    private static List<String> parsePriority(String priority) {
        return Arrays.stream(Optional.ofNullable(priority).orElse("").split(","))
                .map(RetrieverSelector::normalize)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    private static String normalize(String name) {
        return Optional.ofNullable(name).orElse("").replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
