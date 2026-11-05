package hu.eszkozpont.user;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    UserResponse toResponse(AppUser user);
    AdminUserResponse toAdminResponse(AppUser user);
}
