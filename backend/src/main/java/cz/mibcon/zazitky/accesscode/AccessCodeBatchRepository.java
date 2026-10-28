package cz.mibcon.zazitky.accesscode;

import java.util.UUID;

import cz.mibcon.zazitky.accesscode.domain.AccessCodeBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeBatchRepository extends JpaRepository<AccessCodeBatch, UUID> {
}
