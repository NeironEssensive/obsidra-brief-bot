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
import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.i18n.LanguageStore;
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
    private final LanguageStore languages;

    public BriefBotUpdateConsumer(DialogService dialog, AdminCommands admin, ReplySender sender,
            LanguageStore languages) {
        this.dialog = dialog;
        this.admin = admin;
        this.sender = sender;
        this.languages = languages;
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
            case "/myid" -> myId(client);
            case "/leads" -> admin.isAdmin(client.userId()) ? admin.latest() : unknownCommand(client);
            case "/stats" -> admin.isAdmin(client.userId()) ? admin.stats() : unknownCommand(client);
            case "/lead" -> admin.isAdmin(client.userId()) ? admin.lead(argument(text)) : unknownCommand(client);
            case "" -> dialog.onText(client, text);
            default -> unknownCommand(client);
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
                    : BotResponse.notice(Texts.get(client.language(), "stale_button"));
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

    /** Всё после имени команды: {@code "/lead 1001"} → {@code "1001"}; без аргумента — пустая строка. */
    static String argument(String text) {
        String[] parts = text.split("\\s+", 2);
        return parts.length > 1 ? parts[1].strip() : "";
    }

    /** ID пишется и в лог: оттуда его удобно скопировать в BOT_ADMIN_CHAT_ID без ошибок. */
    private static BotResponse myId(Client client) {
        log.info("/myid: Telegram ID {} (@{})", client.userId(), client.username());
        return BotResponse.of(Reply.send(Texts.get(client.language(), "my_id", client.userId())));
    }

    private static BotResponse unknownCommand(Client client) {
        return BotResponse.of(Reply.send(Texts.get(client.language(), "unknown_command")));
    }

    private Client client(User user, long chatId) {
        Language language = languages.find(user.getId())
                .orElseGet(() -> Language.fromTelegram(user.getLanguageCode()));
        return new Client(user.getId(), chatId, user.getUserName(), user.getFirstName(), language);
    }
}
