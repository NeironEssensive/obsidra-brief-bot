package dev.obsidra.briefbot.message;

/** Экранирование текста для HTML-разметки Telegram: всё, что ввёл пользователь, проходит через {@link #escape}. */
public final class Html {

    private Html() {
    }

    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
