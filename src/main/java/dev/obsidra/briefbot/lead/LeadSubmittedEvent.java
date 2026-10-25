package dev.obsidra.briefbot.lead;

/** Публикуется после сохранения новой заявки; уведомление владельцу уходит после коммита транзакции. */
public record LeadSubmittedEvent(Lead lead) {
}
