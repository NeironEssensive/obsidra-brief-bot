package dev.obsidra.briefbot.telegram;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import dev.obsidra.briefbot.admin.AdminCommands;
import dev.obsidra.briefbot.dialog.Client;
import dev.obsidra.briefbot.dialog.DialogService;
import dev.obsidra.briefbot.dialog.Texts;
import dev.obsidra.briefbot.message.BotResponse;
import dev.obsidra.briefbot.message.Reply;
import dev.obsidra.briefbot.notify.LeadCards;

/**
 * Точка входа апдейтов: разбирает команды и нажатия кнопок и передаёт их диалогу или админке.
 * Апдейты приходят строго по одному (single thread consumer), поэтому гонок за черновик нет.
 */
@Component
public class BriefBotUpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(BriefBotUpdateConsumer.class);

    private final DialogService dialog;
    private final AdminCommands admin;
    private final ReplySender sender;

    public BriefBotUpdateConsumer(DialogService dialog, AdminCommands admin, ReplySender sender) {
        this.dialog = dialog;
        this.admin = admin;
        this.sender = sender;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage()) {
                onMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
            }
        } catch (RuntimeException e) {
            log.error("Не удалось обработать апдейт {}", update.getUpdateId(), e);
        }
    }

    private void onMessage(Message message) {
        // бот работает только в личке; в группах и каналах молчит
        if (!message.isUserMessage() || !message.hasText() || message.getFrom() == null) {
            return;
        }
        Client client = client(message.getFrom(), message.getChatId());
        String text = message.getText().strip();
        BotResponse response = switch (command(text)) {
            case "/start" -> dialog.start(client);
            case "/cancel" -> dialog.cancel(client);
            case "/myid" -> BotResponse.of(Reply.send("Ваш Telegram ID: <code>" + client.userId() + "</code>"));
            case "/leads" -> admin.isAdmin(client.userId()) ? admin.latest() : unknownCommand();
            case "/stats" -> admin.isAdmin(client.userId()) ? admin.stats() : unknownCommand();
            case "" -> dialog.onText(client, text);
            default -> unknownCommand();
        };
        sender.deliver(message.getChatId(), null, response, null);
    }

    private void onCallback(CallbackQuery query) {
        MaybeInaccessibleMessage message = query.getMessage();
        if (message == null || query.getData() == null) {
            sender.deliver(query.getFrom().getId(), null, BotResponse.notice(null), query.getId());
            return;
        }
        Client client = client(query.getFrom(), message.getChatId());
        String data = query.getData();
        BotResponse response;
        if (data.startsWith(LeadCards.STATUS_CALLBACK_PREFIX)) {
            response = admin.isAdmin(client.userId())
                    ? admin.onStatusButton(data)
                    : BotResponse.notice(Texts.STALE_BUTTON);
        } else {
            response = dialog.onButton(client, data);
        }
        sender.deliver(message.getChatId(), message.getMessageId(), response, query.getId());
    }

    /**
     * Имя команды в нижнем регистре без «@имя_бота» и аргументов: {@code "/start@obsidra_bot site"} → {@code "/start"}.
     * Для обычного текста — пустая строка.
     */
    static String command(String text) {
        if (!text.startsWith("/")) {
            return "";
        }
        String first = text.split("\\s+", 2)[0];
        int at = first.indexOf('@');
        return (at < 0 ? first : first.substring(0, at)).toLowerCase(Locale.ROOT);
    }

    private static BotResponse unknownCommand() {
        return BotResponse.of(Reply.send(Texts.UNKNOWN_COMMAND));
    }

    private static Client client(User user, long chatId) {
        return new Client(user.getId(), chatId, user.getUserName(), user.getFirstName());
    }
}
