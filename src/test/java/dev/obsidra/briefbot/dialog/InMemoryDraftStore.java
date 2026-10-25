package dev.obsidra.briefbot.dialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Хранит копии черновиков, как Redis: если сервис поменяет черновик и забудет его сохранить,
 * тест это заметит.
 */
class InMemoryDraftStore implements DraftStore {

    private final Map<Long, Draft> drafts = new HashMap<>();

    @Override
    public Optional<Draft> find(long userId) {
        return Optional.ofNullable(drafts.get(userId)).map(InMemoryDraftStore::copy);
    }

    @Override
    public void save(Draft draft) {
        drafts.put(draft.getUserId(), copy(draft));
    }

    @Override
    public void delete(long userId) {
        drafts.remove(userId);
    }

    private static Draft copy(Draft source) {
        Draft copy = new Draft(source.getUserId());
        copy.setStep(source.getStep());
        copy.setType(source.getType());
        copy.setDescription(source.getDescription());
        copy.setAssets(source.getAssets());
        copy.setBudget(source.getBudget());
        copy.setDeadline(source.getDeadline());
        copy.setContact(source.getContact());
        copy.setEditing(source.isEditing());
        return copy;
    }
}
