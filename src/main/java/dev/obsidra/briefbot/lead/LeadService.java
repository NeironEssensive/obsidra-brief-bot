package dev.obsidra.briefbot.lead;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.obsidra.briefbot.config.BotProperties;

@Service
public class LeadService {

    /** Статусы, в которых клиент ещё может отозвать заявку. */
    private static final Set<LeadStatus> ACTIVE = EnumSet.of(LeadStatus.NEW, LeadStatus.IN_PROGRESS);

    private final LeadRepository repository;
    private final ApplicationEventPublisher events;
    private final BotProperties props;
    private final Clock clock;

    public LeadService(LeadRepository repository, ApplicationEventPublisher events, BotProperties props, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Сохраняет заявку и сообщает о ней владельцу.
     *
     * @throws LeadLimitExceededException если клиент уже отправил {@code bot.daily-limit} заявок за сутки
     */
    @Transactional
    public Lead submit(LeadRequest request) {
        Instant now = clock.instant();
        long recent = repository.countByTgUserIdAndCreatedAtAfter(request.tgUserId(), now.minus(Duration.ofDays(1)));
        if (recent >= props.dailyLimit()) {
            throw new LeadLimitExceededException(request.tgUserId());
        }
        Lead lead = repository.save(Lead.create(request, now));
        events.publishEvent(new LeadSubmittedEvent(lead));
        return lead;
    }

    /**
     * Владелец разбирает новую заявку. Решение по уже разобранной или отозванной заявке не перезаписывается:
     * под старой карточкой могли остаться кнопки.
     *
     * @return заявка с её актуальным статусом (он мог и не измениться)
     */
    @Transactional
    public Optional<Lead> changeStatus(long leadId, LeadStatus status) {
        return repository.findById(leadId).map(lead -> {
            if (lead.getStatus() == LeadStatus.NEW) {
                lead.changeStatus(status, clock.instant());
            }
            return lead;
        });
    }

    /** Заявки клиента, которые ещё можно отозвать, от свежих к старым. */
    @Transactional(readOnly = true)
    public List<Lead> activeLeadsOf(long tgUserId) {
        return repository.findByTgUserIdAndStatusInOrderByCreatedAtDesc(tgUserId, ACTIVE);
    }

    /**
     * Клиент отзывает свою заявку, пока она новая или в работе.
     *
     * @return отозванная заявка или пусто, если она чужая, не найдена или уже закрыта
     */
    @Transactional
    public Optional<Lead> withdraw(long tgUserId, long leadId) {
        return repository.findById(leadId)
                .filter(lead -> lead.getTgUserId() == tgUserId && ACTIVE.contains(lead.getStatus()))
                .map(lead -> {
                    lead.changeStatus(LeadStatus.WITHDRAWN, clock.instant());
                    events.publishEvent(new LeadWithdrawnEvent(lead));
                    return lead;
                });
    }

    @Transactional(readOnly = true)
    public Optional<Lead> find(long leadId) {
        return repository.findById(leadId);
    }

    @Transactional(readOnly = true)
    public List<Lead> latest(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public LeadStats statsFor(Duration period) {
        Instant since = clock.instant().minus(period);
        Map<ProjectType, Long> byType = new LinkedHashMap<>();
        repository.countByTypeSince(since).forEach(row -> byType.put(row.getType(), row.getTotal()));
        Map<LeadStatus, Long> byStatus = new EnumMap<>(LeadStatus.class);
        repository.countByStatusSince(since).forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new LeadStats(total, byType, byStatus);
    }
}
