package hu.eszkozpont.maintenance;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<MaintenancePeriod, Long>, JpaSpecificationExecutor<MaintenancePeriod> {
    @Query("select new hu.eszkozpont.equipment.availability.BlockingInterval(m.startsAt, m.endsAt, 'MAINTENANCE') from MaintenancePeriod m where m.equipment.id = :equipmentId and m.status = hu.eszkozpont.maintenance.MaintenanceStatus.OPEN and m.startsAt < :to and m.endsAt > :from")
    List<hu.eszkozpont.equipment.availability.BlockingInterval> findAvailabilityIntervals(Long equipmentId, Instant from, Instant to);

    @Query("select count(m) > 0 from MaintenancePeriod m where m.equipment.id = :equipmentId and m.status = hu.eszkozpont.maintenance.MaintenanceStatus.OPEN and m.startsAt <= :now and m.endsAt <= :now")
    boolean hasOverdueMaintenance(Long equipmentId, Instant now);

    @Override
    @EntityGraph(attributePaths = {"equipment", "createdBy", "loan", "loan.reservation"})
    Optional<MaintenancePeriod> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"equipment", "createdBy", "loan", "loan.reservation"})
    Page<MaintenancePeriod> findAll(Specification<MaintenancePeriod> specification, Pageable pageable);

    @Query("select m.equipment.id from MaintenancePeriod m where m.id = :id")
    Optional<Long> findEquipmentId(Long id);
}
