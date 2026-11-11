package hu.eszkozpont.dashboard;

import java.time.Instant;

public record UserDashboardResponse(Instant checkedAt, long pendingCount, long activeLoanCount,
        DashboardAppointment nextPickup, DashboardAppointment nextReturn) {}
