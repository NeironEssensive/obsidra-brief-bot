package dev.obsidra.briefbot.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.ProjectType;

class TextsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final Pattern TAG = Pattern.compile("</?[a-z]+");

    @Test
    void everyKeyIsTranslatedToEveryLanguage() {
        Set<String> allKeys = new TreeSet<>();
        for (Language language : Language.values()) {
            allKeys.addAll(Texts.bundle(language).keySet());
        }

        for (Language language : Language.values()) {
            ResourceBundle bundle = Texts.bundle(language);
            assertThat(bundle.keySet()).as("ключи messages_%s", language.locale())
                    .containsExactlyInAnyOrderElementsOf(allKeys);
            for (String key : allKeys) {
                assertThat(bundle.getString(key)).as("%s в messages_%s", key, language.locale()).isNotBlank();
            }
        }
    }

    @Test
    void translationsKeepPlaceholdersAndMarkup() {
        ResourceBundle russian = Texts.bundle(Language.RU);
        for (Language language : Language.values()) {
            ResourceBundle bundle = Texts.bundle(language);
            for (String key : russian.keySet()) {
                assertThat(matches(PLACEHOLDER, bundle.getString(key)))
                        .as("подстановки %s в messages_%s", key, language.locale())
                        .containsExactlyInAnyOrderElementsOf(matches(PLACEHOLDER, russian.getString(key)));
                assertThat(matches(TAG, bundle.getString(key)))
                        .as("разметка %s в messages_%s", key, language.locale())
                        .containsExactlyElementsOf(matches(TAG, russian.getString(key)));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyAnswerOptionHasLabel(Language language) {
        for (ProjectType type : ProjectType.values()) {
            assertThat(Texts.type(language, type)).isNotBlank();
            assertThat(Texts.askDescription(language, type)).doesNotContain("{0}");
        }
        for (Asset asset : Asset.values()) {
            assertThat(Texts.asset(language, asset)).isNotBlank();
        }
        for (Deadline deadline : Deadline.values()) {
            assertThat(Texts.deadline(language, deadline)).isNotBlank();
        }
        for (Step step : Step.values()) {
            if (step.isEditable()) {
                assertThat(Texts.field(language, step)).isNotBlank();
            }
        }
    }

    @Test
    void placeholdersAreFilledInOrder() {
        assertThat(Texts.withdrawOffer(Language.EN, 1001L, ProjectType.BOT))
                .isEqualTo("Withdraw request #1001 (Telegram bot)? We won't contact you about it anymore.");
        assertThat(Texts.withdrawOffer(Language.RU, 1001L, ProjectType.BOT))
                .isEqualTo("Отозвать заявку №1001 («Telegram-бот»)? Мы больше не будем связываться по ней.");
    }

    private static List<String> matches(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(MatchResult::group).toList();
    }
}
