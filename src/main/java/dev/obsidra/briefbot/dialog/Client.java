package dev.obsidra.briefbot.dialog;

/**
 * Тот, кто пишет боту.
 *
 * @param username ник в Telegram без «@» или {@code null}, если ника нет
 */
public record Client(long userId, long chatId, String username, String firstName) {

    public boolean hasUsername() {
        return username != null && !username.isBlank();
    }
}
