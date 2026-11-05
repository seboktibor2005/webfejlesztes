package hu.eszkozpont.equipment;

import hu.eszkozpont.common.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EquipmentService {
    private final EquipmentRepository equipment;
    private final CategoryRepository categories;
    private final LocationRepository locations;
    private final EquipmentMapper mapper;
    public EquipmentService(EquipmentRepository equipment, CategoryRepository categories, LocationRepository locations, EquipmentMapper mapper) {
        this.equipment = equipment; this.categories = categories; this.locations = locations; this.mapper = mapper;
    }

    public PageResponse<EquipmentResponse> list(String q, Long categoryId, Long locationId, int page, int size, String sort, boolean management) {
        if (page < 0 || size < 1 || size > 100 || q != null && q.length() > 120
                || categoryId != null && categoryId < 1 || locationId != null && locationId < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Érvénytelen keresési vagy lapozási paraméter.");
        }
        var order = switch (sort) {
            case "name,asc" -> Sort.by("name").ascending();
            case "name,desc" -> Sort.by("name").descending();
            case "inventoryCode,asc" -> Sort.by("inventoryCode").ascending();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Ismeretlen rendezési feltétel.");
        };
        Specification<EquipmentItem> specification = (root, query, cb) -> {
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!management) {
                conditions.add(cb.equal(root.get("lifecycle"), EquipmentLifecycle.ACTIVE));
                conditions.add(cb.isTrue(root.get("category").get("active")));
                conditions.add(cb.isTrue(root.get("location").get("active")));
            }
            if (categoryId != null) conditions.add(cb.equal(root.get("category").get("id"), categoryId));
            if (locationId != null) conditions.add(cb.equal(root.get("location").get("id"), locationId));
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                conditions.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("inventoryCode")), pattern, '\\')));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.of(equipment.findAll(specification, PageRequest.of(page, size, order.and(Sort.by("id")))).map(mapper::toResponse));
    }

    public EquipmentResponse get(long id) {
        var item = equipment.findById(id).filter(e -> e.getLifecycle() == EquipmentLifecycle.ACTIVE
                && e.getCategory().isActive() && e.getLocation().isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Az eszköz nem található a katalógusban."));
        return mapper.toResponse(item);
    }
    public List<CategoryResponse> categories() { return categories.findAllByActiveTrueOrderByNameAsc().stream().map(mapper::toResponse).toList(); }
    public List<LocationResponse> locations() { return locations.findAllByActiveTrueOrderByNameAsc().stream().map(mapper::toResponse).toList(); }
}
