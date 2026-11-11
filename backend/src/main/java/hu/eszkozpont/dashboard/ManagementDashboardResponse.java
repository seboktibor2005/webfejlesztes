package hu.eszkozpont.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ManagementDashboardResponse(Instant checkedAt, LocalDate date, LocalDate historyFrom,
        long pendingCount, long todayPickupCount, long todayReturnCount, long overdueLoanCount,
        long maintenanceCount, List<CategoryLoanCount> loansByCategory) {}
