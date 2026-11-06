package hu.eszkozpont.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface LocationRepository extends JpaRepository<StorageLocation, Long>, JpaSpecificationExecutor<StorageLocation> {
    List<StorageLocation> findAllByActiveTrueOrderByNameAsc();
    @Query("select count(l) > 0 from StorageLocation l where upper(trim(l.code)) = :code and l.id <> :exceptId")
    boolean normalizedCodeExists(String code, long exceptId);
}
