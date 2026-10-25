package dev.obsidra.briefbot.telegram;

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

    private final BotProperties props;
    private final BriefBotUpdateConsumer consumer;
    private TelegramBotsLongPollingApplication application;
    private volatile boolean running;

    BotLauncher(BotProperties props, BriefBotUpdateConsumer consumer) {
        this.props = props;
        this.consumer = consumer;
    }

    @Override
    public void start() {
        application = new TelegramBotsLongPollingApplication();
        try {
            application.registerBot(props.token(), consumer);
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Не удалось подключиться к Telegram — проверьте BOT_TOKEN", e);
        }
        running = true;
        log.info("Бот подключён к Telegram и ждёт сообщений");
    }

    @Override
    public void stop() {
        try {
            application.close();
        } catch (Exception e) {
            log.warn("Не удалось аккуратно остановить long polling", e);
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
