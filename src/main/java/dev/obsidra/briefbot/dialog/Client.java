package dev.obsidra.briefbot.dialog;

import dev.obsidra.briefbot.i18n.Language;

/**
 * Тот, кто пишет боту.
 *
 * @param username ник в Telegram без «@» или {@code null}, если ника нет
 */
public record Client(long userId, long chatId, String username, String firstName, Language language) {

    public boolean hasUsername() {
        return username != null && !username.isBlank();
    }
}
