package hu.eszkozpont.reservation;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.loan.LoanResponse;
import hu.eszkozpont.user.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReservationMapper {
    @Mapping(target = "id", source = "reservation.id")
    @Mapping(target = "equipment", source = "reservation.equipment")
    @Mapping(target = "requester", source = "reservation.requester")
    ReservationResponse toResponse(Reservation reservation, LoanResponse loan, boolean atRisk);
    ReservationSummaryResponse toSummary(Reservation reservation, boolean hasOpenLoan, boolean overdue, boolean atRisk);
    EquipmentSummary toSummary(EquipmentItem equipment);
    UserSummary toSummary(AppUser user);
}
