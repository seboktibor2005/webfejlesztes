package hu.eszkozpont.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    @Modifying
    @Query("update AppUser u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int invalidateTokens(long id);
}
