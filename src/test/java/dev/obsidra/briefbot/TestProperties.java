package dev.obsidra.briefbot;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import dev.obsidra.briefbot.config.BotProperties;

public final class TestProperties {

    private TestProperties() {
    }

    public static BotProperties bot() {
        return new BotProperties("test-token", 0, false,
                List.of("до 20 тыс. ₽", "20–50 тыс. ₽", "50–150 тыс. ₽", "больше 150 тыс. ₽", "Пока не знаю"),
                3, Duration.ofHours(24), "https://t.me/obsidra_dev", "https://example.org/privacy",
                ZoneId.of("Europe/Moscow"));
    }
}
