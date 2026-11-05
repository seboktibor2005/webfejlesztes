package hu.eszkozpont.reservation;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
    @Override
    @EntityGraph(attributePaths = {"equipment", "requester", "decidedBy"})
    Optional<Reservation> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"equipment", "requester"})
    Page<Reservation> findAll(Specification<Reservation> specification, Pageable page);

    @Query("select r.equipment.id from Reservation r where r.id = :id")
    Optional<Long> findEquipmentId(Long id);

    boolean existsByEquipment_IdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            Long equipmentId, ReservationStatus status, Instant endsAt, Instant startsAt);

    @Query(value = """
            select exists(select 1 from maintenance_period
              where equipment_id = :equipmentId and status = 'OPEN'
                and ((starts_at < :endsAt and ends_at > :startsAt)
                  or (starts_at <= :now and ends_at <= :now)))
            """, nativeQuery = true)
    boolean hasBlockingMaintenance(Long equipmentId, Instant startsAt, Instant endsAt, Instant now);

    @Query(value = """
            select exists(select 1 from loan
              where equipment_id = :equipmentId and returned_at is null and due_at < :now)
            """, nativeQuery = true)
    boolean hasOverdueLoan(Long equipmentId, Instant now);

    @Query(value = "select exists(select 1 from loan where reservation_id = :reservationId)", nativeQuery = true)
    boolean hasLoan(Long reservationId);
}
