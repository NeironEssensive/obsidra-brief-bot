package dev.obsidra.briefbot.dialog;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.obsidra.briefbot.i18n.Language;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.ProjectType;
import dev.obsidra.briefbot.message.Html;

/** Все тексты диалога в одном месте. Разметка — HTML Telegram. */
public final class Texts {

    static final String BUNDLE = "messages";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final Map<Language, ResourceBundle> BUNDLES = new EnumMap<>(Language.class);

    static {
        for (Language language : Language.values()) {
            BUNDLES.put(language, bundle(language));
        }
    }

    private Texts() {
    }

    static ResourceBundle bundle(Language language) {
        return ResourceBundle.getBundle(BUNDLE, language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    public static String get(Language language, String key, Object... args) {
        String text = BUNDLES.get(language).getString(key);
        if (args.length == 0) {
            return text;
        }
        return PLACEHOLDER.matcher(text)
                .replaceAll(match -> Matcher.quoteReplacement(String.valueOf(args[Integer.parseInt(match.group(1))])));
    }

    public static String type(Language language, ProjectType type) {
        return get(language, "type." + type.name());
    }

    public static String asset(Language language, Asset asset) {
        return get(language, "asset." + asset.name());
    }

    public static String deadline(Language language, Deadline deadline) {
        return get(language, "deadline." + deadline.name());
    }

    public static String field(Language language, Step step) {
        return get(language, "field." + step.name());
    }

    public static String askDescription(Language language, ProjectType type) {
        ProjectType hint = type == null ? ProjectType.OTHER : type;
        return get(language, "ask.description", get(language, "type." + hint.name() + ".example"));
    }

    /** Вопрос с выбранным ответом — им заменяется сообщение с кнопками, чтобы в чате осталась история. */
    public static String answered(String question, String answer) {
        return question + "\n\n→ <b>" + Html.escape(answer) + "</b>";
    }

    public static String assetsLabel(Language language, Set<Asset> assets) {
        return Arrays.stream(Asset.values())
                .filter(assets::contains)
                .map(asset -> asset(language, asset))
                .collect(Collectors.joining(", "));
    }

    /**
     * Карточка заявки для проверки перед отправкой.
     *
     * @param privacyUrl ссылка на политику обработки данных; {@code null} — без строки о согласии
     */
    public static String summary(Draft draft, Language language, String privacyUrl) {
        StringBuilder text = new StringBuilder(get(language, "summary.title")).append("\n\n");
        line(text, language, Step.TYPE, draft.getType() == null ? null : type(language, draft.getType()));
        line(text, language, Step.DESCRIPTION, draft.getDescription());
        line(text, language, Step.ASSETS,
                draft.getAssets().isEmpty() ? null : assetsLabel(language, draft.getAssets()));
        line(text, language, Step.BUDGET, draft.getBudget());
        line(text, language, Step.DEADLINE,
                draft.getDeadline() == null ? null : deadline(language, draft.getDeadline()));
        line(text, language, Step.CONTACT, draft.getContact());
        if (privacyUrl != null) {
            text.append('\n').append(get(language, "summary.privacy", privacyUrl));
        }
        return text.toString().stripTrailing();
    }

    public static String submitted(Language language, long leadId) {
        return get(language, "submitted", leadId);
    }

    public static String withdrawOffer(Language language, long leadId, ProjectType type) {
        return get(language, "withdraw.offer", leadId, type(language, type));
    }

    public static String withdrawn(Language language, long leadId) {
        return get(language, "withdrawn", leadId);
    }

    private static void line(StringBuilder text, Language language, Step field, String value) {
        text.append("<b>").append(field(language, field)).append(":</b> ")
                .append(value == null ? "—" : Html.escape(value))
                .append('\n');
    }
}
