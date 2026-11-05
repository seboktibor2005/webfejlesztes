package hu.eszkozpont.equipment;

import hu.eszkozpont.audit.AuditEventResponse;
import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/management/equipment")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class EquipmentManagementController {
    private final EquipmentManagementService service;
    public EquipmentManagementController(EquipmentManagementService service) { this.service = service; }

    @GetMapping("/{id}")
    public EquipmentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.get(Long.parseLong(jwt.getSubject()), id); }

    @GetMapping("/{id}/events")
    public PageResponse<AuditEventResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(Long.parseLong(jwt.getSubject()), id, page, size);
    }

    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EquipmentCreateRequest request) {
        var result = service.create(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/management/equipment/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public EquipmentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody EquipmentUpdateRequest request) {
        return service.update(Long.parseLong(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/retire")
    public EquipmentResponse retire(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody EquipmentRetireRequest request) {
        return service.retire(Long.parseLong(jwt.getSubject()), id, request);
    }
}
