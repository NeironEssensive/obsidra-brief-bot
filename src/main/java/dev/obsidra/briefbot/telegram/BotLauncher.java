package dev.obsidra.briefbot.telegram;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import dev.obsidra.briefbot.config.BotProperties;

/**
 * Подключает бота к Telegram через long polling, когда контекст Spring полностью поднят,
 * и отключает при остановке приложения. Публичный адрес и HTTPS для этого не нужны.
 */
@Component
@ConditionalOnProperty(name = "bot.polling-enabled", havingValue = "true", matchIfMissing = true)
class BotLauncher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BotLauncher.class);
    /** Сеть до api.telegram.org бывает нестабильной — не падаем с первого же обрыва. */
    private static final int START_ATTEMPTS = 5;

    private final BotProperties props;
    private final BriefBotUpdateConsumer consumer;
    private final OkHttpClient httpClient;
    private TelegramBotsLongPollingApplication application;
    private volatile boolean running;

    BotLauncher(BotProperties props, BriefBotUpdateConsumer consumer, OkHttpClient telegramHttpClient) {
        this.props = props;
        this.consumer = consumer;
        this.httpClient = telegramHttpClient;
    }

    @Override
    public void start() {
        for (int attempt = 1; ; attempt++) {
            // после неудачной попытки приложение пересоздаём: регистрация бота в старом могла остаться
            application = new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> httpClient);
            try {
                application.registerBot(props.token(), consumer);
                break;
            } catch (TelegramApiException e) {
                closeQuietly();
                if (attempt == START_ATTEMPTS) {
                    throw new IllegalStateException("Не удалось подключиться к Telegram за " + attempt
                            + " попыток — проверьте BOT_TOKEN и доступ к api.telegram.org (настройка bot.proxy)", e);
                }
                log.warn("Telegram не ответил (попытка {} из {}): {}", attempt, START_ATTEMPTS, e.getMessage());
                pause(Duration.ofSeconds(2L * attempt));
            }
        }
        running = true;
        log.info("Бот подключён к Telegram{} и ждёт сообщений",
                props.proxy() == null || props.proxy().isBlank() ? "" : " через прокси " + props.proxy());
    }

    @Override
    public void stop() {
        closeQuietly();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void closeQuietly() {
        try {
            application.close();
        } catch (Exception e) {
            log.warn("Не удалось аккуратно остановить long polling", e);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запуск бота прерван", e);
        }
    }
}
