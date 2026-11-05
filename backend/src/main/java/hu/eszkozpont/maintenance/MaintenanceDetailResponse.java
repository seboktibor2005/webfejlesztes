package hu.eszkozpont.maintenance;

import hu.eszkozpont.reservation.EquipmentSummary;
import hu.eszkozpont.user.UserSummary;
import java.time.Instant;

public record MaintenanceDetailResponse(Long id, EquipmentSummary equipment, UserSummary createdBy,
        Long loanId, Long reservationId, Instant startsAt, Instant endsAt, String reason,
        MaintenanceStatus status, Instant closedAt, boolean overdue, Instant createdAt, Instant updatedAt) {}
