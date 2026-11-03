package dev.obsidra.briefbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import dev.obsidra.briefbot.i18n.Language;

class BotPropertiesTest {

    @Test
    void applicationYmlHasBudgetsForEveryLanguage() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        BotProperties props = binder.bind("bot", BotProperties.class).get();

        assertThat(props.budgets(Language.RU)).hasSize(5).first().isEqualTo("до 20 тыс. ₽");
        assertThat(props.budgets(Language.EN)).hasSize(5).last().isEqualTo("Not sure yet");
    }

    @Test
    void missingBudgetsForLanguageFailStartup() {
        assertThatThrownBy(() -> new BotProperties("test-token", 0, false, Map.of(Language.RU, List.of("до 20 тыс. ₽")),
                3, Duration.ofHours(24), "https://t.me/obsidra_dev", "https://example.org/privacy",
                ZoneId.of("Europe/Moscow"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bot.budgets.en");
    }
}
