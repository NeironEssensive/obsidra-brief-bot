package dev.obsidra.briefbot;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.i18n.Language;

public final class TestProperties {

    private TestProperties() {
    }

    public static BotProperties bot() {
        return new BotProperties("test-token", 0, false,
                Map.of(Language.RU, List.of("до 20 тыс. ₽", "20–50 тыс. ₽", "50–150 тыс. ₽", "больше 150 тыс. ₽",
                                "Пока не знаю"),
                        Language.EN, List.of("Under $250", "$250–600", "$600–2,000", "Over $2,000", "Not sure yet")),
                3, Duration.ofHours(24), "https://t.me/obsidra_dev", "https://example.org/privacy",
                ZoneId.of("Europe/Moscow"), null);
    }
}
