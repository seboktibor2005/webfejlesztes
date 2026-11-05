package hu.eszkozpont.loan;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.maintenance.*;
import hu.eszkozpont.reservation.EquipmentSummary;
import hu.eszkozpont.user.*;
import org.mapstruct.*;
import java.time.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LoanMapper {
    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "requester", source = "reservation.requester")
    @Mapping(target = "overdue", expression = "java(loan.getReturnedAt() == null && loan.getDueAt().isBefore(now))")
    @Mapping(target = "returnedLate", expression = "java(loan.getReturnedAt() != null && loan.getDueAt().isBefore(loan.getReturnedAt()))")
    @Mapping(target = "overdueMinutes", expression = "java(lateMinutes(loan, now))")
    LoanResponse toResponse(Loan loan, @Context Instant now);
    EquipmentSummary toSummary(EquipmentItem equipment);
    UserSummary toSummary(AppUser user);
    MaintenanceResponse toResponse(MaintenancePeriod maintenance);

    default long lateMinutes(Loan loan, Instant now) {
        var end = loan.getReturnedAt() == null ? now : loan.getReturnedAt();
        // A megkezdett késési perc is látható, a pontos határidő még nem késés.
        var duration = Duration.between(loan.getDueAt(), end);
        return duration.isNegative() || duration.isZero() ? 0 : duration.toSeconds() / 60 + (duration.getNano() > 0 || duration.toSeconds() % 60 > 0 ? 1 : 0);
    }
}
