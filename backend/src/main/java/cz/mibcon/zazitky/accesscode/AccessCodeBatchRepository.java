package cz.mibcon.zazitky.accesscode;

import java.util.List;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.AdminBatchDtos.BatchSummary;
import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessCodeBatchRepository extends JpaRepository<AccessCodeBatch, UUID> {

    /**
     * @param partnerId        only batches of this partner; null = all
     * @param handedOverOnly   only batches already handed over to the partner
     */
    @Query("""
            select new cz.mibcon.zazitky.accesscode.AdminBatchDtos$BatchSummary(
                b.id, p.id, g.title, p.name, b.quantity, b.validityHours, b.multiSession, b.note, b.unitPriceCzk,
                b.createdAt, b.handedOverAt, count(c.activatedAt),
                count(case when c.status = cz.mibcon.zazitky.accesscode.domain.AccessCodeStatus.NEW then 1 end))
            from AccessCodeBatch b
            join b.game g
            join b.partner p
            left join AccessCode c on c.batch = b
            where (:partnerId is null or p.id = :partnerId)
              and (:handedOverOnly = false or b.handedOverAt is not null)
            group by b.id, p.id, g.title, p.name, b.quantity, b.validityHours, b.multiSession, b.note, b.unitPriceCzk,
                b.createdAt, b.handedOverAt
            order by b.createdAt desc
            """)
    List<BatchSummary> findSummaries(UUID partnerId, boolean handedOverOnly);
}
