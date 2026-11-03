package dev.obsidra.briefbot.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.obsidra.briefbot.TestProperties;
import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.ProjectType;

class LeadCardsTest {

    private final LeadCards cards = new LeadCards(TestProperties.bot());

    @Test
    void cardOfEnglishLeadStaysRussianAndShowsClientLanguage() {
        String card = cards.card(lead(Language.EN));

        assertThat(card).startsWith("🆕 <b>Заявка №1001</b>").contains(
                "<b>Что сделать:</b> Сайт",
                "<b>Задача:</b> Landing page for a coffee shop",
                "<b>Что есть:</b> Дизайн, Примеры, что нравится",
                "<b>Бюджет:</b> $250–600",
                "<b>Сроки:</b> В течение месяца",
                "<b>Язык клиента:</b> 🇬🇧 английский");
    }

    @Test
    void cardOfRussianLeadShowsRussian() {
        assertThat(cards.card(lead(Language.RU))).contains("<b>Язык клиента:</b> 🇷🇺 русский");
    }

    private static Lead lead(Language language) {
        Lead lead = mock(Lead.class);
        when(lead.getId()).thenReturn(1001L);
        when(lead.getTgUserId()).thenReturn(77L);
        when(lead.getClientName()).thenReturn("John");
        when(lead.getTgUsername()).thenReturn("john");
        when(lead.getProjectType()).thenReturn(ProjectType.SITE);
        when(lead.getDescription()).thenReturn("Landing page for a coffee shop");
        when(lead.getAssets()).thenReturn(Set.of(Asset.REFERENCES, Asset.DESIGN));
        when(lead.getBudget()).thenReturn("$250–600");
        when(lead.getDeadline()).thenReturn(Deadline.MONTH);
        when(lead.getContact()).thenReturn("@john");
        when(lead.getLanguage()).thenReturn(language);
        when(lead.getCreatedAt()).thenReturn(Instant.parse("2026-11-03T10:00:00Z"));
        return lead;
    }
}
