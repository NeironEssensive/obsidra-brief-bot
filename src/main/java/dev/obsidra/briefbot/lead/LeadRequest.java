package dev.obsidra.briefbot.lead;

import java.util.Set;

/** Готовая к сохранению заявка — то, что клиент заполнил в диалоге. */
public record LeadRequest(
        long tgUserId,
        String tgUsername,
        String clientName,
        ProjectType type,
        String description,
        Set<Asset> assets,
        String budget,
        Deadline deadline,
        String contact) {

    public LeadRequest {
        assets = Set.copyOf(assets);
    }
}
