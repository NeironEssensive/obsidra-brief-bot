package dev.obsidra.briefbot.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import dev.obsidra.briefbot.i18n.Language;

/**
 * Настройки бота из секции {@code bot} в application.yml.
 *
 * @param token           токен от @BotFather
 * @param adminChatId     Telegram ID владельца: ему приходят заявки и только он видит админ-команды
 *                        (узнать свой ID — команда /myid); 0 — владелец не задан
 * @param pollingEnabled  подключаться ли к Telegram (в тестах выключено)
 * @param budgets         варианты бюджета для каждого языка в том порядке, в котором их показывать
 * @param dailyLimit      сколько заявок один человек может отправить за сутки
 * @param draftTtl        сколько живёт незаконченная заявка с момента последнего ответа
 * @param humanContactUrl куда ведёт кнопка «Написать человеку»
 * @param privacyUrl      страница политики обработки персональных данных
 * @param zone            часовой пояс для дат в карточках заявок
 * @param proxy           прокси до api.telegram.org: {@code http://host:port} или {@code socks5://host:port};
 *                        пусто — напрямую
 */
@Validated
@ConfigurationProperties("bot")
public record BotProperties(
        @NotBlank String token,
        @DefaultValue("0") long adminChatId,
        @DefaultValue("true") boolean pollingEnabled,
        Map<Language, List<String>> budgets,
        @DefaultValue("3") @Positive int dailyLimit,
        @DefaultValue("24h") Duration draftTtl,
        @NotBlank String humanContactUrl,
        @NotBlank String privacyUrl,
        @DefaultValue("Europe/Moscow") ZoneId zone,
        String proxy) {

    public BotProperties {
        for (Language language : Language.values()) {
            if (budgets == null || budgets.getOrDefault(language, List.of()).isEmpty()) {
                throw new IllegalArgumentException("Не заданы варианты бюджета: bot.budgets."
                        + language.name().toLowerCase(Locale.ROOT));
            }
        }
        budgets = Map.copyOf(budgets);
    }

    public List<String> budgets(Language language) {
        return budgets.get(language);
    }

    public boolean hasAdmin() {
        return adminChatId != 0;
    }
}
