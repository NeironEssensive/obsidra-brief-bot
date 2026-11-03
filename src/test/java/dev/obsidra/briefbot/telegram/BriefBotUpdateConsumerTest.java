package dev.obsidra.briefbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import dev.obsidra.briefbot.admin.AdminCommands;
import dev.obsidra.briefbot.dialog.Client;
import dev.obsidra.briefbot.dialog.DialogService;
import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.i18n.LocalLanguageStore;
import dev.obsidra.briefbot.message.BotResponse;
import dev.obsidra.briefbot.message.Reply;

@ExtendWith(MockitoExtension.class)
class BriefBotUpdateConsumerTest {

    private static final long USER_ID = 42L;

    @Mock
    private DialogService dialog;

    @Mock
    private AdminCommands admin;

    @Mock
    private ReplySender sender;

    private final LocalLanguageStore languages = new LocalLanguageStore();
    private BriefBotUpdateConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BriefBotUpdateConsumer(dialog, admin, sender, languages);
    }

    @ParameterizedTest
    @CsvSource({"ru, RU", "uk, RU", "en, EN", "de, EN", "'', RU"})
    void languageFollowsTelegramSettings(String languageCode, Language expected) {
        assertThat(clientOf(message("/start", languageCode)).language()).isEqualTo(expected);
    }

    @Test
    void missingLanguageCodeMeansRussian() {
        assertThat(clientOf(message("/start", null)).language()).isEqualTo(Language.RU);
    }

    @Test
    void chosenLanguageWinsOverTelegramSettings() {
        languages.save(USER_ID, Language.EN);

        assertThat(clientOf(message("/start", "ru")).language()).isEqualTo(Language.EN);
    }

    @Test
    void unknownCommandIsAnsweredInClientLanguage() {
        consumer.consume(message("/foo", "en"));

        ArgumentCaptor<BotResponse> response = ArgumentCaptor.forClass(BotResponse.class);
        verify(sender).deliver(eq(USER_ID), isNull(), response.capture(), isNull());
        assertThat(response.getValue().replies()).singleElement().extracting(Reply::text)
                .isEqualTo("I don't know this command. To send a request, tap /start.");
    }

    private Client clientOf(Update update) {
        when(dialog.start(any())).thenReturn(BotResponse.of());
        consumer.consume(update);
        ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
        verify(dialog).start(client.capture());
        return client.getValue();
    }

    private static Update message(String text, String languageCode) {
        User user = new User(USER_ID, "John", false);
        user.setLanguageCode(languageCode);
        Message message = new Message();
        message.setChat(new Chat(USER_ID, "private"));
        message.setFrom(user);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
