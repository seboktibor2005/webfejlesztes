package hu.eszkozpont.reservation;

import hu.eszkozpont.dashboard.DashboardAppointment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
    long countByStatusAndStartsAtAfter(ReservationStatus status, Instant now);
    long countByRequester_IdAndStatusAndStartsAtAfter(long requesterId, ReservationStatus status, Instant now);

    @Query("""
            select new hu.eszkozpont.dashboard.DashboardAppointment(r.id, r.equipment.name, r.equipment.inventoryCode, r.startsAt)
            from Reservation r
            where r.requester.id = :requesterId and r.status = hu.eszkozpont.reservation.ReservationStatus.APPROVED
              and r.endsAt > :now
              and not exists (select l.id from Loan l where l.reservation = r)
            order by r.startsAt, r.id
            """)
    List<DashboardAppointment> findDashboardPickups(long requesterId, Instant now, Pageable page);

    @Query("""
            select count(r) from Reservation r
            where r.status = hu.eszkozpont.reservation.ReservationStatus.APPROVED
              and r.startsAt >= :dayStart and r.startsAt < :dayEnd and r.endsAt > :now
              and not exists (select l.id from Loan l where l.reservation = r)
            """)
    long countDashboardPickups(Instant dayStart, Instant dayEnd, Instant now);

    @Query("select new hu.eszkozpont.equipment.availability.BlockingInterval(r.startsAt, r.endsAt, 'RESERVATION') from Reservation r where r.equipment.id = :equipmentId and r.status = hu.eszkozpont.reservation.ReservationStatus.APPROVED and r.startsAt < :to and r.endsAt > :from")
    List<hu.eszkozpont.equipment.availability.BlockingInterval> findAvailabilityIntervals(Long equipmentId, Instant from, Instant to);

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

    @Query(value = """
            select r.id from reservation r
             where r.id in (:ids) and r.status = 'APPROVED'
               and not exists(select 1 from loan own_loan where own_loan.reservation_id = r.id)
               and (exists(select 1 from loan l where l.equipment_id = r.equipment_id
                     and l.returned_at is null and l.due_at < :now)
                 or exists(select 1 from maintenance_period m where m.equipment_id = r.equipment_id and m.status = 'OPEN'
                     and (m.starts_at <= :now or (m.starts_at < r.ends_at and m.ends_at > r.starts_at))))
            """, nativeQuery = true)
    List<Long> findAtRiskIds(List<Long> ids, Instant now);

    @Query("select count(r) > 0 from Reservation r where r.equipment.id = :equipmentId and r.status in (hu.eszkozpont.reservation.ReservationStatus.REQUESTED, hu.eszkozpont.reservation.ReservationStatus.APPROVED) and r.endsAt > :now")
    boolean hasLiveReservation(Long equipmentId, Instant now);

    @Query(value = "select exists(select 1 from reservation where equipment_id = :equipmentId and status = 'APPROVED' and starts_at < :endsAt and ends_at > :startsAt and ends_at > :now)", nativeQuery = true)
    boolean hasApprovedMaintenanceConflict(Long equipmentId, Instant startsAt, Instant endsAt, Instant now);

    @Query(value = """
            select r.id from reservation r
            where r.status in ('REQUESTED','APPROVED') and r.ends_at <= :now
              and not exists(select 1 from loan l where l.reservation_id = r.id)
            order by r.ends_at, r.id
            """, nativeQuery = true)
    List<Long> findExpiryCandidates(Instant now, Pageable pageable);
}
