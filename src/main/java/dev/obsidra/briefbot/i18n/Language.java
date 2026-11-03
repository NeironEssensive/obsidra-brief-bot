package dev.obsidra.briefbot.i18n;

import java.util.Locale;
import java.util.Set;

public enum Language {
    RU("🇷🇺 русский"),
    EN("🇬🇧 английский");

    private static final Set<String> RUSSIAN_SPEAKING = Set.of("ru", "uk", "be", "kk");

    private final String label;

    Language(String label) {
        this.label = label;
    }

    public static Language fromTelegram(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return RU;
        }
        String primary = languageCode.strip().split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
        return RUSSIAN_SPEAKING.contains(primary) ? RU : EN;
    }

    public Locale locale() {
        return Locale.forLanguageTag(name().toLowerCase(Locale.ROOT));
    }

    public String label() {
        return label;
    }
}
