package dev.obsidra.briefbot.lead;

public enum Deadline {
    URGENT("Срочно, до 2 недель"),
    MONTH("В течение месяца"),
    QUARTER("1–3 месяца"),
    NO_RUSH("Не горит");

    private final String label;

    Deadline(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
