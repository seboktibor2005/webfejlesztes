package hu.eszkozpont.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    @Modifying
    @Query("update AppUser u set u.tokenVersion = u.tokenVersion + 1, u.version = u.version + 1 where u.id = :id")
    int invalidateTokens(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockById(Long id);

    // A két egész szám az alkalmazás felhasználó-adminisztrációs zárának rögzített névtere.
    @Query(value = "select 1 from pg_advisory_xact_lock(173528, 1)", nativeQuery = true)
    Integer lockAdministration();

    long countByRoleAndEnabledTrue(Role role);
}
