package dev.obsidra.briefbot.lead;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    long countByTgUserIdAndCreatedAtAfter(long tgUserId, Instant after);

    List<Lead> findAllByOrderByCreatedAtDesc(Limit limit);

    @Query("""
            select l.projectType as type, count(l) as total
            from Lead l
            where l.createdAt >= :since
            group by l.projectType
            order by count(l) desc""")
    List<TypeCount> countByTypeSince(@Param("since") Instant since);

    @Query("""
            select l.status as status, count(l) as total
            from Lead l
            where l.createdAt >= :since
            group by l.status""")
    List<StatusCount> countByStatusSince(@Param("since") Instant since);

    interface TypeCount {
        ProjectType getType();

        long getTotal();
    }

    interface StatusCount {
        LeadStatus getStatus();

        long getTotal();
    }
}
