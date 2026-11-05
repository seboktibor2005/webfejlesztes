package hu.eszkozpont.reservation;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.user.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReservationMapper {
    ReservationResponse toResponse(Reservation reservation);
    ReservationSummaryResponse toSummary(Reservation reservation);
    EquipmentSummary toSummary(EquipmentItem equipment);
    UserSummary toSummary(AppUser user);
}
