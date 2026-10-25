package dev.obsidra.briefbot.telegram;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import dev.obsidra.briefbot.message.BotResponse;
import dev.obsidra.briefbot.message.Button;
import dev.obsidra.briefbot.message.Reply;

/** Переводит {@link Reply} в вызовы Bot API. Ошибки Telegram логируются и не роняют обработку апдейта. */
@Component
public class ReplySender {

    private static final Logger log = LoggerFactory.getLogger(ReplySender.class);
    private static final int SEND_ATTEMPTS = 3;
    private static final LinkPreviewOptions NO_PREVIEW = LinkPreviewOptions.builder().isDisabled(true).build();

    private final TelegramClient telegram;

    public ReplySender(TelegramClient telegram) {
        this.telegram = telegram;
    }

    /**
     * @param sourceMessageId сообщение, на кнопку которого нажали ({@code null} для текстовых сообщений)
     * @param callbackQueryId id нажатия кнопки — на него обязательно нужно ответить, иначе кнопка «зависнет»
     */
    public void deliver(long chatId, Integer sourceMessageId, BotResponse response, String callbackQueryId) {
        if (callbackQueryId != null) {
            execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).text(response.notice()).build());
        }
        for (Reply reply : response.replies()) {
            if (reply.replaceOriginal() && sourceMessageId != null) {
                edit(chatId, sourceMessageId, reply);
            } else {
                send(chatId, reply);
            }
        }
    }

    /** @return {@code true}, если Telegram принял сообщение */
    public boolean send(long chatId, Reply reply) {
        return execute(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(reply.text())
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(NO_PREVIEW)
                .replyMarkup(markup(reply.keyboard()))
                .build());
    }

    private void edit(long chatId, int messageId, Reply reply) {
        // без replyMarkup Telegram убирает кнопки — так ответ «фиксируется» в истории чата
        execute(EditMessageText.builder()
                .chatId(String.valueOf(chatId))
                .messageId(messageId)
                .text(reply.text())
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(NO_PREVIEW)
                .replyMarkup(markup(reply.keyboard()))
                .build());
    }

    private static InlineKeyboardMarkup markup(List<List<Button>> keyboard) {
        if (keyboard.isEmpty()) {
            return null;
        }
        List<InlineKeyboardRow> rows = keyboard.stream()
                .map(row -> new InlineKeyboardRow(row.stream().map(ReplySender::toTelegram).toList()))
                .toList();
        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton toTelegram(Button button) {
        return InlineKeyboardButton.builder()
                .text(button.text())
                .callbackData(button.callbackData())
                .url(button.url())
                .build();
    }

    private boolean execute(BotApiMethod<?> method) {
        for (int attempt = 1; ; attempt++) {
            try {
                telegram.execute(method);
                return true;
            } catch (TelegramApiException e) {
                // повторное нажатие на ту же кнопку пытается записать в сообщение тот же текст — это не ошибка
                if (String.valueOf(e.getMessage()).contains("message is not modified")) {
                    log.debug("Сообщение не изменилось: {}", method.getMethod());
                    return true;
                }
                // обрыв сети повторяем, а ошибку самого Telegram (неверный запрос, бот заблокирован) — нет
                boolean networkError = e.getCause() instanceof IOException;
                if (networkError && attempt < SEND_ATTEMPTS) {
                    log.debug("Сеть оборвалась при {}, повтор {}", method.getMethod(), attempt);
                    pause(attempt);
                    continue;
                }
                log.warn("Telegram не принял {}: {}", method.getMethod(), e.getMessage());
                return false;
            }
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
