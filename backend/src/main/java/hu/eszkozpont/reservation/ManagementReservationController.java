package hu.eszkozpont.reservation;

import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/reservations")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ManagementReservationController {
    private final ReservationService service;
    public ManagementReservationController(ReservationService service) { this.service = service; }

    @GetMapping
    public PageResponse<ReservationSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.management(Long.parseLong(jwt.getSubject()), status, page, size, sort);
    }

    @PostMapping("/{id}/approve")
    public ReservationResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody ApproveRequest request) {
        return service.approve(Long.parseLong(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/reject")
    public ReservationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody RejectRequest request) {
        return service.reject(Long.parseLong(jwt.getSubject()), id, request);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody CancelRequest request) {
        return service.cancel(Long.parseLong(jwt.getSubject()), id, request, true);
    }
}
