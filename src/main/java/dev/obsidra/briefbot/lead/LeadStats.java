package dev.obsidra.briefbot.lead;

import java.util.Map;

/** Сводка заявок за период: всего, по типам проектов и по статусам. */
public record LeadStats(long total, Map<ProjectType, Long> byType, Map<LeadStatus, Long> byStatus) {
}
