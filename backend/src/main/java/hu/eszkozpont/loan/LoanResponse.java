package hu.eszkozpont.loan;

import hu.eszkozpont.maintenance.MaintenanceResponse;
import hu.eszkozpont.reservation.EquipmentSummary;
import hu.eszkozpont.user.UserSummary;
import java.time.Instant;

public record LoanResponse(Long id, Long reservationId, EquipmentSummary equipment, UserSummary requester,
        UserSummary issuedBy, Instant issuedAt, Instant dueAt, UserSummary returnedBy, Instant returnedAt,
        IssueCondition issueCondition, ReturnCondition returnCondition, String returnNote,
        boolean overdue, boolean returnedLate, long overdueMinutes, MaintenanceResponse maintenance) {}
