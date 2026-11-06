package hu.eszkozpont.equipment.reference;

import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ReferenceDataController {
    private final ReferenceDataService service;
    public ReferenceDataController(ReferenceDataService service) { this.service = service; }

    @GetMapping("/categories")
    public PageResponse<CategoryAdminResponse> categories(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.categories(Long.parseLong(jwt.getSubject()), q, active, page, size);
    }
    @GetMapping("/categories/{id}")
    public CategoryAdminResponse category(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.category(Long.parseLong(jwt.getSubject()), id); }
    @PostMapping("/categories")
    public ResponseEntity<CategoryAdminResponse> createCategory(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryCreateRequest request) {
        var result = service.createCategory(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/admin/categories/" + result.id())).body(result);
    }
    @PutMapping("/categories/{id}")
    public CategoryAdminResponse updateCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody CategoryUpdateRequest request) { return service.updateCategory(Long.parseLong(jwt.getSubject()), id, request); }
    @PatchMapping("/categories/{id}/active")
    public ResponseEntity<Void> categoryActive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ActiveRequest request) { service.categoryActive(Long.parseLong(jwt.getSubject()), id, request); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam long version) { service.deleteCategory(Long.parseLong(jwt.getSubject()), id, version); return ResponseEntity.noContent().build(); }

    @GetMapping("/locations")
    public PageResponse<LocationAdminResponse> locations(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.locations(Long.parseLong(jwt.getSubject()), q, active, page, size);
    }
    @GetMapping("/locations/{id}")
    public LocationAdminResponse location(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.location(Long.parseLong(jwt.getSubject()), id); }
    @PostMapping("/locations")
    public ResponseEntity<LocationAdminResponse> createLocation(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationCreateRequest request) {
        var result = service.createLocation(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/admin/locations/" + result.id())).body(result);
    }
    @PutMapping("/locations/{id}")
    public LocationAdminResponse updateLocation(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody LocationUpdateRequest request) { return service.updateLocation(Long.parseLong(jwt.getSubject()), id, request); }
    @PatchMapping("/locations/{id}/active")
    public ResponseEntity<Void> locationActive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ActiveRequest request) { service.locationActive(Long.parseLong(jwt.getSubject()), id, request); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deleteLocation(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam long version) { service.deleteLocation(Long.parseLong(jwt.getSubject()), id, version); return ResponseEntity.noContent().build(); }
}
