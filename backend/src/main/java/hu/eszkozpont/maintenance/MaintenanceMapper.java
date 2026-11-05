package hu.eszkozpont.maintenance;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.reservation.EquipmentSummary;
import hu.eszkozpont.user.*;
import org.mapstruct.*;
import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MaintenanceMapper {
    @Mapping(target = "loanId", source = "loan.id")
    @Mapping(target = "reservationId", source = "loan.reservation.id")
    @Mapping(target = "overdue", expression = "java(period.getStatus() == MaintenanceStatus.OPEN && !period.getEndsAt().isAfter(now))")
    MaintenanceDetailResponse toDetail(MaintenancePeriod period, @Context Instant now);
    MaintenanceResponse toResponse(MaintenancePeriod period);
    EquipmentSummary toSummary(EquipmentItem item);
    UserSummary toSummary(AppUser user);
}
