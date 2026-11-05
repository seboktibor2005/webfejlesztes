package hu.eszkozpont.audit;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    @EntityGraph(attributePaths = "actor")
    Page<AuditEvent> findAllByReservation_IdOrderByOccurredAtAscIdAsc(Long reservationId, Pageable page);
}
