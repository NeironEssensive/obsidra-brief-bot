package dev.obsidra.briefbot.i18n;

import java.util.Optional;

public interface LanguageStore {

    Optional<Language> find(long userId);

    void save(long userId, Language language);
}
