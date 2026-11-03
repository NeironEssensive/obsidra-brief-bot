package dev.obsidra.briefbot.i18n;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalLanguageStore implements LanguageStore {

    private final Map<Long, Language> languages = new ConcurrentHashMap<>();

    @Override
    public Optional<Language> find(long userId) {
        return Optional.ofNullable(languages.get(userId));
    }

    @Override
    public void save(long userId, Language language) {
        languages.put(userId, language);
    }
}
