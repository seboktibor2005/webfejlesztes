package hu.eszkozpont.reservation;

import hu.eszkozpont.audit.*;
import hu.eszkozpont.equipment.EquipmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationExpiryService {
    private final ReservationRepository reservations;
    private final EquipmentRepository equipment;
    private final AuditEventRepository events;
    private final Clock clock;

    public ReservationExpiryService(ReservationRepository reservations, EquipmentRepository equipment, AuditEventRepository events, Clock clock) {
        this.reservations = reservations; this.equipment = equipment; this.events = events; this.clock = clock;
    }

    public List<Long> candidates() { return reservations.findExpiryCandidates(clock.instant(), PageRequest.of(0, 100)); }

    @Transactional
    public boolean expire(long id) {
        var itemId = reservations.findEquipmentId(id);
        if (itemId.isEmpty() || equipment.lockById(itemId.get()).isEmpty()) return false;
        var reservation = reservations.findById(id).orElse(null);
        if (reservation == null) return false;
        var now = clock.instant();
        var previous = reservation.getStatus();
        if ((previous != ReservationStatus.REQUESTED && previous != ReservationStatus.APPROVED)
                || reservation.getEndsAt().isAfter(now) || reservations.hasLoan(id)) return false;
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservations.flush();
        events.saveAndFlush(new AuditEvent(reservation, null, "RESERVATION_EXPIRED", previous.name(), "EXPIRED",
                "Az igény időszaka átvétel nélkül véget ért.", now));
        return true;
    }
}
