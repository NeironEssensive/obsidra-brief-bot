package dev.obsidra.briefbot.dialog;

import static dev.obsidra.briefbot.message.Button.callback;
import static dev.obsidra.briefbot.message.Button.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.i18n.LanguageStore;
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

/**
 * Сценарий брифа: вопросы по шагам {@link Step}, черновик между ответами, отправка заявки.
 * О Telegram ничего не знает — на вход действие клиента, на выход {@link BotResponse}.
 */
@Service
public class DialogService {

    static final int DESCRIPTION_MIN = 10;
    static final int DESCRIPTION_MAX = 1000;
    static final int CONTACT_MIN = 3;
    static final int CONTACT_MAX = 200;

    private final DraftStore drafts;
    private final LanguageStore languages;
    private final LeadService leads;
    private final BotProperties props;

    public DialogService(DraftStore drafts, LanguageStore languages, LeadService leads, BotProperties props) {
        this.drafts = drafts;
        this.languages = languages;
        this.leads = leads;
        this.props = props;
    }

    /** /start: приветствие или предложение продолжить незаконченную заявку. */
    public BotResponse start(Client client) {
        Language lang = client.language();
        if (drafts.find(client.userId()).isPresent()) {
            return BotResponse.of(Reply.send(Texts.get(lang, "resume.offer"), List.of(List.of(
                    callback(Texts.get(lang, "button.resume"), Callbacks.RESUME),
                    callback(Texts.get(lang, "button.restart"), Callbacks.RESTART)))));
        }
        return welcome(lang);
    }

    /**
     * /cancel: незаконченную заявку удаляет сразу, а уже отправленную предлагает отозвать —
     * так клиент не получит «Заявка отменена», когда отменять было нечего.
     */
    public BotResponse cancel(Client client) {
        Language lang = client.language();
        if (drafts.find(client.userId()).isPresent()) {
            drafts.delete(client.userId());
            return BotResponse.of(Reply.send(Texts.get(lang, "cancelled")));
        }
        List<Lead> active = leads.activeLeadsOf(client.userId());
        if (active.isEmpty()) {
            return BotResponse.of(Reply.send(Texts.get(lang, "nothing_to_cancel")));
        }
        if (active.size() == 1) {
            Lead lead = active.getFirst();
            return BotResponse.of(Reply.send(
                    Texts.withdrawOffer(lang, lead.getId(), lead.getProjectType()),
                    List.of(List.of(
                            callback(Texts.get(lang, "button.withdraw_yes"), Callbacks.WITHDRAW + lead.getId()),
                            callback(Texts.get(lang, "button.withdraw_no"), Callbacks.WITHDRAW_KEEP)))));
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Lead lead : active) {
            String label = Texts.get(lang, "button.withdraw_lead", lead.getId(), Texts.type(lang, lead.getProjectType()));
            rows.add(List.of(callback(label, Callbacks.WITHDRAW + lead.getId())));
        }
        rows.add(List.of(callback(Texts.get(lang, "button.withdraw_none"), Callbacks.WITHDRAW_KEEP)));
        return BotResponse.of(Reply.send(Texts.get(lang, "withdraw.choose"), rows));
    }

    public BotResponse onText(Client client, String text) {
        Optional<Draft> found = drafts.find(client.userId());
        if (found.isEmpty()) {
            return welcome(client.language());
        }
        Draft draft = found.get();
        return switch (draft.getStep()) {
            case DESCRIPTION -> acceptDescription(draft, client, text.strip());
            // без username кнопки «Пишите в Telegram» нет, поэтому контакт приходит текстом прямо на шаге CONTACT
            case CONTACT, CONTACT_TEXT -> acceptContact(draft, client, text.strip());
            default -> BotResponse.of(Reply.send(Texts.get(client.language(), "use_buttons")));
        };
    }

    public BotResponse onButton(Client client, String data) {
        Language lang = client.language();
        switch (data) {
            case Callbacks.START -> {
                return begin(client, Texts.get(lang, "welcome"));
            }
            case Callbacks.RESTART -> {
                return begin(client, Texts.get(lang, "restarted"));
            }
            case Callbacks.RESUME -> {
                return resume(client);
            }
            default -> {
            }
        }
        Language chosen = Callbacks.parse(data, Callbacks.LANGUAGE, Language.class);
        if (chosen != null) {
            languages.save(client.userId(), chosen);
            return BotResponse.of(Reply.replace(Texts.get(chosen, "welcome"), welcomeKeyboard(chosen)));
        }
        // отзыв относится к отправленной заявке, черновик для него не нужен
        if (data.startsWith(Callbacks.WITHDRAW)) {
            return onWithdrawButton(client, data);
        }
        Optional<Draft> found = drafts.find(client.userId());
        if (found.isEmpty()) {
            return BotResponse.notice(Texts.get(lang, "stale_button"));
        }
        Draft draft = found.get();
        BotResponse response = switch (draft.getStep()) {
            case TYPE -> onTypeButton(draft, client, data);
            case ASSETS -> onAssetsButton(draft, client, data);
            case BUDGET -> onBudgetButton(draft, client, data);
            case DEADLINE -> onDeadlineButton(draft, client, data);
            case CONTACT -> onContactButton(draft, client, data);
            case CONFIRM -> onConfirmButton(draft, client, data);
            case DESCRIPTION, CONTACT_TEXT -> null;
        };
        // кнопка от прошлого шага или старого сообщения — ничего не меняем
        return response != null ? response : BotResponse.notice(Texts.get(lang, "stale_button"));
    }

    private BotResponse begin(Client client, String previousMessageText) {
        Draft draft = new Draft(client.userId());
        drafts.save(draft);
        return BotResponse.of(Reply.replace(previousMessageText), question(draft, client));
    }

    private BotResponse resume(Client client) {
        Language lang = client.language();
        return drafts.find(client.userId())
                .map(draft -> BotResponse.of(Reply.replace(Texts.get(lang, "resumed")), question(draft, client)))
                .orElseGet(() -> begin(client, Texts.get(lang, "restarted")));
    }

    private BotResponse onWithdrawButton(Client client, String data) {
        Language lang = client.language();
        if (data.equals(Callbacks.WITHDRAW_KEEP)) {
            return BotResponse.of(Reply.replace(Texts.get(lang, "withdraw.kept")));
        }
        long leadId;
        try {
            leadId = Long.parseLong(data.substring(Callbacks.WITHDRAW.length()));
        } catch (NumberFormatException e) {
            return BotResponse.notice(Texts.get(lang, "stale_button"));
        }
        return leads.withdraw(client.userId(), leadId)
                .map(lead -> BotResponse.of(Reply.replace(Texts.withdrawn(lang, lead.getId()))))
                .orElseGet(() -> BotResponse.of(Reply.replace(Texts.get(lang, "withdraw.impossible"))));
    }

    private BotResponse onTypeButton(Draft draft, Client client, String data) {
        ProjectType type = Callbacks.parse(data, Callbacks.TYPE, ProjectType.class);
        if (type == null) {
            return null;
        }
        draft.setType(type);
        Language lang = client.language();
        return advance(draft, client, Step.DESCRIPTION, Texts.get(lang, "ask.type"), Texts.type(lang, type));
    }

    private BotResponse onAssetsButton(Draft draft, Client client, String data) {
        Language lang = client.language();
        if (data.equals(Callbacks.ASSETS_DONE)) {
            if (draft.getAssets().isEmpty()) {
                return BotResponse.notice(Texts.get(lang, "pick_asset"));
            }
            return advance(draft, client, Step.BUDGET, Texts.get(lang, "ask.assets"),
                    Texts.assetsLabel(lang, draft.getAssets()));
        }
        Asset asset = Callbacks.parse(data, Callbacks.ASSET, Asset.class);
        if (asset == null) {
            return null;
        }
        draft.toggleAsset(asset);
        drafts.save(draft);
        return BotResponse.of(Reply.replace(Texts.get(lang, "ask.assets"), assetsKeyboard(draft, lang)));
    }

    private BotResponse onBudgetButton(Draft draft, Client client, String data) {
        if (!data.startsWith(Callbacks.BUDGET)) {
            return null;
        }
        int index;
        try {
            index = Integer.parseInt(data.substring(Callbacks.BUDGET.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        List<String> budgets = props.budgets(client.language());
        if (index < 0 || index >= budgets.size()) {
            return null;
        }
        String budget = budgets.get(index);
        draft.setBudget(budget);
        return advance(draft, client, Step.DEADLINE, Texts.get(client.language(), "ask.budget"), budget);
    }

    private BotResponse onDeadlineButton(Draft draft, Client client, String data) {
        Deadline deadline = Callbacks.parse(data, Callbacks.DEADLINE, Deadline.class);
        if (deadline == null) {
            return null;
        }
        draft.setDeadline(deadline);
        Language lang = client.language();
        return advance(draft, client, Step.CONTACT, Texts.get(lang, "ask.deadline"), Texts.deadline(lang, deadline));
    }

    private BotResponse onContactButton(Draft draft, Client client, String data) {
        Language lang = client.language();
        if (data.equals(Callbacks.CONTACT_TELEGRAM) && client.hasUsername()) {
            String contact = "@" + client.username();
            draft.setContact(contact);
            return advance(draft, client, Step.CONFIRM, Texts.get(lang, "ask.contact"), contact);
        }
        if (data.equals(Callbacks.CONTACT_OTHER)) {
            // режим редактирования не сбрасываем: после ввода контакта вернёмся к карточке
            draft.setStep(Step.CONTACT_TEXT);
            drafts.save(draft);
            return BotResponse.of(
                    Reply.replace(Texts.answered(Texts.get(lang, "ask.contact"), Texts.get(lang, "button.contact_other"))),
                    question(draft, client));
        }
        return null;
    }

    private BotResponse onConfirmButton(Draft draft, Client client, String data) {
        Language lang = client.language();
        switch (data) {
            case Callbacks.SEND -> {
                return submit(draft, client);
            }
            case Callbacks.EDIT -> {
                return BotResponse.of(Reply.replace(summary(draft, lang), editKeyboard(lang)));
            }
            case Callbacks.BACK -> {
                return BotResponse.of(Reply.replace(summary(draft, lang), confirmKeyboard(lang)));
            }
            case Callbacks.CANCEL -> {
                drafts.delete(client.userId());
                return BotResponse.of(Reply.replace(Texts.get(lang, "cancelled")));
            }
            default -> {
            }
        }
        Step field = Callbacks.parse(data, Callbacks.EDIT_FIELD, Step.class);
        if (field == null || !field.isEditable()) {
            return null;
        }
        draft.setEditing(true);
        draft.setStep(field);
        drafts.save(draft);
        return BotResponse.of(Reply.replace(Texts.summary(draft, lang, null)), question(draft, client));
    }

    private BotResponse submit(Draft draft, Client client) {
        Language lang = client.language();
        try {
            Lead lead = leads.submit(toRequest(draft, client));
            drafts.delete(client.userId());
            return BotResponse.of(
                    Reply.replace(Texts.summary(draft, lang, null) + "\n\n" + Texts.get(lang, "sent_mark")),
                    Reply.send(Texts.submitted(lang, lead.getId()),
                            List.of(List.of(link(Texts.get(lang, "button.write_now"), props.humanContactUrl())))));
        } catch (LeadLimitExceededException e) {
            return BotResponse.of(Reply.send(Texts.get(lang, "limit_reached"),
                    List.of(List.of(link(Texts.get(lang, "button.write_telegram"), props.humanContactUrl())))));
        }
    }

    private BotResponse acceptDescription(Draft draft, Client client, String text) {
        if (text.length() < DESCRIPTION_MIN) {
            return BotResponse.of(Reply.send(Texts.get(client.language(), "description.too_short")));
        }
        if (text.length() > DESCRIPTION_MAX) {
            return BotResponse.of(Reply.send(Texts.get(client.language(), "description.too_long")));
        }
        draft.setDescription(text);
        return advance(draft, client, Step.ASSETS, null, null);
    }

    private BotResponse acceptContact(Draft draft, Client client, String text) {
        if (text.length() < CONTACT_MIN || text.length() > CONTACT_MAX) {
            return BotResponse.of(Reply.send(Texts.get(client.language(), "contact.invalid")));
        }
        draft.setContact(text);
        return advance(draft, client, Step.CONFIRM, null, null);
    }

    /**
     * Переход к следующему вопросу. Если ответ меняли с карточки — сразу обратно к карточке.
     *
     * @param askedQuestion вопрос, на который ответили кнопкой: его сообщение заменяется на «вопрос → ответ»;
     *                      {@code null}, если ответили текстом
     */
    private BotResponse advance(Draft draft, Client client, Step next, String askedQuestion, String answer) {
        if (draft.isEditing()) {
            draft.setEditing(false);
            draft.setStep(Step.CONFIRM);
        } else {
            draft.setStep(next);
        }
        drafts.save(draft);
        Reply nextQuestion = question(draft, client);
        if (askedQuestion == null) {
            return BotResponse.of(nextQuestion);
        }
        return BotResponse.of(Reply.replace(Texts.answered(askedQuestion, answer)), nextQuestion);
    }

    private Reply question(Draft draft, Client client) {
        Language lang = client.language();
        return switch (draft.getStep()) {
            case TYPE -> Reply.send(Texts.get(lang, "ask.type"), typeKeyboard(lang));
            case DESCRIPTION -> Reply.send(Texts.askDescription(lang, draft.getType()));
            case ASSETS -> Reply.send(Texts.get(lang, "ask.assets"), assetsKeyboard(draft, lang));
            case BUDGET -> Reply.send(Texts.get(lang, "ask.budget"), budgetKeyboard(lang));
            case DEADLINE -> Reply.send(Texts.get(lang, "ask.deadline"), deadlineKeyboard(lang));
            case CONTACT -> client.hasUsername()
                    ? Reply.send(Texts.get(lang, "ask.contact"), contactKeyboard(client))
                    : Reply.send(Texts.get(lang, "ask.contact.no_username"));
            case CONTACT_TEXT -> Reply.send(Texts.get(lang, "ask.contact.text"));
            case CONFIRM -> Reply.send(summary(draft, lang), confirmKeyboard(lang));
        };
    }

    private String summary(Draft draft, Language lang) {
        return Texts.summary(draft, lang, props.privacyUrl());
    }

    private static LeadRequest toRequest(Draft draft, Client client) {
        return new LeadRequest(client.userId(), client.username(), client.firstName(),
                draft.getType(), draft.getDescription(), draft.getAssets(),
                draft.getBudget(), draft.getDeadline(), draft.getContact(), client.language());
    }

    private BotResponse welcome(Language lang) {
        return BotResponse.of(Reply.send(Texts.get(lang, "welcome"), welcomeKeyboard(lang)));
    }

    private List<List<Button>> welcomeKeyboard(Language lang) {
        List<List<Button>> rows = new ArrayList<>();
        rows.add(List.of(callback(Texts.get(lang, "button.start"), Callbacks.START)));
        rows.add(List.of(link(Texts.get(lang, "button.human"), props.humanContactUrl())));
        for (Language other : Language.values()) {
            if (other != lang) {
                rows.add(List.of(callback(Texts.get(other, "language.switch"), Callbacks.LANGUAGE + other.name())));
            }
        }
        return rows;
    }

    private static List<List<Button>> typeKeyboard(Language lang) {
        // длинные названия — отдельной строкой, иначе на телефоне они обрезаются
        return List.of(
                List.of(typeButton(lang, ProjectType.SITE), typeButton(lang, ProjectType.BOT)),
                List.of(typeButton(lang, ProjectType.WEB_SERVICE)),
                List.of(typeButton(lang, ProjectType.MOBILE)),
                List.of(typeButton(lang, ProjectType.GAME), typeButton(lang, ProjectType.BACKEND)),
                List.of(typeButton(lang, ProjectType.OTHER)));
    }

    private static Button typeButton(Language lang, ProjectType type) {
        return callback(Texts.type(lang, type), Callbacks.TYPE + type.name());
    }

    private static List<List<Button>> assetsKeyboard(Draft draft, Language lang) {
        List<List<Button>> rows = new ArrayList<>();
        for (Asset asset : Asset.values()) {
            String mark = draft.getAssets().contains(asset) ? "✅ " : "";
            rows.add(List.of(callback(mark + Texts.asset(lang, asset), Callbacks.ASSET + asset.name())));
        }
        rows.add(List.of(callback(Texts.get(lang, "button.assets_done"), Callbacks.ASSETS_DONE)));
        return rows;
    }

    private List<List<Button>> budgetKeyboard(Language lang) {
        List<String> budgets = props.budgets(lang);
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < budgets.size(); i++) {
            buttons.add(callback(budgets.get(i), Callbacks.BUDGET + i));
        }
        return inPairs(buttons);
    }

    private static List<List<Button>> deadlineKeyboard(Language lang) {
        List<Button> buttons = new ArrayList<>();
        for (Deadline deadline : Deadline.values()) {
            buttons.add(callback(Texts.deadline(lang, deadline), Callbacks.DEADLINE + deadline.name()));
        }
        return inPairs(buttons);
    }

    private static List<List<Button>> contactKeyboard(Client client) {
        Language lang = client.language();
        return List.of(
                List.of(callback(Texts.get(lang, "button.contact_telegram", client.username()),
                        Callbacks.CONTACT_TELEGRAM)),
                List.of(callback(Texts.get(lang, "button.contact_other"), Callbacks.CONTACT_OTHER)));
    }

    private static List<List<Button>> confirmKeyboard(Language lang) {
        return List.of(
                List.of(callback(Texts.get(lang, "button.send"), Callbacks.SEND)),
                List.of(callback(Texts.get(lang, "button.edit"), Callbacks.EDIT),
                        callback(Texts.get(lang, "button.cancel"), Callbacks.CANCEL)));
    }

    private static List<List<Button>> editKeyboard(Language lang) {
        List<Button> buttons = new ArrayList<>();
        for (Step step : Step.values()) {
            if (step.isEditable()) {
                buttons.add(callback(Texts.field(lang, step), Callbacks.EDIT_FIELD + step.name()));
            }
        }
        List<List<Button>> rows = new ArrayList<>(inPairs(buttons));
        rows.add(List.of(callback(Texts.get(lang, "button.back"), Callbacks.BACK)));
        return rows;
    }

    private static List<List<Button>> inPairs(List<Button> buttons) {
        List<List<Button>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 2) {
            rows.add(buttons.subList(i, Math.min(i + 2, buttons.size())));
        }
        return rows;
    }
}
