package dev.obsidra.briefbot.dialog;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.ProjectType;
import dev.obsidra.briefbot.message.Html;

/** Все тексты диалога в одном месте. Разметка — HTML Telegram. */
public final class Texts {

    public static final String WELCOME = """
            Привет! Я бот <b>Obsidra</b> 👋

            Задам 6 коротких вопросов о вашем проекте — это займёт около минуты. \
            Ответим в течение рабочего дня.""";
    public static final String RESUME_OFFER = "У вас есть незаконченная заявка. Продолжим с того места, где остановились?";
    public static final String RESUMED = "Продолжаем 👇";
    public static final String RESTARTED = "Начинаем заново 👇";

    public static final String ASK_TYPE = "<b>1/6.</b> Что нужно сделать?";
    public static final String ASK_ASSETS = "<b>3/6.</b> Что уже есть? Можно выбрать несколько вариантов.";
    public static final String ASK_BUDGET = "<b>4/6.</b> Какой бюджет?";
    public static final String ASK_DEADLINE = "<b>5/6.</b> Когда нужно?";
    public static final String ASK_CONTACT = "<b>6/6.</b> Как с вами связаться?";
    public static final String ASK_CONTACT_TEXT =
            "<b>6/6.</b> Напишите, как с вами связаться: почта, телефон или другой мессенджер.";
    public static final String ASK_CONTACT_NO_USERNAME = """
            <b>6/6.</b> Как с вами связаться?

            У вас в Telegram не указан username, поэтому напишите контакт сообщением: \
            почта, телефон или другой мессенджер.""";

    public static final String DESCRIPTION_TOO_SHORT = "Слишком коротко — опишите задачу хотя бы одним предложением.";
    public static final String DESCRIPTION_TOO_LONG =
            "Получилось длинно: уложитесь, пожалуйста, в 1000 символов. Подробности обсудим лично.";
    public static final String CONTACT_INVALID = "Не похоже на контакт — напишите почту, телефон или ник в мессенджере.";
    public static final String USE_BUTTONS = "Выберите вариант кнопкой под вопросом 👆";
    public static final String STALE_BUTTON = "Эта кнопка уже неактуальна";
    public static final String PICK_ASSET = "Выберите хотя бы один вариант";
    public static final String UNKNOWN_COMMAND = "Не знаю такой команды. Чтобы оставить заявку, нажмите /start.";

    public static final String CANCELLED = "Заявка отменена. Если передумаете — нажмите /start.";
    public static final String SENT_MARK = "✅ <b>Отправлено</b>";
    public static final String LIMIT_REACHED = """
            Вы уже отправили несколько заявок за сутки. \
            Чтобы обсудить ещё один проект, напишите нам напрямую — ответим лично.""";

    private Texts() {
    }

    public static String askDescription(ProjectType type) {
        String example = (type == null ? ProjectType.OTHER : type).example();
        return "<b>2/6.</b> Опишите задачу в паре предложений.\n\n<i>" + example + "</i>";
    }

    /** Вопрос с выбранным ответом — им заменяется сообщение с кнопками, чтобы в чате осталась история. */
    public static String answered(String question, String answer) {
        return question + "\n\n→ <b>" + Html.escape(answer) + "</b>";
    }

    public static String assetsLabel(Set<Asset> assets) {
        return Arrays.stream(Asset.values())
                .filter(assets::contains)
                .map(Asset::label)
                .collect(Collectors.joining(", "));
    }

    /**
     * Карточка заявки для проверки перед отправкой.
     *
     * @param privacyUrl ссылка на политику обработки данных; {@code null} — без строки о согласии
     */
    public static String summary(Draft draft, String privacyUrl) {
        StringBuilder text = new StringBuilder("<b>Проверьте заявку</b>\n\n");
        line(text, Step.TYPE, draft.getType() == null ? null : draft.getType().label());
        line(text, Step.DESCRIPTION, draft.getDescription());
        line(text, Step.ASSETS, draft.getAssets().isEmpty() ? null : assetsLabel(draft.getAssets()));
        line(text, Step.BUDGET, draft.getBudget());
        line(text, Step.DEADLINE, draft.getDeadline() == null ? null : draft.getDeadline().label());
        line(text, Step.CONTACT, draft.getContact());
        if (privacyUrl != null) {
            text.append("\n<i>Нажимая «Отправить», вы соглашаетесь с <a href=\"")
                    .append(privacyUrl)
                    .append("\">политикой обработки персональных данных</a>.</i>");
        }
        return text.toString().stripTrailing();
    }

    public static String submitted(long leadId) {
        return "✅ Заявка №" + leadId + " принята! Ответим в течение рабочего дня.";
    }

    private static void line(StringBuilder text, Step field, String value) {
        text.append("<b>").append(field.label()).append(":</b> ")
                .append(value == null ? "—" : Html.escape(value))
                .append('\n');
    }
}
