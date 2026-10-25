package dev.obsidra.briefbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandParsingTest {

    @ParameterizedTest
    @CsvSource({
            "/start, /start",
            "/start@obsidra_bot, /start",
            "/START site, /start",
            "/leads@Obsidra_Bot extra, /leads",
            "привет, ''",
            "сайт /start, ''"
    })
    void extractsCommandName(String text, String expected) {
        assertThat(BriefBotUpdateConsumer.command(text)).isEqualTo(expected);
    }
}
