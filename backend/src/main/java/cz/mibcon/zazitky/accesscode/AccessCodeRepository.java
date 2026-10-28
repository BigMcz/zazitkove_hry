package cz.mibcon.zazitky.accesscode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {

    Optional<AccessCode> findByCode(String code);

    @Query("select c.code from AccessCode c where c.code in :codes")
    List<String> findExistingCodes(Collection<String> codes);

    List<AccessCode> findByBatchIdOrderByCode(UUID batchId);
}
