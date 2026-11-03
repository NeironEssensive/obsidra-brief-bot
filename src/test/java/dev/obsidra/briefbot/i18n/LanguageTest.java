package dev.obsidra.briefbot.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTest {

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "be", "kk", "RU", "ru-RU", "uk_UA", " kk "})
    void russianSpeakingClientsGetRussian(String languageCode) {
        assertThat(Language.fromTelegram(languageCode)).isEqualTo(Language.RU);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void unknownLanguageCodeMeansRussian(String languageCode) {
        assertThat(Language.fromTelegram(languageCode)).isEqualTo(Language.RU);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-GB", "de", "es", "pt-br", "zh-hans"})
    void everyoneElseGetsEnglish(String languageCode) {
        assertThat(Language.fromTelegram(languageCode)).isEqualTo(Language.EN);
    }
}
