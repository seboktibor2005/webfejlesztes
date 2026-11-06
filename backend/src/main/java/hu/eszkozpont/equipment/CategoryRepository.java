package hu.eszkozpont.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CategoryRepository extends JpaRepository<EquipmentCategory, Long>, JpaSpecificationExecutor<EquipmentCategory> {
    List<EquipmentCategory> findAllByActiveTrueOrderByNameAsc();
    @Query("select count(c) > 0 from EquipmentCategory c where lower(trim(c.name)) = lower(:name) and c.id <> :exceptId")
    boolean normalizedNameExists(String name, long exceptId);
}
