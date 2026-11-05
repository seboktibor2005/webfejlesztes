package hu.eszkozpont.user;

import hu.eszkozpont.common.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable long id) { return service.get(id); }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        return service.update(Long.parseLong(jwt.getSubject()), id, request);
    }
}
