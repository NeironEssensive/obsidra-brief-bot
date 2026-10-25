package dev.obsidra.briefbot.dialog;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import dev.obsidra.briefbot.config.BotProperties;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.ProjectType;

/**
 * Черновик хранится в Redis как hash {@code brief:draft:<userId>} с TTL: брошенные заявки удаляются сами.
 */
@Component
public class RedisDraftStore implements DraftStore {

    private static final Logger log = LoggerFactory.getLogger(RedisDraftStore.class);
    private static final String KEY_PREFIX = "brief:draft:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDraftStore(StringRedisTemplate redis, BotProperties props) {
        this.redis = redis;
        this.ttl = props.draftTtl();
    }

    @Override
    public Optional<Draft> find(long userId) {
        Map<String, String> fields = redis.<String, String>opsForHash().entries(key(userId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromFields(userId, fields));
        } catch (IllegalArgumentException e) {
            // формат черновика изменился после обновления бота — проще начать заявку заново
            log.warn("Не удалось прочитать черновик {}, удаляю его", userId, e);
            delete(userId);
            return Optional.empty();
        }
    }

    @Override
    public void save(Draft draft) {
        String key = key(draft.getUserId());
        // Апдейты Telegram обрабатываются в одном потоке, поэтому delete + putAll без транзакции безопасны
        redis.delete(key);
        redis.opsForHash().putAll(key, toFields(draft));
        redis.expire(key, ttl);
    }

    @Override
    public void delete(long userId) {
        redis.delete(key(userId));
    }

    private static String key(long userId) {
        return KEY_PREFIX + userId;
    }

    private static Map<String, String> toFields(Draft draft) {
        Map<String, String> fields = new HashMap<>();
        fields.put("step", draft.getStep().name());
        fields.put("assets", draft.getAssets().stream().map(Enum::name).collect(Collectors.joining(",")));
        fields.put("editing", String.valueOf(draft.isEditing()));
        putIfPresent(fields, "type", draft.getType() == null ? null : draft.getType().name());
        putIfPresent(fields, "description", draft.getDescription());
        putIfPresent(fields, "budget", draft.getBudget());
        putIfPresent(fields, "deadline", draft.getDeadline() == null ? null : draft.getDeadline().name());
        putIfPresent(fields, "contact", draft.getContact());
        return fields;
    }

    private static Draft fromFields(long userId, Map<String, String> fields) {
        Draft draft = new Draft(userId);
        draft.setStep(Step.valueOf(fields.get("step")));
        draft.setType(enumOrNull(ProjectType.class, fields.get("type")));
        draft.setDescription(fields.get("description"));
        String assets = fields.getOrDefault("assets", "");
        if (!assets.isEmpty()) {
            draft.setAssets(Arrays.stream(assets.split(",")).map(Asset::valueOf).toList());
        }
        draft.setBudget(fields.get("budget"));
        draft.setDeadline(enumOrNull(Deadline.class, fields.get("deadline")));
        draft.setContact(fields.get("contact"));
        draft.setEditing(Boolean.parseBoolean(fields.get("editing")));
        return draft;
    }

    private static void putIfPresent(Map<String, String> fields, String name, String value) {
        if (value != null) {
            fields.put(name, value);
        }
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
