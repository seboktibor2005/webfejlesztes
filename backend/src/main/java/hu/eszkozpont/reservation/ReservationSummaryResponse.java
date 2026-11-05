package hu.eszkozpont.reservation;

import hu.eszkozpont.user.UserSummary;
import java.time.Instant;

public record ReservationSummaryResponse(Long id, EquipmentSummary equipment, UserSummary requester,
        Instant startsAt, Instant endsAt, ReservationStatus status, long version) {}
