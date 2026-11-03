package dev.obsidra.briefbot.lead;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.obsidra.briefbot.i18n.Language;

@Entity
@Table(name = "leads")
public class Lead {

    /** Номер заявки, который видит клиент. Нумерация начинается с 1001. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tg_user_id", nullable = false)
    private long tgUserId;

    @Column(name = "tg_username")
    private String tgUsername;

    @Column(name = "client_name")
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** Имена {@link Asset} — в PostgreSQL это text[]. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] assets;

    @Column(nullable = false)
    private String budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Deadline deadline;

    @Column(nullable = false)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lead() {
    }

    static Lead create(LeadRequest request, Instant now) {
        Lead lead = new Lead();
        lead.tgUserId = request.tgUserId();
        lead.tgUsername = request.tgUsername();
        lead.clientName = request.clientName();
        lead.projectType = request.type();
        lead.description = request.description();
        lead.assets = Arrays.stream(Asset.values())
                .filter(request.assets()::contains)
                .map(Enum::name)
                .toArray(String[]::new);
        lead.budget = request.budget();
        lead.deadline = request.deadline();
        lead.contact = request.contact();
        lead.language = request.language();
        lead.status = LeadStatus.NEW;
        lead.createdAt = now;
        lead.updatedAt = now;
        return lead;
    }

    void changeStatus(LeadStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getTgUserId() {
        return tgUserId;
    }

    public String getTgUsername() {
        return tgUsername;
    }

    public String getClientName() {
        return clientName;
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public String getDescription() {
        return description;
    }

    public Set<Asset> getAssets() {
        Set<Asset> result = EnumSet.noneOf(Asset.class);
        Arrays.stream(assets).map(Asset::valueOf).forEach(result::add);
        return result;
    }

    public String getBudget() {
        return budget;
    }

    public Deadline getDeadline() {
        return deadline;
    }

    public String getContact() {
        return contact;
    }

    public Language getLanguage() {
        return language;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
