package hu.eszkozpont.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findAllByActiveTrueOrderByNameAsc();
}
