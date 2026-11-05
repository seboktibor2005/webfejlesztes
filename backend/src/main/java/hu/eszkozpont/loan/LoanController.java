package hu.eszkozpont.loan;

import hu.eszkozpont.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/management")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class LoanController {
    private final LoanService service;
    public LoanController(LoanService service) { this.service = service; }

    @PostMapping("/reservations/{id}/checkout")
    public ResponseEntity<LoanResponse> checkout(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody CheckoutRequest request) {
        var response = service.checkout(Long.parseLong(jwt.getSubject()), id, request);
        return ResponseEntity.created(URI.create("/api/management/loans/" + response.id())).body(response);
    }

    @GetMapping("/loans")
    public PageResponse<LoanResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Boolean open, @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return service.list(Long.parseLong(jwt.getSubject()), open, overdue, page, size);
    }

    @GetMapping("/loans/{id}")
    public LoanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.get(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/loans/{id}/return")
    public LoanResponse takeBack(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody ReturnRequest request) {
        return service.takeBack(Long.parseLong(jwt.getSubject()), id, request);
    }

}
