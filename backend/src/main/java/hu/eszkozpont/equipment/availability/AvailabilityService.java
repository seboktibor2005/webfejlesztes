package hu.eszkozpont.equipment.availability;

import hu.eszkozpont.common.ApiException;
import hu.eszkozpont.equipment.*;
import hu.eszkozpont.loan.Loan;
import hu.eszkozpont.maintenance.*;
import hu.eszkozpont.reservation.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {
    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final MaintenanceRepository maintenance;
    private final Clock clock;

    public AvailabilityService(EquipmentRepository equipment, ReservationRepository reservations, MaintenanceRepository maintenance, Clock clock) {
        this.equipment = equipment; this.reservations = reservations; this.maintenance = maintenance; this.clock = clock;
    }

    public AvailabilityResponse get(long equipmentId, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "A naptárhoz növekvő, legfeljebb 90 napos időszakot adj meg.");
        }
        equipment.findById(equipmentId).filter(item -> item.getLifecycle() == EquipmentLifecycle.ACTIVE
                && item.getCategory().isActive() && item.getLocation().isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Az eszköz nem található a katalógusban."));
        var now = clock.instant();
        var intervals = Stream.concat(reservations.findAvailabilityIntervals(equipmentId, from, to).stream(),
                        maintenance.findAvailabilityIntervals(equipmentId, from, to).stream())
                .map(interval -> new BlockingInterval(interval.startsAt().isBefore(from) ? from : interval.startsAt(),
                        interval.endsAt().isAfter(to) ? to : interval.endsAt(), interval.kind()))
                .sorted(Comparator.comparing(BlockingInterval::startsAt).thenComparing(BlockingInterval::endsAt).thenComparing(BlockingInterval::kind))
                .toList();
        return new AvailabilityResponse(equipmentId, from, to, now, intervals, reservations.hasOverdueLoan(equipmentId, now),
                maintenance.hasOverdueMaintenance(equipmentId, now));
    }

    /** NOT EXISTS szűrés a lapozás és összesítés ELŐTT, eszközönkénti lekérdezések nélkül. */
    public Specification<EquipmentItem> availableDuring(Instant start, Instant end) {
        var now = clock.instant();
        ReservationPeriods.validate(start, end, now);
        return (root, query, cb) -> {
            var booked = query.subquery(Long.class);
            var reservation = booked.from(Reservation.class);
            booked.select(reservation.get("id")).where(cb.equal(reservation.get("equipment").get("id"), root.get("id")),
                    cb.equal(reservation.get("status"), ReservationStatus.APPROVED),
                    cb.lessThan(reservation.get("startsAt"), end), cb.greaterThan(reservation.get("endsAt"), start));

            var repairing = query.subquery(Long.class);
            var period = repairing.from(MaintenancePeriod.class);
            repairing.select(period.get("id")).where(cb.equal(period.get("equipment").get("id"), root.get("id")),
                    cb.equal(period.get("status"), MaintenanceStatus.OPEN),
                    cb.or(cb.and(cb.lessThan(period.get("startsAt"), end), cb.greaterThan(period.get("endsAt"), start)),
                            cb.and(cb.lessThanOrEqualTo(period.get("startsAt"), now), cb.lessThanOrEqualTo(period.get("endsAt"), now))));

            var late = query.subquery(Long.class);
            var loan = late.from(Loan.class);
            late.select(loan.get("id")).where(cb.equal(loan.get("equipment").get("id"), root.get("id")),
                    cb.isNull(loan.get("returnedAt")), cb.lessThan(loan.get("dueAt"), now));
            return cb.and(cb.not(cb.exists(booked)), cb.not(cb.exists(repairing)), cb.not(cb.exists(late)));
        };
    }
}
