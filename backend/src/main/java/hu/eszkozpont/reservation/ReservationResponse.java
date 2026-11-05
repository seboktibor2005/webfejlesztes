package hu.eszkozpont.reservation;

import hu.eszkozpont.user.UserSummary;
import java.time.Instant;

public record ReservationResponse(Long id, EquipmentSummary equipment, UserSummary requester,
        Instant startsAt, Instant endsAt, String purpose, ReservationStatus status,
        UserSummary decidedBy, Instant decidedAt, String decisionReason,
        Instant createdAt, Instant updatedAt, long version) {}
