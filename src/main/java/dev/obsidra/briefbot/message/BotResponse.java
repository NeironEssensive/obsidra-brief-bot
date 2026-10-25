package dev.obsidra.briefbot.message;

import java.util.List;

/**
 * Всё, чем бот отвечает на одно действие пользователя.
 *
 * @param replies сообщения, по порядку
 * @param notice  короткая всплывающая подсказка над нажатой кнопкой (или {@code null})
 */
public record BotResponse(List<Reply> replies, String notice) {

    public BotResponse {
        replies = List.copyOf(replies);
    }

    public static BotResponse of(Reply... replies) {
        return new BotResponse(List.of(replies), null);
    }

    public static BotResponse notice(String notice) {
        return new BotResponse(List.of(), notice);
    }
}
