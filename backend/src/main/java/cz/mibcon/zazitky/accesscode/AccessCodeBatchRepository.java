package cz.mibcon.zazitky.accesscode;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessCodeBatchRepository extends JpaRepository<AccessCodeBatch, UUID> {

    @Query("""
            select new cz.mibcon.zazitky.accesscode.AdminBatchDtos$BatchSummary(
                b.id, g.title, p.name, b.quantity, b.validityHours, b.multiSession, b.note, b.unitPriceCzk,
                b.createdAt, count(c.activatedAt))
            from AccessCodeBatch b
            join b.game g
            join b.partner p
            left join AccessCode c on c.batch = b
            group by b.id, g.title, p.name, b.quantity, b.validityHours, b.multiSession, b.note, b.unitPriceCzk,
                b.createdAt
            order by b.createdAt desc
            """)
    List<BatchSummary> findSummaries();
}
