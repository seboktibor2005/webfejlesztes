package hu.eszkozpont.equipment;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface EquipmentRepository extends JpaRepository<EquipmentItem, Long>, JpaSpecificationExecutor<EquipmentItem> {
    boolean existsByInventoryCode(String inventoryCode);
    boolean existsByCategory_Id(Long categoryId);
    boolean existsByLocation_Id(Long locationId);
    @Query("select e.id from EquipmentItem e where e.category.id = :id order by e.id")
    List<Long> findIdsByCategory(Long id);
    @Query("select e.id from EquipmentItem e where e.location.id = :id order by e.id")
    List<Long> findIdsByLocation(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EquipmentItem e where e.id = :id")
    Optional<EquipmentItem> lockById(Long id);

    @Override @EntityGraph(attributePaths = {"category", "location"})
    Page<EquipmentItem> findAll(Specification<EquipmentItem> specification, Pageable pageable);
    @Override @EntityGraph(attributePaths = {"category", "location"})
    Optional<EquipmentItem> findById(Long id);
}
