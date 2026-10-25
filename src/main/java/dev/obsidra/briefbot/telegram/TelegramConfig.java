package dev.obsidra.briefbot.telegram;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import dev.obsidra.briefbot.config.BotProperties;

@Configuration
public class TelegramConfig {

    @Bean
    TelegramClient telegramClient(BotProperties props) {
        return new OkHttpTelegramClient(props.token());
    }
}
