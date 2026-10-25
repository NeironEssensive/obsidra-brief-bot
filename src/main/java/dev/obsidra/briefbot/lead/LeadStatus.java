package dev.obsidra.briefbot.lead;

public enum LeadStatus {
    NEW("новая"),
    IN_PROGRESS("в работе"),
    REJECTED("отклонена"),
    WITHDRAWN("отозвана клиентом");

    private final String label;

    LeadStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
