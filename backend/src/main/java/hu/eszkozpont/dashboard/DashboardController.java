package hu.eszkozpont.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard/me")
    public UserDashboardResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/api/management/dashboard")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ManagementDashboardResponse management() {
        return service.management();
    }
}
