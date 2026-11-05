package hu.eszkozpont.reservation;

import hu.eszkozpont.audit.AuditEventResponse;
import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReservationCreateRequest request) {
        var response = service.create(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/reservations/" + response.id())).body(response);
    }

    @GetMapping("/me/reservations")
    public PageResponse<ReservationSummaryResponse> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.mine(Long.parseLong(jwt.getSubject()), status, page, size, sort);
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.get(Long.parseLong(jwt.getSubject()), id);
    }

    @GetMapping("/reservations/{id}/events")
    public PageResponse<AuditEventResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(Long.parseLong(jwt.getSubject()), id, page, size);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody CancelRequest request) {
        return service.cancel(Long.parseLong(jwt.getSubject()), id, request, false);
    }
}
