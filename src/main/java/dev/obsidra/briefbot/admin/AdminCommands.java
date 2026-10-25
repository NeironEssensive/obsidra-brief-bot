package dev.obsidra.briefbot.admin;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.dialog.Texts;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.LeadService;
import dev.obsidra.briefbot.lead.LeadStats;
import dev.obsidra.briefbot.lead.LeadStatus;
import dev.obsidra.briefbot.message.BotResponse;
import dev.obsidra.briefbot.message.Reply;
import dev.obsidra.briefbot.notify.LeadCards;

/** Команды владельца: /leads, /stats и кнопки статуса под карточкой заявки. */
@Component
public class AdminCommands {

    private static final int LATEST_LIMIT = 10;
    private static final Duration STATS_PERIOD = Duration.ofDays(7);

    private final BotProperties props;
    private final LeadService leads;
    private final LeadCards cards;

    public AdminCommands(BotProperties props, LeadService leads, LeadCards cards) {
        this.props = props;
        this.leads = leads;
        this.cards = cards;
    }

    public boolean isAdmin(long userId) {
        return props.hasAdmin() && props.adminChatId() == userId;
    }

    public BotResponse latest() {
        List<Lead> latest = leads.latest(LATEST_LIMIT);
        if (latest.isEmpty()) {
            return BotResponse.of(Reply.send("Заявок пока нет."));
        }
        String lines = latest.stream().map(cards::shortLine).collect(Collectors.joining("\n"));
        return BotResponse.of(Reply.send("<b>Последние заявки</b>\n\n" + lines));
    }

    public BotResponse stats() {
        LeadStats stats = leads.statsFor(STATS_PERIOD);
        if (stats.total() == 0) {
            return BotResponse.of(Reply.send("За 7 дней заявок не было."));
        }
        String byType = stats.byType().entrySet().stream()
                .map(e -> "• " + e.getKey().label() + " — " + e.getValue())
                .collect(Collectors.joining("\n"));
        String byStatus = stats.byStatus().entrySet().stream()
                .map(e -> "• " + e.getKey().label() + " — " + e.getValue())
                .collect(Collectors.joining("\n"));
        return BotResponse.of(Reply.send("""
                <b>Заявки за 7 дней: %d</b>

                <b>По типам</b>
                %s

                <b>По статусам</b>
                %s""".formatted(stats.total(), byType, byStatus)));
    }

    /** Нажатие «Взял в работу» / «Отклонить»: данные вида {@code lead:<id>:<STATUS>}. */
    public BotResponse onStatusButton(String data) {
        String[] parts = data.split(":");
        if (parts.length != 3) {
            return BotResponse.notice(Texts.STALE_BUTTON);
        }
        long leadId;
        LeadStatus status;
        try {
            leadId = Long.parseLong(parts[1]);
            status = LeadStatus.valueOf(parts[2]);
        } catch (IllegalArgumentException e) {
            return BotResponse.notice(Texts.STALE_BUTTON);
        }
        return leads.changeStatus(leadId, status)
                .map(lead -> new BotResponse(List.of(Reply.replace(cards.cardWithStatus(lead))),
                        lead.getStatus() == status
                                ? "Статус: " + status.label()
                                : "Статус не изменён: заявка уже " + lead.getStatus().label()))
                .orElseGet(() -> BotResponse.notice("Заявка №" + leadId + " не найдена"));
    }

    /** /lead 1001 — карточка любой заявки, с кнопками статуса, если она ещё новая. */
    public BotResponse lead(String argument) {
        long leadId;
        try {
            leadId = Long.parseLong(argument.strip());
        } catch (NumberFormatException e) {
            return BotResponse.of(Reply.send("Укажите номер заявки: /lead 1001"));
        }
        return leads.find(leadId)
                .map(lead -> BotResponse.of(Reply.send(cards.cardWithStatus(lead), cards.statusKeyboard(lead))))
                .orElseGet(() -> BotResponse.of(Reply.send("Заявка №" + leadId + " не найдена.")));
    }
}
