package dev.obsidra.briefbot.message;

/** Кнопка под сообщением: либо присылает боту {@code callbackData}, либо открывает {@code url}. */
public record Button(String text, String callbackData, String url) {

    public static Button callback(String text, String callbackData) {
        return new Button(text, callbackData, null);
    }

    public static Button link(String text, String url) {
        return new Button(text, null, url);
    }
}
