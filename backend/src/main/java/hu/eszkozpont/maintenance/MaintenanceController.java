package hu.eszkozpont.maintenance;

import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/management/maintenance")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class MaintenanceController {
    private final MaintenanceService service;
    public MaintenanceController(MaintenanceService service) { this.service = service; }

    @GetMapping
    public PageResponse<MaintenanceDetailResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long equipmentId, @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return service.list(Long.parseLong(jwt.getSubject()), equipmentId, status, page, size);
    }

    @GetMapping("/{id}")
    public MaintenanceDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.get(Long.parseLong(jwt.getSubject()), id); }

    @PostMapping
    public ResponseEntity<MaintenanceDetailResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MaintenanceCreateRequest request) {
        var result = service.create(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/management/maintenance/" + result.id())).body(result);
    }

    @PostMapping("/{id}/close")
    public MaintenanceResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody MaintenanceCloseRequest request) {
        return service.close(Long.parseLong(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/cancel")
    public MaintenanceDetailResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody MaintenanceCancelRequest request) {
        return service.cancel(Long.parseLong(jwt.getSubject()), id, request);
    }
}
