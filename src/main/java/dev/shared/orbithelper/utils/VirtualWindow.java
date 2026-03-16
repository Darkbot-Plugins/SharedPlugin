package dev.shared.orbithelper.utils;

public class VirtualWindow {

    private final double left;
    private final double top;
    private final double right;
    private final double bottom;

    // Primary constructor — takes absolute corner coordinates
    public VirtualWindow(double left, double top, double right, double bottom) {
        this.left = Math.floor(left);
        this.top = Math.floor(top);
        this.right = Math.ceil(right);
        this.bottom = Math.ceil(bottom);
    }

    // Creates a region from a top-left origin and dimensions
    public static VirtualWindow fromBounds(double x, double y, double width, double height) {
        return new VirtualWindow(x, y, x + width, y + height);
    }

    // --- Position getters ---

    /** Returns the left edge (start X). */
    public double x() {
        return this.left;
    }

    /** Returns the top edge (start Y). */
    public double y() {
        return this.top;
    }

    /** Returns the right edge (end X). */
    public double endX() {
        return this.right;
    }

    /** Returns the bottom edge (end Y). */
    public double endY() {
        return this.bottom;
    }

    // --- Dimension helpers ---

    public double width() {
        return this.right - this.left;
    }

    public double height() {
        return this.bottom - this.top;
    }

    public double centerX() {
        return this.left + (width() / 2.0D);
    }

    public double centerY() {
        return this.top + (height() / 2.0D);
    }

    // --- Random point helpers (for human-like click variance) ---

    @Override
    public String toString() {
        return "VirtualWindow{left=" + left
                + ", top=" + top
                + ", right=" + right
                + ", bottom=" + bottom
                + ", width=" + width()
                + ", height=" + height() + "}";
    }
}