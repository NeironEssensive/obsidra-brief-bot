package dev.obsidra.briefbot.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.LeadSubmittedEvent;
import dev.obsidra.briefbot.lead.LeadWithdrawnEvent;
import dev.obsidra.briefbot.message.Reply;
import dev.obsidra.briefbot.telegram.ReplySender;

/** Присылает владельцу карточку новой заявки — только после того, как она точно сохранилась в базе. */
@Component
public class AdminNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminNotifier.class);

    private final BotProperties props;
    private final LeadCards cards;
    private final ReplySender sender;

    public AdminNotifier(BotProperties props, LeadCards cards, ReplySender sender) {
        this.props = props;
        this.cards = cards;
        this.sender = sender;
    }

    @TransactionalEventListener
    public void onLeadSubmitted(LeadSubmittedEvent event) {
        Lead lead = event.lead();
        if (!props.hasAdmin()) {
            log.warn("bot.admin-chat-id не задан — заявка №{} сохранена, но владельцу не отправлена", lead.getId());
            return;
        }
        boolean delivered = sender.send(props.adminChatId(), Reply.send(cards.card(lead), cards.statusKeyboard(lead)));
        if (!delivered) {
            // заявка уже в базе — владелец увидит её в /leads, но о проблеме нужно знать
            log.warn("Карточка заявки №{} не доставлена владельцу {}: проверьте BOT_ADMIN_CHAT_ID (команда /myid) "
                    + "и что с этого аккаунта в боте нажимали «Старт»", lead.getId(), props.adminChatId());
        }
    }

    @TransactionalEventListener
    public void onLeadWithdrawn(LeadWithdrawnEvent event) {
        if (props.hasAdmin()) {
            sender.send(props.adminChatId(), Reply.send(cards.withdrawn(event.lead())));
        }
    }
}
