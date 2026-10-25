package dev.obsidra.briefbot.dialog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import dev.obsidra.briefbot.config.BotProperties;

/**
 * Черновики в памяти приложения — для запуска на своём компьютере без Redis (профиль {@code local}).
 * Время жизни то же, что в Redis; после перезапуска бота черновики теряются.
 */
@Component
@Profile("local")
public class LocalDraftStore implements DraftStore {

    private final Map<Long, Entry> drafts = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public LocalDraftStore(BotProperties props, Clock clock) {
        this.ttl = props.draftTtl();
        this.clock = clock;
    }

    @Override
    public Optional<Draft> find(long userId) {
        Entry entry = drafts.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant())) {
            drafts.remove(userId);
            return Optional.empty();
        }
        return Optional.of(entry.draft());
    }

    @Override
    public void save(Draft draft) {
        drafts.put(draft.getUserId(), new Entry(draft, clock.instant().plus(ttl)));
    }

    @Override
    public void delete(long userId) {
        drafts.remove(userId);
    }

    private record Entry(Draft draft, Instant expiresAt) {
    }
}
