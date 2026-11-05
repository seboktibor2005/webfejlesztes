package hu.eszkozpont.equipment;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<EquipmentItem, Long>, JpaSpecificationExecutor<EquipmentItem> {
    @Override @EntityGraph(attributePaths = {"category", "location"})
    Page<EquipmentItem> findAll(Specification<EquipmentItem> specification, Pageable pageable);
    @Override @EntityGraph(attributePaths = {"category", "location"})
    Optional<EquipmentItem> findById(Long id);
}
