package dev.obsidra.briefbot.dialog;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.ProjectType;

/** Незаконченная заявка: ответы, которые клиент уже дал, и шаг, на котором он сейчас. */
public class Draft {

    private final long userId;
    private Step step = Step.TYPE;
    private ProjectType type;
    private String description;
    private final Set<Asset> assets = EnumSet.noneOf(Asset.class);
    private String budget;
    private Deadline deadline;
    private String contact;
    /** Ответ меняют с карточки подтверждения: после него вернуться к карточке, а не идти дальше по вопросам. */
    private boolean editing;

    public Draft(long userId) {
        this.userId = userId;
    }

    /** «Только идея» исключает остальные варианты, и наоборот. */
    public void toggleAsset(Asset asset) {
        if (assets.remove(asset)) {
            return;
        }
        if (asset == Asset.IDEA_ONLY) {
            assets.clear();
        } else {
            assets.remove(Asset.IDEA_ONLY);
        }
        assets.add(asset);
    }

    public long getUserId() {
        return userId;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Asset> getAssets() {
        return Collections.unmodifiableSet(assets);
    }

    public void setAssets(Collection<Asset> values) {
        assets.clear();
        assets.addAll(values);
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public Deadline getDeadline() {
        return deadline;
    }

    public void setDeadline(Deadline deadline) {
        this.deadline = deadline;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }
}
