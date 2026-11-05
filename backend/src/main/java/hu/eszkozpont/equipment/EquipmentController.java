package hu.eszkozpont.equipment;

import hu.eszkozpont.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class EquipmentController {
    private final EquipmentService service;
    public EquipmentController(EquipmentService service) { this.service = service; }

    @GetMapping("/api/equipment")
    public PageResponse<EquipmentResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(q, categoryId, locationId, page, size, sort, false);
    }

    @GetMapping("/api/management/equipment")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public PageResponse<EquipmentResponse> managementList(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(q, categoryId, locationId, page, size, sort, true);
    }

    @GetMapping("/api/equipment/{id}")
    public EquipmentResponse get(@PathVariable long id) { return service.get(id); }
    @GetMapping("/api/categories") public List<CategoryResponse> categories() { return service.categories(); }
    @GetMapping("/api/locations") public List<LocationResponse> locations() { return service.locations(); }
}
