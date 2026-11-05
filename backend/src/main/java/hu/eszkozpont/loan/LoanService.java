package hu.eszkozpont.loan;

import hu.eszkozpont.audit.*;
import hu.eszkozpont.common.*;
import hu.eszkozpont.equipment.*;
import hu.eszkozpont.maintenance.*;
import hu.eszkozpont.reservation.*;
import hu.eszkozpont.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;

@Service
@Validated
@Transactional(readOnly = true)
public class LoanService {
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final MaintenanceRepository maintenance;
    private final AuditEventRepository events;
    private final LoanMapper mapper;
    private final Clock clock;

    public LoanService(LoanRepository loans, ReservationRepository reservations, EquipmentRepository equipment,
            UserRepository users, MaintenanceRepository maintenance, AuditEventRepository events, LoanMapper mapper, Clock clock) {
        this.loans = loans;
        this.reservations = reservations;
        this.equipment = equipment;
        this.users = users;
        this.maintenance = maintenance;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse checkout(long actorId, long reservationId, @NotNull @Valid CheckoutRequest request) {
        var actor = manager(actorId);
        lockEquipment(reservations.findEquipmentId(reservationId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND", "A foglalás nem található.")));
        var reservation = reservations.findById(reservationId).orElseThrow();
        if (reservation.getVersion() != request.version()) throw conflict("STALE_VERSION", "A foglalás időközben megváltozott. Frissítsd az adatlapot.");
        if (reservation.getStatus() != ReservationStatus.APPROVED || reservations.hasLoan(reservationId)) {
            throw conflict("INVALID_TRANSITION", "Csak jóváhagyott, még át nem adott foglaláshoz adható ki eszköz.");
        }
        var now = clock.instant();
        if (now.isBefore(reservation.getStartsAt()) || !now.isBefore(reservation.getEndsAt())) {
            throw conflict("OUTSIDE_PICKUP_WINDOW", "Az eszköz csak a foglalási időszakon belül adható át.");
        }
        var item = reservation.getEquipment();
        if (!reservation.getRequester().isEnabled()) throw conflict("REQUESTER_DISABLED", "Letiltott felhasználónak nem adható át eszköz.");
        if (item.getLifecycle() != EquipmentLifecycle.ACTIVE || !item.getCategory().isActive() || !item.getLocation().isActive()
                || loans.existsByEquipment_IdAndReturnedAtIsNull(item.getId())
                || reservations.hasBlockingMaintenance(item.getId(), now, reservation.getEndsAt(), now)) {
            throw conflict("EQUIPMENT_UNAVAILABLE", "Az eszköz nem aktív, még nincs visszahozva, vagy karbantartás blokkolja az átadást.");
        }
        var loan = new Loan();
        loan.setReservation(reservation);
        loan.setEquipment(item);
        loan.setIssuedBy(actor);
        loan.setIssuedAt(now);
        loan.setDueAt(reservation.getEndsAt());
        loan.setIssueCondition(request.issueCondition());
        loans.saveAndFlush(loan);
        events.saveAndFlush(new AuditEvent(reservation, actor, "LOAN_ISSUED", "APPROVED", "APPROVED",
                request.issueCondition() == IssueCondition.GOOD ? "Átadáskor jó állapotú." : "Átadáskor használt, működőképes állapotú.", now));
        return mapper.toResponse(loan, now);
    }

    @Transactional
    public LoanResponse takeBack(long actorId, long id, @NotNull @Valid ReturnRequest request) {
        var actor = manager(actorId);
        var loan = locked(id);
        if (loan.getReturnedAt() != null || loan.getReservation().getStatus() != ReservationStatus.APPROVED) {
            throw conflict("INVALID_TRANSITION", "Ez a kölcsönzés már lezárult vagy nem vehető vissza.");
        }
        var now = clock.instant();
        if (now.isBefore(loan.getIssuedAt())) throw conflict("INVALID_TRANSITION", "A visszavétel nem előzheti meg az átadást.");
        if (request.condition() == ReturnCondition.DAMAGED) {
            if (request.note() == null || request.note().length() < 5 || request.maintenanceEndsAt() == null
                    || !request.maintenanceEndsAt().isAfter(now)) {
                throw error(HttpStatus.BAD_REQUEST, "DAMAGE_DETAILS_REQUIRED", "Sérülésnél legalább 5 karakteres leírás és jövőbeli tervezett karbantartásvég szükséges.");
            }
            var safety = new MaintenancePeriod();
            safety.setEquipment(loan.getEquipment());
            safety.setCreatedBy(actor);
            safety.setLoan(loan);
            safety.setStartsAt(now);
            safety.setEndsAt(request.maintenanceEndsAt());
            safety.setReason("Sérült visszavétel miatti biztonsági zárolás. A részletek a kölcsönzés jegyzőkönyvében találhatók.");
            maintenance.saveAndFlush(safety);
            loan.setMaintenance(safety);
            events.saveAndFlush(new AuditEvent(loan.getReservation(), actor, "MAINTENANCE_OPENED", null, "OPEN", request.note(), now));
        } else if (request.maintenanceEndsAt() != null) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Karbantartásvég csak sérült visszavételnél adható meg.");
        }
        loan.setReturnedAt(now);
        loan.setReturnedBy(actor);
        loan.setReturnCondition(request.condition());
        loan.setReturnNote(request.note());
        loan.getReservation().setStatus(ReservationStatus.COMPLETED);
        loans.flush();
        events.saveAndFlush(new AuditEvent(loan.getReservation(), actor, "LOAN_RETURNED", "APPROVED", "COMPLETED", request.note(), now));
        return mapper.toResponse(loan, now);
    }

    public LoanResponse get(long actorId, long id) {
        manager(actorId);
        return mapper.toResponse(loans.findById(id).orElseThrow(this::notFound), clock.instant());
    }

    public PageResponse<LoanResponse> list(long actorId, Boolean open, Boolean overdue, int page, int size) {
        manager(actorId);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Érvénytelen lapozás.");
        if (Boolean.TRUE.equals(overdue) && Boolean.FALSE.equals(open)) throw error(HttpStatus.BAD_REQUEST,
                "INVALID_FILTER", "Lezárt kölcsönzés nem lehet jelenleg késésben.");
        var now = clock.instant();
        Specification<Loan> filter = (root, query, cb) -> cb.conjunction();
        if (open != null) filter = filter.and((root, query, cb) -> open ? cb.isNull(root.get("returnedAt")) : cb.isNotNull(root.get("returnedAt")));
        if (overdue != null) filter = filter.and((root, query, cb) -> {
            var late = cb.and(cb.isNull(root.get("returnedAt")), cb.lessThan(root.get("dueAt"), now));
            return overdue ? late : cb.not(late);
        });
        return PageResponse.of(loans.findAll(filter, PageRequest.of(page, size, Sort.by("dueAt", "id")))
                .map(loan -> mapper.toResponse(loan, now)));
    }

    private Loan locked(long id) {
        lockEquipment(loans.findEquipmentId(id).orElseThrow(this::notFound));
        return loans.findById(id).orElseThrow(this::notFound);
    }

    private void lockEquipment(long id) {
        equipment.lockById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Az eszköz nem található."));
    }

    private AppUser manager(long actorId) {
        var actor = users.findById(actorId).filter(AppUser::isEnabled).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "A fiók nem aktív."));
        if (actor.getRole() == Role.USER) throw error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez a művelethez eszközfelelősi jogosultság szükséges.");
        return actor;
    }

    private ApiException notFound() { return error(HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND", "A kölcsönzés nem található."); }
    private ApiException conflict(String code, String message) { return error(HttpStatus.CONFLICT, code, message); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
