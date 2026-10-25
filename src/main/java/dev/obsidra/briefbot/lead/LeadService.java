package dev.obsidra.briefbot.lead;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.obsidra.briefbot.config.BotProperties;

@Service
public class LeadService {

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

    @Transactional
    public Optional<Lead> changeStatus(long leadId, LeadStatus status) {
        return repository.findById(leadId).map(lead -> {
            lead.changeStatus(status, clock.instant());
            return lead;
        });
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
