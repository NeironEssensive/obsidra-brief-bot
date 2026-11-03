package dev.obsidra.briefbot.i18n;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class RedisLanguageStore implements LanguageStore {

    private static final String KEY_PREFIX = "brief:lang:";

    private final StringRedisTemplate redis;

    public RedisLanguageStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<Language> find(long userId) {
        String value = redis.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Language.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(long userId, Language language) {
        redis.opsForValue().set(key(userId), language.name());
    }

    private static String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
