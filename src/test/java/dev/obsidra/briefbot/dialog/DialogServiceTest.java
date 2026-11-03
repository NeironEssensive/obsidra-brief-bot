package dev.obsidra.briefbot.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.obsidra.briefbot.TestProperties;
import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.i18n.LocalLanguageStore;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.LeadLimitExceededException;
import dev.obsidra.briefbot.lead.LeadRequest;
import dev.obsidra.briefbot.lead.LeadService;
import dev.obsidra.briefbot.lead.ProjectType;
import dev.obsidra.briefbot.message.BotResponse;
import dev.obsidra.briefbot.message.Button;
import dev.obsidra.briefbot.message.Reply;

@ExtendWith(MockitoExtension.class)
class DialogServiceTest {

    private static final Client IVAN = new Client(42L, 42L, "ivan", "Иван", Language.RU);
    private static final Client NO_USERNAME = new Client(7L, 7L, null, "Аня", Language.RU);
    private static final Client JOHN = new Client(77L, 77L, "john", "John", Language.EN);

    @Mock
    private LeadService leads;

    private final InMemoryDraftStore drafts = new InMemoryDraftStore();
    private final LocalLanguageStore languages = new LocalLanguageStore();
    private DialogService dialog;

    @BeforeEach
    void setUp() {
        dialog = new DialogService(drafts, languages, leads, TestProperties.bot());
    }

    @Test
    void fullBriefIsSubmittedWithAllAnswers() {
        Lead saved = lead(1001L);
        when(leads.submit(any())).thenReturn(saved);

        fillUpToConfirm(IVAN);
        BotResponse response = dialog.onButton(IVAN, Callbacks.SEND);

        ArgumentCaptor<LeadRequest> captor = ArgumentCaptor.forClass(LeadRequest.class);
        verify(leads).submit(captor.capture());
        LeadRequest request = captor.getValue();
        assertThat(request.tgUserId()).isEqualTo(42L);
        assertThat(request.tgUsername()).isEqualTo("ivan");
        assertThat(request.type()).isEqualTo(ProjectType.BOT);
        assertThat(request.description()).isEqualTo("Бот для записи клиентов в барбершоп");
        assertThat(request.assets()).containsExactly(Asset.DESIGN);
        assertThat(request.budget()).isEqualTo("20–50 тыс. ₽");
        assertThat(request.deadline()).isEqualTo(Deadline.MONTH);
        assertThat(request.contact()).isEqualTo("@ivan");
        assertThat(request.language()).isEqualTo(Language.RU);

        assertThat(response.replies().getFirst().replaceOriginal()).isTrue();
        assertThat(response.replies().getLast().text()).contains("№1001");
        assertThat(drafts.find(IVAN.userId())).isEmpty();
    }

    @Test
    void englishClientGetsWholeBriefInEnglish() {
        Lead saved = lead(1001L);
        when(leads.submit(any())).thenReturn(saved);

        Reply typeQuestion = dialog.onButton(JOHN, Callbacks.START).replies().getLast();
        assertThat(typeQuestion.text()).isEqualTo("<b>1/6.</b> What do you need?");
        assertThat(typeQuestion.keyboard().getFirst()).extracting(Button::text)
                .containsExactly("Website", "Telegram bot");

        dialog.onButton(JOHN, Callbacks.TYPE + "BOT");
        dialog.onText(JOHN, "Booking bot for a barbershop");
        dialog.onButton(JOHN, Callbacks.ASSET + "DESIGN");
        Reply budgetQuestion = dialog.onButton(JOHN, Callbacks.ASSETS_DONE).replies().getLast();
        assertThat(budgetQuestion.keyboard().stream().flatMap(List::stream).map(Button::text))
                .containsExactly("Under $250", "$250–600", "$600–2,000", "Over $2,000", "Not sure yet");
        dialog.onButton(JOHN, Callbacks.BUDGET + "1");
        dialog.onButton(JOHN, Callbacks.DEADLINE + "MONTH");
        Reply summary = dialog.onButton(JOHN, Callbacks.CONTACT_TELEGRAM).replies().getLast();
        assertThat(summary.text()).startsWith("<b>Check your request</b>").contains(
                "<b>Project:</b> Telegram bot",
                "<b>You have:</b> Design",
                "<b>Budget:</b> $250–600",
                "<b>Timeline:</b> Within a month",
                "<a href=\"https://example.org/privacy\">privacy policy</a>");

        BotResponse sent = dialog.onButton(JOHN, Callbacks.SEND);

        ArgumentCaptor<LeadRequest> captor = ArgumentCaptor.forClass(LeadRequest.class);
        verify(leads).submit(captor.capture());
        assertThat(captor.getValue().language()).isEqualTo(Language.EN);
        assertThat(captor.getValue().budget()).isEqualTo("$250–600");
        assertThat(sent.replies().getLast().text())
                .isEqualTo("✅ Request #1001 received! We'll reply within one business day.");
    }

    @Test
    void welcomeOffersTheOtherLanguage() {
        Reply russian = dialog.start(IVAN).replies().getFirst();
        Reply english = dialog.start(JOHN).replies().getFirst();

        assertThat(russian.text()).startsWith("Привет!");
        assertThat(russian.keyboard().getLast()).singleElement()
                .isEqualTo(Button.callback("🇬🇧 English", Callbacks.LANGUAGE + "EN"));
        assertThat(english.text()).startsWith("Hi!");
        assertThat(english.keyboard().getLast()).singleElement()
                .isEqualTo(Button.callback("🇷🇺 Русский", Callbacks.LANGUAGE + "RU"));
    }

    @Test
    void languageButtonRemembersChoiceAndRedrawsWelcome() {
        BotResponse response = dialog.onButton(IVAN, Callbacks.LANGUAGE + "EN");

        assertThat(response.replies()).singleElement().satisfies(reply -> {
            assertThat(reply.replaceOriginal()).isTrue();
            assertThat(reply.text()).isEqualTo(Texts.get(Language.EN, "welcome"));
            assertThat(reply.keyboard().getLast()).singleElement()
                    .isEqualTo(Button.callback("🇷🇺 Русский", Callbacks.LANGUAGE + "RU"));
        });
        assertThat(languages.find(IVAN.userId())).contains(Language.EN);
        assertThat(drafts.find(IVAN.userId())).isEmpty();
    }

    @Test
    void unknownLanguageButtonIsIgnored() {
        BotResponse response = dialog.onButton(IVAN, Callbacks.LANGUAGE + "DE");

        assertThat(response.notice()).isEqualTo(Texts.get(Language.RU, "stale_button"));
        assertThat(languages.find(IVAN.userId())).isEmpty();
    }

    @Test
    void buttonAnswerReplacesQuestionAndAsksNext() {
        dialog.onButton(IVAN, Callbacks.START);

        BotResponse response = dialog.onButton(IVAN, Callbacks.TYPE + "SITE");

        assertThat(response.replies()).hasSize(2);
        Reply answered = response.replies().get(0);
        assertThat(answered.replaceOriginal()).isTrue();
        assertThat(answered.text()).contains("→ <b>Сайт</b>");
        assertThat(answered.keyboard()).isEmpty();
        assertThat(response.replies().get(1).text()).startsWith("<b>2/6.</b>");
    }

    @Test
    void tooShortDescriptionIsRejected() {
        dialog.onButton(IVAN, Callbacks.START);
        dialog.onButton(IVAN, Callbacks.TYPE + "SITE");

        BotResponse response = dialog.onText(IVAN, "сайт");

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "description.too_short"));
        assertThat(draft(IVAN).getStep()).isEqualTo(Step.DESCRIPTION);
    }

    @Test
    void ideaOnlyExcludesOtherAssets() {
        goToAssets(IVAN);

        dialog.onButton(IVAN, Callbacks.ASSET + "SPEC");
        dialog.onButton(IVAN, Callbacks.ASSET + "DESIGN");
        dialog.onButton(IVAN, Callbacks.ASSET + "IDEA_ONLY");
        assertThat(draft(IVAN).getAssets()).containsExactly(Asset.IDEA_ONLY);

        dialog.onButton(IVAN, Callbacks.ASSET + "SPEC");
        assertThat(draft(IVAN).getAssets()).containsExactly(Asset.SPEC);
    }

    @Test
    void assetsCannotBeLeftEmpty() {
        goToAssets(IVAN);

        BotResponse response = dialog.onButton(IVAN, Callbacks.ASSETS_DONE);

        assertThat(response.notice()).isEqualTo(Texts.get(Language.RU, "pick_asset"));
        assertThat(draft(IVAN).getStep()).isEqualTo(Step.ASSETS);
    }

    @Test
    void buttonFromAnotherStepIsIgnored() {
        dialog.onButton(IVAN, Callbacks.START);

        BotResponse response = dialog.onButton(IVAN, Callbacks.BUDGET + "0");

        assertThat(response.notice()).isEqualTo(Texts.get(Language.RU, "stale_button"));
        assertThat(response.replies()).isEmpty();
        assertThat(draft(IVAN).getBudget()).isNull();
        assertThat(draft(IVAN).getStep()).isEqualTo(Step.TYPE);
    }

    @Test
    void editedAnswerReturnsToConfirmation() {
        fillUpToConfirm(IVAN);
        dialog.onButton(IVAN, Callbacks.EDIT);
        dialog.onButton(IVAN, Callbacks.EDIT_FIELD + "BUDGET");
        assertThat(draft(IVAN).getStep()).isEqualTo(Step.BUDGET);

        BotResponse response = dialog.onButton(IVAN, Callbacks.BUDGET + "3");

        assertThat(draft(IVAN).getStep()).isEqualTo(Step.CONFIRM);
        assertThat(draft(IVAN).getBudget()).isEqualTo("больше 150 тыс. ₽");
        assertThat(draft(IVAN).getDeadline()).isEqualTo(Deadline.MONTH);
        assertThat(response.replies().getLast().text()).contains("Проверьте заявку");
    }

    @Test
    void dailyLimitKeepsDraftForLater() {
        when(leads.submit(any())).thenThrow(new LeadLimitExceededException(IVAN.userId()));
        fillUpToConfirm(IVAN);

        BotResponse response = dialog.onButton(IVAN, Callbacks.SEND);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "limit_reached"));
        assertThat(drafts.find(IVAN.userId())).isPresent();
    }

    @Test
    void clientWithoutUsernameTypesContact() {
        goToAssets(NO_USERNAME);
        dialog.onButton(NO_USERNAME, Callbacks.ASSET + "SPEC");
        dialog.onButton(NO_USERNAME, Callbacks.ASSETS_DONE);
        dialog.onButton(NO_USERNAME, Callbacks.BUDGET + "0");

        BotResponse contactQuestion = dialog.onButton(NO_USERNAME, Callbacks.DEADLINE + "NO_RUSH");
        assertThat(contactQuestion.replies().getLast().text())
                .isEqualTo(Texts.get(Language.RU, "ask.contact.no_username"));
        assertThat(contactQuestion.replies().getLast().keyboard()).isEmpty();

        dialog.onText(NO_USERNAME, "anya@example.com");

        assertThat(draft(NO_USERNAME).getContact()).isEqualTo("anya@example.com");
        assertThat(draft(NO_USERNAME).getStep()).isEqualTo(Step.CONFIRM);
    }

    @Test
    void userInputIsEscapedInSummary() {
        Draft draft = new Draft(IVAN.userId());
        draft.setDescription("<b>Сайт</b> & магазин");

        String summary = Texts.summary(draft, Language.RU, null);

        assertThat(summary).contains("&lt;b&gt;Сайт&lt;/b&gt; &amp; магазин").doesNotContain("<b>Сайт</b>");
    }

    @Test
    void startWithUnfinishedDraftOffersToResume() {
        dialog.onButton(IVAN, Callbacks.START);

        BotResponse response = dialog.start(IVAN);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "resume.offer"));
    }

    @Test
    void cancelOnConfirmationDeletesDraftWithoutSubmitting() {
        fillUpToConfirm(IVAN);

        BotResponse response = dialog.onButton(IVAN, Callbacks.CANCEL);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "cancelled"));
        assertThat(drafts.find(IVAN.userId())).isEmpty();
        verify(leads, never()).submit(any());
    }

    @Test
    void cancelWithDraftDeletesItWithoutTouchingSubmittedLeads() {
        dialog.onButton(IVAN, Callbacks.START);

        BotResponse response = dialog.cancel(IVAN);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "cancelled"));
        assertThat(drafts.find(IVAN.userId())).isEmpty();
        verify(leads, never()).activeLeadsOf(anyLong());
    }

    @Test
    void cancelWithoutDraftOffersToWithdrawActiveLead() {
        Lead active = lead(1001L);
        when(active.getProjectType()).thenReturn(ProjectType.BOT);
        when(leads.activeLeadsOf(IVAN.userId())).thenReturn(List.of(active));

        BotResponse response = dialog.cancel(IVAN);

        Reply offer = response.replies().getFirst();
        assertThat(offer.text()).contains("№1001").contains("Telegram-бот");
        assertThat(offer.keyboard().getFirst()).extracting(Button::callbackData)
                .containsExactly(Callbacks.WITHDRAW + "1001", Callbacks.WITHDRAW_KEEP);
    }

    @Test
    void severalActiveLeadsAreOfferedOneByOne() {
        Lead newer = lead(1002L);
        when(newer.getProjectType()).thenReturn(ProjectType.BACKEND);
        Lead older = lead(1001L);
        when(older.getProjectType()).thenReturn(ProjectType.SITE);
        when(leads.activeLeadsOf(IVAN.userId())).thenReturn(List.of(newer, older));

        Reply choice = dialog.cancel(IVAN).replies().getFirst();

        assertThat(choice.text()).isEqualTo(Texts.get(Language.RU, "withdraw.choose"));
        assertThat(choice.keyboard()).extracting(row -> row.getFirst().callbackData())
                .containsExactly(Callbacks.WITHDRAW + "1002", Callbacks.WITHDRAW + "1001", Callbacks.WITHDRAW_KEEP);
    }

    @Test
    void cancelWithNothingActiveSaysSoInsteadOfFakeCancellation() {
        when(leads.activeLeadsOf(IVAN.userId())).thenReturn(List.of());

        BotResponse response = dialog.cancel(IVAN);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "nothing_to_cancel"));
    }

    @Test
    void confirmedWithdrawalGoesToLeadService() {
        Lead withdrawn = lead(1001L);
        when(leads.withdraw(IVAN.userId(), 1001L)).thenReturn(Optional.of(withdrawn));

        BotResponse response = dialog.onButton(IVAN, Callbacks.WITHDRAW + "1001");

        assertThat(response.replies()).singleElement().satisfies(reply -> {
            assertThat(reply.replaceOriginal()).isTrue();
            assertThat(reply.text()).isEqualTo(Texts.withdrawn(Language.RU, 1001L));
        });
    }

    @Test
    void closedLeadCannotBeWithdrawn() {
        when(leads.withdraw(IVAN.userId(), 1001L)).thenReturn(Optional.empty());

        BotResponse response = dialog.onButton(IVAN, Callbacks.WITHDRAW + "1001");

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "withdraw.impossible"));
    }

    @Test
    void keepingLeadChangesNothing() {
        BotResponse response = dialog.onButton(IVAN, Callbacks.WITHDRAW_KEEP);

        assertThat(response.replies()).singleElement().extracting(Reply::text)
                .isEqualTo(Texts.get(Language.RU, "withdraw.kept"));
        verifyNoInteractions(leads);
    }

    private static Lead lead(long id) {
        Lead lead = mock(Lead.class);
        when(lead.getId()).thenReturn(id);
        return lead;
    }

    private void goToAssets(Client client) {
        dialog.onButton(client, Callbacks.START);
        dialog.onButton(client, Callbacks.TYPE + "SITE");
        dialog.onText(client, "Лендинг для кофейни с меню");
        assertThat(draft(client).getStep()).isEqualTo(Step.ASSETS);
    }

    private void fillUpToConfirm(Client client) {
        dialog.onButton(client, Callbacks.START);
        dialog.onButton(client, Callbacks.TYPE + "BOT");
        dialog.onText(client, "Бот для записи клиентов в барбершоп");
        dialog.onButton(client, Callbacks.ASSET + "DESIGN");
        dialog.onButton(client, Callbacks.ASSETS_DONE);
        dialog.onButton(client, Callbacks.BUDGET + "1");
        dialog.onButton(client, Callbacks.DEADLINE + "MONTH");
        dialog.onButton(client, Callbacks.CONTACT_TELEGRAM);
        assertThat(draft(client).getStep()).isEqualTo(Step.CONFIRM);
    }

    private Draft draft(Client client) {
        return drafts.find(client.userId()).orElseThrow();
    }
}
