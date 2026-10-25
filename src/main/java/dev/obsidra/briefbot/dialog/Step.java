package dev.obsidra.briefbot.dialog;

/**
 * Шаг диалога, на котором сейчас клиент.
 * {@code label} есть только у шагов-полей заявки: их можно поменять с карточки подтверждения.
 */
public enum Step {
    TYPE("Что сделать"),
    DESCRIPTION("Задача"),
    ASSETS("Что есть"),
    BUDGET("Бюджет"),
    DEADLINE("Сроки"),
    CONTACT("Контакт"),
    /** Клиент выбрал «Другой способ связи» и вводит контакт текстом. */
    CONTACT_TEXT(null),
    CONFIRM(null);

    private final String label;

    Step(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isEditable() {
        return label != null;
    }
}
