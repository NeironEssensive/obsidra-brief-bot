package dev.obsidra.briefbot.dialog;

import java.util.Optional;

/** Хранилище незаконченных заявок. В приложении — Redis, в тестах — обычная Map. */
public interface DraftStore {

    Optional<Draft> find(long userId);

    /** Сохраняет черновик и заново отсчитывает время его жизни. */
    void save(Draft draft);

    void delete(long userId);
}
