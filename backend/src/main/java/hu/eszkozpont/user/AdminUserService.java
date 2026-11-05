package hu.eszkozpont.user;

import hu.eszkozpont.common.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@Service
@Validated
@Transactional(readOnly = true)
public class AdminUserService {
    private final UserRepository users;
    private final UserMapper mapper;
    public AdminUserService(UserRepository users, UserMapper mapper) { this.users = users; this.mapper = mapper; }

    public PageResponse<AdminUserResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Érvénytelen lapozás.");
        }
        return PageResponse.of(users.findAll(PageRequest.of(page, size, Sort.by("id"))).map(mapper::toAdminResponse));
    }

    public AdminUserResponse get(long id) {
        return mapper.toAdminResponse(users.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public AdminUserResponse update(long actorId, long id, @NotNull @Valid AdminUserUpdateRequest request) {
        // Az adminszám és a jogosultság friss olvasása a közös tranzakciós zár UTÁN történik.
        users.lockAdministration();
        var target = users.lockById(id).orElseThrow(this::notFound);
        var actor = users.findById(actorId).orElseThrow(this::notFound);
        if (!actor.isEnabled() || actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "A módosításhoz aktív adminisztrátori jogosultság szükséges.");
        }
        if (target.getVersion() != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "A felhasználó adatai időközben megváltoztak. Töltsd be a friss adatokat.");
        }
        boolean removesAdmin = target.isEnabled() && target.getRole() == Role.ADMIN
                && (!request.enabled() || request.role() != Role.ADMIN);
        if (removesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "Az utolsó aktív adminisztrátor nem tiltható le és nem fokozható le.");
        }
        boolean changed = !Objects.equals(target.getDisplayName(), request.displayName())
                || target.getRole() != request.role() || target.isEnabled() != request.enabled();
        if (changed) {
            target.setDisplayName(request.displayName());
            target.setRole(request.role());
            target.setEnabled(request.enabled());
            target.setTokenVersion(target.getTokenVersion() + 1);
            users.flush();
        }
        return mapper.toAdminResponse(target);
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "A felhasználó nem található."); }
}
