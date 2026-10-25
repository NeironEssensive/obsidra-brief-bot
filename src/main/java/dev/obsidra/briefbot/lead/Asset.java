package dev.obsidra.briefbot.lead;

/** Что у клиента уже есть к началу проекта. */
public enum Asset {
    SPEC("ТЗ"),
    DESIGN("Дизайн"),
    REFERENCES("Примеры, что нравится"),
    EXISTING_PROJECT("Готовый проект на доработку"),
    IDEA_ONLY("Только идея");

    private final String label;

    Asset(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
