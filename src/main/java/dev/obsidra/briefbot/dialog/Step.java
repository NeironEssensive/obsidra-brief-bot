package dev.obsidra.briefbot.dialog;

/** Шаг диалога, на котором сейчас клиент. */
public enum Step {
    TYPE(true),
    DESCRIPTION(true),
    ASSETS(true),
    BUDGET(true),
    DEADLINE(true),
    CONTACT(true),
    /** Клиент выбрал «Другой способ связи» и вводит контакт текстом. */
    CONTACT_TEXT(false),
    CONFIRM(false);

    private final boolean editable;

    Step(boolean editable) {
        this.editable = editable;
    }

    public boolean isEditable() {
        return editable;
    }
}
