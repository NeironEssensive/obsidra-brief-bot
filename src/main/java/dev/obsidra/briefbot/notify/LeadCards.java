package dev.obsidra.briefbot.notify;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.dialog.Texts;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.LeadStatus;
import dev.obsidra.briefbot.message.Button;
import dev.obsidra.briefbot.message.Html;

/** Как заявка выглядит для владельца: полная карточка и строка в списке /leads. */
@Component
public class LeadCards {

    /** Кнопки статуса под карточкой: {@code lead:<id>:<STATUS>}. */
    public static final String STATUS_CALLBACK_PREFIX = "lead:";

    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final BotProperties props;

    public LeadCards(BotProperties props) {
        this.props = props;
    }

    /** Карточка новой заявки — так она приходит владельцу. */
    public String card(Lead lead) {
        return body(lead, "🆕 <b>Заявка №" + lead.getId() + "</b>");
    }

    /** Карточка с текущим статусом: после нажатия кнопки статуса и по команде /lead. */
    public String cardWithStatus(Lead lead) {
        return body(lead, "<b>Заявка №" + lead.getId() + "</b>") + "\n\n<b>Статус:</b> " + lead.getStatus().label();
    }

    public String withdrawn(Lead lead) {
        return "↩️ <b>Клиент отозвал заявку</b>\n" + shortLine(lead)
                + "\n\nПодробности: /lead " + lead.getId();
    }

    private String body(Lead lead, String header) {
        String client = "<a href=\"tg://user?id=" + lead.getTgUserId() + "\">"
                + Html.escape(lead.getClientName() == null ? "Клиент" : lead.getClientName()) + "</a>"
                + (lead.getTgUsername() == null ? "" : " (@" + lead.getTgUsername() + ")");
        return """
                %s

                <b>Что сделать:</b> %s
                <b>Задача:</b> %s
                <b>Что есть:</b> %s
                <b>Бюджет:</b> %s
                <b>Сроки:</b> %s
                <b>Контакт:</b> %s
                <b>Клиент:</b> %s

                <i>%s</i>""".formatted(
                header,
                lead.getProjectType().label(),
                Html.escape(lead.getDescription()),
                Texts.assetsLabel(lead.getAssets()),
                Html.escape(lead.getBudget()),
                lead.getDeadline().label(),
                Html.escape(lead.getContact()),
                client,
                FULL_TIME.format(lead.getCreatedAt().atZone(props.zone())));
    }

    public List<List<Button>> statusKeyboard(Lead lead) {
        if (lead.getStatus() != LeadStatus.NEW) {
            return List.of();
        }
        return List.of(List.of(
                Button.callback("✅ Взял в работу", statusCallback(lead, LeadStatus.IN_PROGRESS)),
                Button.callback("✖️ Отклонить", statusCallback(lead, LeadStatus.REJECTED))));
    }

    public String shortLine(Lead lead) {
        return "№%d · %s · %s · %s · %s".formatted(
                lead.getId(),
                lead.getProjectType().label(),
                Html.escape(lead.getBudget()),
                lead.getStatus().label(),
                SHORT_TIME.format(lead.getCreatedAt().atZone(props.zone())));
    }

    private static String statusCallback(Lead lead, LeadStatus status) {
        return STATUS_CALLBACK_PREFIX + lead.getId() + ":" + status.name();
    }
}
