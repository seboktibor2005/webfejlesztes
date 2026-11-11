package hu.eszkozpont.dashboard;

import hu.eszkozpont.loan.LoanRepository;
import hu.eszkozpont.maintenance.MaintenanceRepository;
import hu.eszkozpont.maintenance.MaintenanceStatus;
import hu.eszkozpont.reservation.ReservationRepository;
import hu.eszkozpont.reservation.ReservationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DashboardService {
    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");
    private final ReservationRepository reservations;
    private final LoanRepository loans;
    private final MaintenanceRepository maintenance;
    private final Clock clock;

    public DashboardService(ReservationRepository reservations, LoanRepository loans,
            MaintenanceRepository maintenance, Clock clock) {
        this.reservations = reservations;
        this.loans = loans;
        this.maintenance = maintenance;
        this.clock = clock;
    }

    public UserDashboardResponse mine(long userId) {
        var now = clock.instant();
        var first = PageRequest.of(0, 1);
        return new UserDashboardResponse(now,
                reservations.countByRequester_IdAndStatusAndStartsAtAfter(userId, ReservationStatus.REQUESTED, now),
                loans.countByReservation_Requester_IdAndReturnedAtIsNull(userId),
                reservations.findDashboardPickups(userId, now, first).stream().findFirst().orElse(null),
                loans.findDashboardReturns(userId, first).stream().findFirst().orElse(null));
    }

    public ManagementDashboardResponse management() {
        var now = clock.instant();
        var today = now.atZone(BUDAPEST).toLocalDate();
        var dayStart = today.atStartOfDay(BUDAPEST).toInstant();
        var dayEnd = today.plusDays(1).atStartOfDay(BUDAPEST).toInstant();
        var historyFrom = today.minusDays(29);
        var historyStart = historyFrom.atStartOfDay(BUDAPEST).toInstant();
        return new ManagementDashboardResponse(now, today, historyFrom,
                reservations.countByStatusAndStartsAtAfter(ReservationStatus.REQUESTED, now),
                reservations.countDashboardPickups(dayStart, dayEnd, now),
                loans.countByReturnedAtIsNullAndDueAtGreaterThanEqualAndDueAtLessThan(dayStart, dayEnd),
                loans.countByReturnedAtIsNullAndDueAtLessThan(now),
                maintenance.countByStatus(MaintenanceStatus.OPEN),
                loans.countIssuedByCategory(historyStart, now));
    }
}
