package hu.eszkozpont.audit;

import hu.eszkozpont.user.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuditEventMapper {
    AuditEventResponse toResponse(AuditEvent event);
    UserSummary toSummary(AppUser user);
}
