package dev.obsidra.briefbot.lead;

/** Клиент сам отозвал заявку; владельцу уходит уведомление после коммита транзакции. */
public record LeadWithdrawnEvent(Lead lead) {
}
