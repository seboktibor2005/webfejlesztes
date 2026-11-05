package hu.eszkozpont.maintenance;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface MaintenanceRepository extends JpaRepository<MaintenancePeriod, Long>, JpaSpecificationExecutor<MaintenancePeriod> {
    @Override
    @EntityGraph(attributePaths = {"equipment", "createdBy", "loan", "loan.reservation"})
    Optional<MaintenancePeriod> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"equipment", "createdBy", "loan", "loan.reservation"})
    Page<MaintenancePeriod> findAll(Specification<MaintenancePeriod> specification, Pageable pageable);

    @Query("select m.equipment.id from MaintenancePeriod m where m.id = :id")
    Optional<Long> findEquipmentId(Long id);
}
