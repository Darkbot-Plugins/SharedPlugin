package dev.shared.berti.types.enums;

public enum Mode {
    STROKE("STROKE", 1),
    BEHE("BEHE", 2),
    INVOKE("Invoke", 3);

    private final String mode;
    private final int id;

    private Mode(String mapName, int id) {
        this.mode = mapName;
        this.id = id;
    }

    public String getName() {
        return this.mode;
    }

    public int getId() {
        return this.id;
    }
}
