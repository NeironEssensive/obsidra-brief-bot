package dev.obsidra.briefbot.message;

import java.util.List;

/**
 * Одно сообщение бота, не привязанное к Telegram API: так логику диалога можно тестировать без Telegram.
 *
 * @param text            текст в HTML-разметке Telegram; пользовательский ввод экранирован через {@link Html}
 * @param keyboard        ряды кнопок под сообщением
 * @param replaceOriginal заменить сообщение, на кнопку которого нажали, а не отправлять новое
 */
public record Reply(String text, List<List<Button>> keyboard, boolean replaceOriginal) {

    public Reply {
        keyboard = keyboard == null ? List.of() : keyboard.stream().map(List::copyOf).toList();
    }

    public static Reply send(String text) {
        return new Reply(text, List.of(), false);
    }

    public static Reply send(String text, List<List<Button>> keyboard) {
        return new Reply(text, keyboard, false);
    }

    public static Reply replace(String text) {
        return new Reply(text, List.of(), true);
    }

    public static Reply replace(String text, List<List<Button>> keyboard) {
        return new Reply(text, keyboard, true);
    }
}
