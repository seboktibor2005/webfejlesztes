package hu.eszkozpont.loan;

import hu.eszkozpont.dashboard.CategoryLoanCount;
import hu.eszkozpont.dashboard.DashboardAppointment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;

public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {
    long countByReservation_Requester_IdAndReturnedAtIsNull(long requesterId);
    long countByReturnedAtIsNullAndDueAtGreaterThanEqualAndDueAtLessThan(Instant dayStart, Instant dayEnd);
    long countByReturnedAtIsNullAndDueAtLessThan(Instant now);

    @Query("""
            select new hu.eszkozpont.dashboard.DashboardAppointment(l.reservation.id, l.equipment.name, l.equipment.inventoryCode, l.dueAt)
            from Loan l where l.reservation.requester.id = :requesterId and l.returnedAt is null
            order by l.dueAt, l.id
            """)
    List<DashboardAppointment> findDashboardReturns(long requesterId, Pageable page);

    @Query("""
            select new hu.eszkozpont.dashboard.CategoryLoanCount(c.id, c.name, count(l))
            from Loan l join l.equipment.category c
            where l.issuedAt >= :from and l.issuedAt <= :now
            group by c.id, c.name
            order by count(l) desc, c.name, c.id
            """)
    List<CategoryLoanCount> countIssuedByCategory(Instant from, Instant now);

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
