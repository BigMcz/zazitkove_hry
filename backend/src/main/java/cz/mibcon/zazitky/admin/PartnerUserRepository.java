package cz.mibcon.zazitky.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.admin.domain.PartnerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartnerUserRepository extends JpaRepository<PartnerUser, UUID> {

    @Query("select u from PartnerUser u join fetch u.partner where u.username = :username")
    Optional<PartnerUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select u from PartnerUser u join fetch u.partner p order by p.name, u.username")
    List<PartnerUser> findAllWithPartner();
}
