package hu.eszkozpont.loan;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;

public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {
    @Override
    @EntityGraph(attributePaths = {"reservation", "reservation.requester", "equipment", "issuedBy", "returnedBy", "maintenance"})
    Optional<Loan> findById(Long id);

    @EntityGraph(attributePaths = {"reservation", "reservation.requester", "equipment", "issuedBy", "returnedBy", "maintenance"})
    Optional<Loan> findByReservation_Id(Long reservationId);

    @EntityGraph(attributePaths = {"reservation", "reservation.requester", "equipment", "issuedBy", "returnedBy", "maintenance"})
    List<Loan> findAllByReservation_IdIn(Collection<Long> ids);

    @Override
    @EntityGraph(attributePaths = {"reservation", "reservation.requester", "equipment", "issuedBy", "returnedBy", "maintenance"})
    Page<Loan> findAll(Specification<Loan> specification, Pageable pageable);

    boolean existsByEquipment_IdAndReturnedAtIsNull(Long equipmentId);

    @Query("select l.equipment.id from Loan l where l.id = :id")
    Optional<Long> findEquipmentId(Long id);

    @Query(value = "select exists(select 1 from loan where equipment_id = :equipmentId and returned_at is null and (due_at > :startsAt or due_at <= :now))", nativeQuery = true)
    boolean blocksMaintenance(Long equipmentId, Instant startsAt, Instant now);
}
