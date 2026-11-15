package dev.shared.fix3dll.behaviour.auto_dispatch;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import eu.darkbot.api.managers.DispatchAPI.Cost;
import eu.darkbot.api.managers.DispatchAPI.Retriever;

/**
 * Decides which retriever should be hired next.
 *
 * <p>The dispatch slots are treated as an unordered pool. The user chooses which retrievers
 * should be running, the same one possibly several times, and the planner compares that wanted
 * pool with what currently occupies the slots. Anything wanted but not running is a candidate.
 *
 * <p>Contains decision logic only: it never reads game state on its own and never clicks, which
 * keeps it independent of the game client.
 */
final class HirePlanner {

    /** Epoch milliseconds until which a retriever, keyed by normalized name, is not offered again. */
    private final Map<String, Long> blockedUntil = new HashMap<>();

    /**
     * Picks the next retriever to hire.
     *
     * <p>Every occupied slot covers one matching entry of the wanted pool. The first entry left
     * uncovered that is currently offered, not paused and affordable is returned.
     *
     * @param targets          the wanted pool; a name appears once per slot it should fill
     * @param runningNames     names of the retrievers occupying a slot
     * @param available        retrievers the game currently offers for hire
     * @param spendableCredits credits that may be spent, i.e. the balance above the configured reserve
     * @param spendableUridium uridium that may be spent, i.e. the balance above the configured reserve
     * @return the retriever to hire, or empty if nothing is missing or nothing missing can be hired
     */
    Optional<Retriever> next(final List<String> targets,
                             final List<String> runningNames,
                             final List<? extends Retriever> available,
                             final double spendableCredits,
                             final double spendableUridium) {
        Map<String, Integer> covered = new HashMap<>();
        for (String running : runningNames) {
            covered.merge(normalize(running), 1, Integer::sum);
        }
        for (String target : targets) {
            String name = normalize(target);
            if (covered.getOrDefault(name, 0) > 0) {
                covered.merge(name, -1, Integer::sum);
                continue;
            }
            Optional<Retriever> offer = this.findOffer(name, available, spendableCredits, spendableUridium);
            if (offer.isPresent()) {
                return offer;
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a retriever by name among the current offers.
     *
     * @return the offer if it exists, is not paused and is affordable; otherwise empty
     */
    private Optional<Retriever> findOffer(final String name,
                                          final List<? extends Retriever> available,
                                          final double spendableCredits,
                                          final double spendableUridium) {
        if (this.isBlocked(name)) {
            return Optional.empty();
        }
        for (Retriever retriever : available) {
            if (name.equals(normalize(retriever.getName()))
                    && isAffordable(retriever, spendableCredits, spendableUridium)) {
                return Optional.of(retriever);
            }
        }
        return Optional.empty();
    }

    /**
     * Excludes a retriever from {@link #next} for a while, typically after a hire attempt failed.
     *
     * @param retrieverName name of the retriever to leave alone
     * @param pauseMs       how long to exclude it, in milliseconds
     */
    void pause(final String retrieverName, final long pauseMs) {
        this.blockedUntil.put(normalize(retrieverName), System.currentTimeMillis() + pauseMs);
    }

    /**
     * Tells when the next paused retriever becomes eligible again.
     *
     * @return milliseconds until the earliest pause ends, or a negative value if nothing is paused
     */
    long msUntilRetry() {
        long now = System.currentTimeMillis();
        long soonest = -1;
        for (long until : this.blockedUntil.values()) {
            if (until > now && (soonest < 0 || until - now < soonest)) {
                soonest = until - now;
            }
        }
        return soonest;
    }

    /**
     * Checks whether the wanted pool is fully covered by the occupied slots.
     *
     * <p>Unlike {@link #next}, pauses, offers and prices are ignored: this only answers whether
     * something is still wanted, not whether it can be hired right now.
     *
     * @param targets       the wanted pool
     * @param occupiedNames names of the retrievers occupying a slot
     * @return {@code true} if at least one wanted retriever is not running
     */
    boolean hasMissing(final List<String> targets, final List<String> occupiedNames) {
        Map<String, Integer> covered = new HashMap<>();
        for (String occupied : occupiedNames) {
            covered.merge(normalize(occupied), 1, Integer::sum);
        }
        for (String target : targets) {
            if (covered.merge(normalize(target), -1, Integer::sum) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a retriever can be paid for without touching the configured reserves.
     *
     * <p>Only credits and uridium have a balance that can be read, so only those are compared.
     * Other costs, such as permits, cannot be verified up front and are let through; if the
     * game rejects the hire for lack of them, the attempt fails and is retried later.
     *
     * @param retriever the retriever to check
     * @param credits   spendable credits
     * @param uridium   spendable uridium
     * @return {@code false} if a credit or uridium cost exceeds the spendable balance, or if a
     *         cost has not been loaded by the game yet
     */
    static boolean isAffordable(final Retriever retriever, final double credits, final double uridium) {
        for (Cost cost : retriever.getCostList()) {
            String loot = lootId(cost);
            if (cost.getAmount() < 0
                    || loot.contains("credit") && credits < cost.getAmount()
                    || loot.contains("uridium") && uridium < cost.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the loot id of a cost in lower case, or an empty string if the game has not filled it in
     */
    private static String lootId(final Cost cost) {
        String id = cost.getLootId();
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private boolean isBlocked(final String name) {
        return this.blockedUntil.getOrDefault(name, 0L) > System.currentTimeMillis();
    }

    /**
     * Normalizes a retriever name for comparison, so that case and surrounding blanks do not matter.
     */
    private static String normalize(final String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
