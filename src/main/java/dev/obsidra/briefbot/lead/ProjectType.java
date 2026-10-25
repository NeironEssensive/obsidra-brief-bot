package dev.obsidra.briefbot.lead;

/** Что клиент хочет заказать. {@code example} — подсказка к вопросу «Опишите задачу». */
public enum ProjectType {
    SITE("Сайт", "Например: лендинг для кофейни с меню и бронированием столика"),
    WEB_SERVICE("Веб-сервис / личный кабинет", "Например: личный кабинет клиента с оплатой и историей заказов"),
    MOBILE("Мобильное приложение", "Например: приложение доставки для iOS и Android"),
    GAME("Игра", "Например: браузерная промо-игра для бренда с таблицей рекордов"),
    BOT("Telegram-бот", "Например: бот для записи клиентов в салон с напоминаниями"),
    BACKEND("Бэкенд / API", "Например: REST API для мобильного приложения с авторизацией"),
    OTHER("Другое", "Расскажите, что нужно сделать и для кого");

    private final String label;
    private final String example;

    ProjectType(String label, String example) {
        this.label = label;
        this.example = example;
    }

    public String label() {
        return label;
    }

    public String example() {
        return example;
    }
}
