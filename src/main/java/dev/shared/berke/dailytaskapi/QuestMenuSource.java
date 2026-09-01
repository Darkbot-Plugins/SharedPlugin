package dev.shared.berke.dailytaskapi;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import eu.darkbot.api.game.other.Gui;
import eu.darkbot.api.utils.NativeAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers the active-quest selector row from DarkBot's native quest GUI.
 *
 * <p>No screenshot, OCR, quest title, fixed slot number or screen resolution is
 * used. The selector row is recognized from the native sprite source: it lives
 * in the quest header and contains evenly-spaced selector sprites. All quest
 * identity and daily/non-daily decisions remain the responsibility of
 * QuestAPI.</p>
 */
final class QuestMenuSource {
    static final class Selector {
        private final int x;
        private final int y;

        Selector(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }
    }

    private static final class Sprite {
        private final int x;
        private final int y;
        private final List<Sprite> children;

        private Sprite(int x, int y, List<Sprite> children) {
            this.x = x;
            this.y = y;
            this.children = children;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private List<Sprite> children() {
            return children;
        }
    }

    private static final class Row {
        private final int x;
        private final int y;
        private final List<Integer> childXs;
        private final int score;

        private Row(int x, int y, List<Integer> childXs, int score) {
            this.x = x;
            this.y = y;
            this.childXs = childXs;
            this.score = score;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private List<Integer> childXs() {
            return childXs;
        }

        private int score() {
            return score;
        }
    }

    private final Gui questGui;
    private final com.github.manolo8.darkbot.core.objects.Gui nativeQuestGui;
    private final IDarkBotAPI memory;
    private String lastError = "";

    QuestMenuSource(Gui questGui) {
        this.questGui = questGui;
        this.nativeQuestGui = questGui instanceof com.github.manolo8.darkbot.core.objects.Gui
                ? (com.github.manolo8.darkbot.core.objects.Gui) questGui : null;
        this.memory = Main.API;
    }

    List<Selector> discoverSelectors() {
        lastError = "";
        if (questGui == null) return fail("Quest window not found");
        if (memory == null || nativeQuestGui == null) {
            return fail("DarkBot quest source tree is unavailable");
        }

        try {
            long rootAddress = nativeQuestGui.getAddress();
            if (rootAddress == 0L) return fail("Quest source address is not ready");
            Sprite root = readSprite(rootAddress, 0, new HashSet<>());
            if (root == null) return fail("Quest source tree could not be read");

            List<Row> rows = new ArrayList<>();
            // The root coordinate is the quest window's screen position.
            // Remove it so candidates are evaluated in GUI-local space;
            // window position, animation and resolution cannot affect discovery.
            findRows(root, -root.x(), -root.y(), 0, rows);
            Row best = rows.stream().max(Comparator.comparingInt(Row::score)).orElse(null);
            if (best == null) return fail("Quest selector row not found in source tree");

            List<Selector> selectors = new ArrayList<>();
            for (int childX : best.childXs()) {
                // These compact quest tabs only expose a hitbox near their
                // top-left edge. The visual centre is outside the clickable
                // area in the current client, so use a one-pixel inset.
                selectors.add(new Selector(best.x() + childX + 1, best.y() + 1));
            }
            return List.copyOf(selectors);
        } catch (RuntimeException error) {
            return fail("Quest source tree error: " + error.getClass().getSimpleName());
        }
    }

    String getLastError() {
        return lastError;
    }

    void scrollSelectorsDown() {
        List<Selector> current = discoverSelectors();
        if (current.isEmpty()) return;
        Selector middle = current.get(current.size() / 2);
        int screenX = (int) Math.round(questGui.getX() + middle.x());
        int screenY = (int) Math.round(questGui.getY() + middle.y());
        Main.API.postActions(NativeAction.MouseWheel.down(screenX, screenY));
    }

    private Sprite readSprite(long address, int depth, Set<Long> visited) {
        if (address == 0L || depth > 7 || visited.size() > 600 || !visited.add(address)) return null;
        List<Long> childAddresses = new ArrayList<>();
        try {
            nativeQuestGui.forEachSpriteChild(address, childAddresses::add);
        } catch (RuntimeException error) {
            lastError = "Quest source child could not be read: " + describe(error);
        }

        List<Sprite> children = new ArrayList<>();
        for (long childAddress : childAddresses) {
            Sprite child = readSprite(childAddress, depth + 1, visited);
            if (child != null) children.add(child);
        }
        return new Sprite(coordinate(address, 88L), coordinate(address, 92L), List.copyOf(children));
    }

    private int coordinate(long address, long offset) {
        try {
            long matrix = memory.readLong(address + 72L);
            int raw = memory.readInt(matrix + offset);
            return Math.round(raw * 0.05f);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void findRows(Sprite sprite, int parentX, int parentY, int depth, List<Row> rows) {
        int absoluteX = parentX + sprite.x();
        int absoluteY = parentY + sprite.y();
        Row candidate = rowCandidate(sprite, absoluteX, absoluteY, depth);
        if (candidate != null) rows.add(candidate);
        for (Sprite child : sprite.children()) findRows(child, absoluteX, absoluteY, depth + 1, rows);
    }

    private Row rowCandidate(Sprite sprite, int absoluteX, int absoluteY, int depth) {
        List<Sprite> children = sprite.children();
        if (children.size() < 2 || children.size() > 24 || depth < 2) return null;
        if (absoluteY < 0 || absoluteY > 25 || absoluteX < 100) return null;

        List<Integer> xs = children.stream().map(Sprite::x).sorted().collect(Collectors.toList());
        int minY = children.stream().mapToInt(Sprite::y).min().orElse(0);
        int maxY = children.stream().mapToInt(Sprite::y).max().orElse(0);
        if (maxY - minY > 2) return null;

        List<Integer> differences = new ArrayList<>();
        for (int index = 1; index < xs.size(); index++) differences.add(xs.get(index) - xs.get(index - 1));
        int step = median(differences);
        if (step < 8 || step > 14) return null;
        if (differences.stream().anyMatch(value -> Math.abs(value - step) > 2)) return null;

        int score = children.size() * 100 + Math.min(absoluteX, 500) - absoluteY * 2;
        return new Row(absoluteX, absoluteY, xs, score);
    }

    private int median(List<Integer> values) {
        if (values.isEmpty()) return 0;
        List<Integer> sorted = values.stream().sorted().collect(Collectors.toList());
        return sorted.get(sorted.size() / 2);
    }

    private List<Selector> fail(String message) {
        if (lastError == null || lastError.isBlank()) lastError = message;
        return List.of();
    }

    private String describe(RuntimeException error) {
        if (error == null) return "unknown error";
        String description = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) return description;
        return description + ": " + message;
    }
}
