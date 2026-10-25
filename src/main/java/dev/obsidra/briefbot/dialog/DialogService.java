package dev.obsidra.briefbot.dialog;

import static dev.obsidra.briefbot.message.Button.callback;
import static dev.obsidra.briefbot.message.Button.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.obsidra.briefbot.config.BotProperties;
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
    private final LeadService leads;
    private final BotProperties props;

    public DialogService(DraftStore drafts, LeadService leads, BotProperties props) {
        this.drafts = drafts;
        this.leads = leads;
        this.props = props;
    }

    /** /start: приветствие или предложение продолжить незаконченную заявку. */
    public BotResponse start(Client client) {
        if (drafts.find(client.userId()).isPresent()) {
            return BotResponse.of(Reply.send(Texts.RESUME_OFFER, List.of(List.of(
                    callback("Продолжить", Callbacks.RESUME),
                    callback("Начать заново", Callbacks.RESTART)))));
        }
        return welcome();
    }

    /**
     * /cancel: незаконченную заявку удаляет сразу, а уже отправленную предлагает отозвать —
     * так клиент не получит «Заявка отменена», когда отменять было нечего.
     */
    public BotResponse cancel(Client client) {
        if (drafts.find(client.userId()).isPresent()) {
            drafts.delete(client.userId());
            return BotResponse.of(Reply.send(Texts.CANCELLED));
        }
        List<Lead> active = leads.activeLeadsOf(client.userId());
        if (active.isEmpty()) {
            return BotResponse.of(Reply.send(Texts.NOTHING_TO_CANCEL));
        }
        if (active.size() == 1) {
            Lead lead = active.getFirst();
            return BotResponse.of(Reply.send(
                    Texts.withdrawOffer(lead.getId(), lead.getProjectType()),
                    List.of(List.of(
                            callback("Да, отозвать", Callbacks.WITHDRAW + lead.getId()),
                            callback("Нет", Callbacks.WITHDRAW_KEEP)))));
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Lead lead : active) {
            rows.add(List.of(callback("Отозвать №" + lead.getId() + " · " + lead.getProjectType().label(),
                    Callbacks.WITHDRAW + lead.getId())));
        }
        rows.add(List.of(callback("Ничего не отзывать", Callbacks.WITHDRAW_KEEP)));
        return BotResponse.of(Reply.send(Texts.WITHDRAW_CHOOSE, rows));
    }

    public BotResponse onText(Client client, String text) {
        Optional<Draft> found = drafts.find(client.userId());
        if (found.isEmpty()) {
            return welcome();
        }
        Draft draft = found.get();
        return switch (draft.getStep()) {
            case DESCRIPTION -> acceptDescription(draft, client, text.strip());
            // без username кнопки «Пишите в Telegram» нет, поэтому контакт приходит текстом прямо на шаге CONTACT
            case CONTACT, CONTACT_TEXT -> acceptContact(draft, client, text.strip());
            default -> BotResponse.of(Reply.send(Texts.USE_BUTTONS));
        };
    }

    public BotResponse onButton(Client client, String data) {
        switch (data) {
            case Callbacks.START -> {
                return begin(client, Texts.WELCOME);
            }
            case Callbacks.RESTART -> {
                return begin(client, Texts.RESTARTED);
            }
            case Callbacks.RESUME -> {
                return resume(client);
            }
            default -> {
            }
        }
        // отзыв относится к отправленной заявке, черновик для него не нужен
        if (data.startsWith(Callbacks.WITHDRAW)) {
            return onWithdrawButton(client, data);
        }
        Optional<Draft> found = drafts.find(client.userId());
        if (found.isEmpty()) {
            return BotResponse.notice(Texts.STALE_BUTTON);
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
        return response != null ? response : BotResponse.notice(Texts.STALE_BUTTON);
    }

    private BotResponse begin(Client client, String previousMessageText) {
        Draft draft = new Draft(client.userId());
        drafts.save(draft);
        return BotResponse.of(Reply.replace(previousMessageText), question(draft, client));
    }

    private BotResponse resume(Client client) {
        return drafts.find(client.userId())
                .map(draft -> BotResponse.of(Reply.replace(Texts.RESUMED), question(draft, client)))
                .orElseGet(() -> begin(client, Texts.RESTARTED));
    }

    private BotResponse onWithdrawButton(Client client, String data) {
        if (data.equals(Callbacks.WITHDRAW_KEEP)) {
            return BotResponse.of(Reply.replace(Texts.WITHDRAW_KEPT));
        }
        long leadId;
        try {
            leadId = Long.parseLong(data.substring(Callbacks.WITHDRAW.length()));
        } catch (NumberFormatException e) {
            return BotResponse.notice(Texts.STALE_BUTTON);
        }
        return leads.withdraw(client.userId(), leadId)
                .map(lead -> BotResponse.of(Reply.replace(Texts.withdrawn(lead.getId()))))
                .orElseGet(() -> BotResponse.of(Reply.replace(Texts.WITHDRAW_IMPOSSIBLE)));
    }

    private BotResponse onTypeButton(Draft draft, Client client, String data) {
        ProjectType type = Callbacks.parse(data, Callbacks.TYPE, ProjectType.class);
        if (type == null) {
            return null;
        }
        draft.setType(type);
        return advance(draft, client, Step.DESCRIPTION, Texts.ASK_TYPE, type.label());
    }

    private BotResponse onAssetsButton(Draft draft, Client client, String data) {
        if (data.equals(Callbacks.ASSETS_DONE)) {
            if (draft.getAssets().isEmpty()) {
                return BotResponse.notice(Texts.PICK_ASSET);
            }
            return advance(draft, client, Step.BUDGET, Texts.ASK_ASSETS, Texts.assetsLabel(draft.getAssets()));
        }
        Asset asset = Callbacks.parse(data, Callbacks.ASSET, Asset.class);
        if (asset == null) {
            return null;
        }
        draft.toggleAsset(asset);
        drafts.save(draft);
        return BotResponse.of(Reply.replace(Texts.ASK_ASSETS, assetsKeyboard(draft)));
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
        if (index < 0 || index >= props.budgets().size()) {
            return null;
        }
        String budget = props.budgets().get(index);
        draft.setBudget(budget);
        return advance(draft, client, Step.DEADLINE, Texts.ASK_BUDGET, budget);
    }

    private BotResponse onDeadlineButton(Draft draft, Client client, String data) {
        Deadline deadline = Callbacks.parse(data, Callbacks.DEADLINE, Deadline.class);
        if (deadline == null) {
            return null;
        }
        draft.setDeadline(deadline);
        return advance(draft, client, Step.CONTACT, Texts.ASK_DEADLINE, deadline.label());
    }

    private BotResponse onContactButton(Draft draft, Client client, String data) {
        if (data.equals(Callbacks.CONTACT_TELEGRAM) && client.hasUsername()) {
            String contact = "@" + client.username();
            draft.setContact(contact);
            return advance(draft, client, Step.CONFIRM, Texts.ASK_CONTACT, contact);
        }
        if (data.equals(Callbacks.CONTACT_OTHER)) {
            // режим редактирования не сбрасываем: после ввода контакта вернёмся к карточке
            draft.setStep(Step.CONTACT_TEXT);
            drafts.save(draft);
            return BotResponse.of(
                    Reply.replace(Texts.answered(Texts.ASK_CONTACT, "Другой способ")),
                    question(draft, client));
        }
        return null;
    }

    private BotResponse onConfirmButton(Draft draft, Client client, String data) {
        switch (data) {
            case Callbacks.SEND -> {
                return submit(draft, client);
            }
            case Callbacks.EDIT -> {
                return BotResponse.of(Reply.replace(summary(draft), editKeyboard()));
            }
            case Callbacks.BACK -> {
                return BotResponse.of(Reply.replace(summary(draft), confirmKeyboard()));
            }
            case Callbacks.CANCEL -> {
                drafts.delete(client.userId());
                return BotResponse.of(Reply.replace(Texts.CANCELLED));
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
        return BotResponse.of(Reply.replace(Texts.summary(draft, null)), question(draft, client));
    }

    private BotResponse submit(Draft draft, Client client) {
        try {
            Lead lead = leads.submit(toRequest(draft, client));
            drafts.delete(client.userId());
            return BotResponse.of(
                    Reply.replace(Texts.summary(draft, null) + "\n\n" + Texts.SENT_MARK),
                    Reply.send(Texts.submitted(lead.getId()),
                            List.of(List.of(link("Написать сейчас", props.humanContactUrl())))));
        } catch (LeadLimitExceededException e) {
            return BotResponse.of(Reply.send(Texts.LIMIT_REACHED,
                    List.of(List.of(link("Написать в Telegram", props.humanContactUrl())))));
        }
    }

    private BotResponse acceptDescription(Draft draft, Client client, String text) {
        if (text.length() < DESCRIPTION_MIN) {
            return BotResponse.of(Reply.send(Texts.DESCRIPTION_TOO_SHORT));
        }
        if (text.length() > DESCRIPTION_MAX) {
            return BotResponse.of(Reply.send(Texts.DESCRIPTION_TOO_LONG));
        }
        draft.setDescription(text);
        return advance(draft, client, Step.ASSETS, null, null);
    }

    private BotResponse acceptContact(Draft draft, Client client, String text) {
        if (text.length() < CONTACT_MIN || text.length() > CONTACT_MAX) {
            return BotResponse.of(Reply.send(Texts.CONTACT_INVALID));
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
        return switch (draft.getStep()) {
            case TYPE -> Reply.send(Texts.ASK_TYPE, typeKeyboard());
            case DESCRIPTION -> Reply.send(Texts.askDescription(draft.getType()));
            case ASSETS -> Reply.send(Texts.ASK_ASSETS, assetsKeyboard(draft));
            case BUDGET -> Reply.send(Texts.ASK_BUDGET, budgetKeyboard());
            case DEADLINE -> Reply.send(Texts.ASK_DEADLINE, deadlineKeyboard());
            case CONTACT -> client.hasUsername()
                    ? Reply.send(Texts.ASK_CONTACT, contactKeyboard(client))
                    : Reply.send(Texts.ASK_CONTACT_NO_USERNAME);
            case CONTACT_TEXT -> Reply.send(Texts.ASK_CONTACT_TEXT);
            case CONFIRM -> Reply.send(summary(draft), confirmKeyboard());
        };
    }

    private String summary(Draft draft) {
        return Texts.summary(draft, props.privacyUrl());
    }

    private static LeadRequest toRequest(Draft draft, Client client) {
        return new LeadRequest(client.userId(), client.username(), client.firstName(),
                draft.getType(), draft.getDescription(), draft.getAssets(),
                draft.getBudget(), draft.getDeadline(), draft.getContact());
    }

    private BotResponse welcome() {
        return BotResponse.of(Reply.send(Texts.WELCOME, List.of(
                List.of(callback("Начать", Callbacks.START)),
                List.of(link("Написать человеку", props.humanContactUrl())))));
    }

    private static List<List<Button>> typeKeyboard() {
        // длинные названия — отдельной строкой, иначе на телефоне они обрезаются
        return List.of(
                List.of(typeButton(ProjectType.SITE), typeButton(ProjectType.BOT)),
                List.of(typeButton(ProjectType.WEB_SERVICE)),
                List.of(typeButton(ProjectType.MOBILE)),
                List.of(typeButton(ProjectType.GAME), typeButton(ProjectType.BACKEND)),
                List.of(typeButton(ProjectType.OTHER)));
    }

    private static Button typeButton(ProjectType type) {
        return callback(type.label(), Callbacks.TYPE + type.name());
    }

    private static List<List<Button>> assetsKeyboard(Draft draft) {
        List<List<Button>> rows = new ArrayList<>();
        for (Asset asset : Asset.values()) {
            String mark = draft.getAssets().contains(asset) ? "✅ " : "";
            rows.add(List.of(callback(mark + asset.label(), Callbacks.ASSET + asset.name())));
        }
        rows.add(List.of(callback("Готово →", Callbacks.ASSETS_DONE)));
        return rows;
    }

    private List<List<Button>> budgetKeyboard() {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < props.budgets().size(); i++) {
            buttons.add(callback(props.budgets().get(i), Callbacks.BUDGET + i));
        }
        return inPairs(buttons);
    }

    private static List<List<Button>> deadlineKeyboard() {
        List<Button> buttons = new ArrayList<>();
        for (Deadline deadline : Deadline.values()) {
            buttons.add(callback(deadline.label(), Callbacks.DEADLINE + deadline.name()));
        }
        return inPairs(buttons);
    }

    private static List<List<Button>> contactKeyboard(Client client) {
        return List.of(
                List.of(callback("Пишите мне в Telegram (@" + client.username() + ")", Callbacks.CONTACT_TELEGRAM)),
                List.of(callback("Другой способ", Callbacks.CONTACT_OTHER)));
    }

    private static List<List<Button>> confirmKeyboard() {
        return List.of(
                List.of(callback("✅ Отправить", Callbacks.SEND)),
                List.of(callback("✏️ Изменить", Callbacks.EDIT), callback("❌ Отменить", Callbacks.CANCEL)));
    }

    private static List<List<Button>> editKeyboard() {
        List<Button> buttons = new ArrayList<>();
        for (Step step : Step.values()) {
            if (step.isEditable()) {
                buttons.add(callback(step.label(), Callbacks.EDIT_FIELD + step.name()));
            }
        }
        List<List<Button>> rows = new ArrayList<>(inPairs(buttons));
        rows.add(List.of(callback("← Назад", Callbacks.BACK)));
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
