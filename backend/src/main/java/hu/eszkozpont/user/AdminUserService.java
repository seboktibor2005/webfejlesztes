package hu.eszkozpont.user;

import hu.eszkozpont.common.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
}
