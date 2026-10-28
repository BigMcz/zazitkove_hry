package cz.mibcon.zazitky.accesscode;

import java.util.UUID;

import cz.mibcon.zazitky.accesscode.domain.AccessCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {
}
