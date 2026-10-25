package dev.obsidra.briefbot.lead;

/** Клиент уже отправил столько заявок за сутки, сколько разрешено в {@code bot.daily-limit}. */
public class LeadLimitExceededException extends RuntimeException {

    public LeadLimitExceededException(long tgUserId) {
        super("Лимит заявок за сутки исчерпан, пользователь " + tgUserId);
    }
}
