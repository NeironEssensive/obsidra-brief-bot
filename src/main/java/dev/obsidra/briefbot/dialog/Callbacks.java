package dev.obsidra.briefbot.dialog;

/** Значения callback_data у кнопок диалога. Telegram ограничивает их 64 байтами. */
final class Callbacks {

    static final String START = "start";
    static final String RESUME = "resume";
    static final String RESTART = "restart";

    static final String TYPE = "type:";
    static final String ASSET = "asset:";
    static final String ASSETS_DONE = "assets:done";
    static final String BUDGET = "budget:";
    static final String DEADLINE = "deadline:";
    static final String CONTACT_TELEGRAM = "contact:tg";
    static final String CONTACT_OTHER = "contact:other";

    static final String SEND = "confirm:send";
    static final String EDIT = "confirm:edit";
    static final String BACK = "confirm:back";
    static final String CANCEL = "confirm:cancel";
    static final String EDIT_FIELD = "edit:";

    private Callbacks() {
    }

    /** Значение enum из {@code prefix + NAME} или {@code null}, если данные не про эту кнопку. */
    static <E extends Enum<E>> E parse(String data, String prefix, Class<E> type) {
        if (!data.startsWith(prefix)) {
            return null;
        }
        try {
            return Enum.valueOf(type, data.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
