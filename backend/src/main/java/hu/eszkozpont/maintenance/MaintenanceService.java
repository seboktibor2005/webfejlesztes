package hu.eszkozpont.maintenance;

import hu.eszkozpont.audit.*;
import hu.eszkozpont.common.*;
import hu.eszkozpont.equipment.*;
import hu.eszkozpont.loan.LoanRepository;
import hu.eszkozpont.reservation.ReservationRepository;
import hu.eszkozpont.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.*;

@Service
@Validated
@Transactional(readOnly = true)
public class MaintenanceService {
    private final MaintenanceRepository periods;
    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final LoanRepository loans;
    private final UserRepository users;
    private final AuditEventRepository events;
    private final MaintenanceMapper mapper;
    private final Clock clock;

    public MaintenanceService(MaintenanceRepository periods, EquipmentRepository equipment, ReservationRepository reservations,
            LoanRepository loans, UserRepository users, AuditEventRepository events, MaintenanceMapper mapper, Clock clock) {
        this.periods = periods; this.equipment = equipment; this.reservations = reservations; this.loans = loans;
        this.users = users; this.events = events; this.mapper = mapper; this.clock = clock;
    }

    public PageResponse<MaintenanceDetailResponse> list(long actorId, Long equipmentId, MaintenanceStatus status, int page, int size) {
        manager(actorId);
        if (page < 0 || size < 1 || size > 100 || equipmentId != null && equipmentId < 1) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Érvénytelen szűrés vagy lapozás.");
        }
        Specification<MaintenancePeriod> filter = (root, query, cb) -> cb.conjunction();
        if (equipmentId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("equipment").get("id"), equipmentId));
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        var now = clock.instant();
        return PageResponse.of(periods.findAll(filter, PageRequest.of(page, size, Sort.by("startsAt", "id")))
                .map(period -> mapper.toDetail(period, now)));
    }

    public MaintenanceDetailResponse get(long actorId, long id) {
        manager(actorId);
        return mapper.toDetail(periods.findById(id).orElseThrow(this::notFound), clock.instant());
    }

    @Transactional
    public MaintenanceDetailResponse create(long actorId, @NotNull @Valid MaintenanceCreateRequest request) {
        var item = equipment.lockById(request.equipmentId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Az eszköz nem található."));
        var actor = manager(actorId);
        var now = clock.instant();
        if (!request.startsAt().isBefore(request.endsAt()) || !request.endsAt().isAfter(now)
                || !minute(request.startsAt()) || !minute(request.endsAt())) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "Egész perces, helyes időszakot adj meg, jövőbeli befejezéssel.");
        }
        if (item.getLifecycle() != EquipmentLifecycle.ACTIVE) throw error(HttpStatus.CONFLICT, "EQUIPMENT_UNAVAILABLE", "Kivont eszközre nem rögzíthető új karbantartás.");
        if (reservations.hasApprovedMaintenanceConflict(item.getId(), request.startsAt(), request.endsAt(), now)
                || loans.blocksMaintenance(item.getId(), request.startsAt(), now)
                || reservations.hasBlockingMaintenance(item.getId(), request.startsAt(), request.endsAt(), now)) {
            throw error(HttpStatus.CONFLICT, "MAINTENANCE_CONFLICT", "Az időszakot jóváhagyott foglalás, nyitott kölcsönzés vagy karbantartás blokkolja. Előbb rendezd az ütközést.");
        }
        var period = new MaintenancePeriod();
        period.setEquipment(item); period.setCreatedBy(actor);
        period.setStartsAt(request.startsAt()); period.setEndsAt(request.endsAt()); period.setReason(request.reason());
        periods.saveAndFlush(period);
        event(period, actor, "MAINTENANCE_OPENED", null, request.reason());
        return mapper.toDetail(period, now);
    }

    @Transactional
    public MaintenanceResponse close(long actorId, long id, @NotNull @Valid MaintenanceCloseRequest request) {
        var period = locked(id);
        var actor = manager(actorId);
        var now = clock.instant();
        requireOpen(period);
        if (now.isBefore(period.getStartsAt())) throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Még el nem kezdett karbantartás nem zárható le.");
        period.setStatus(MaintenanceStatus.CLOSED); period.setClosedAt(now);
        periods.flush();
        event(period, actor, "MAINTENANCE_CLOSED", "OPEN", request.note());
        return mapper.toResponse(period);
    }

    @Transactional
    public MaintenanceDetailResponse cancel(long actorId, long id, @NotNull @Valid MaintenanceCancelRequest request) {
        var period = locked(id);
        var actor = manager(actorId);
        requireOpen(period);
        if (!period.getStartsAt().isAfter(clock.instant())) throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Elindult karbantartás nem mondható le; a javítás után zárd le.");
        period.setStatus(MaintenanceStatus.CANCELLED);
        periods.flush();
        event(period, actor, "MAINTENANCE_CANCELLED", "OPEN", request.reason());
        return mapper.toDetail(period, clock.instant());
    }

    private MaintenancePeriod locked(long id) {
        long itemId = periods.findEquipmentId(id).orElseThrow(this::notFound);
        equipment.lockById(itemId).orElseThrow();
        return periods.findById(id).orElseThrow(this::notFound);
    }
    private boolean minute(Instant instant) { return instant.getNano() == 0 && instant.getEpochSecond() % 60 == 0; }
    private void requireOpen(MaintenancePeriod period) {
        if (period.getStatus() != MaintenanceStatus.OPEN) throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Csak nyitott karbantartáson végezhető ez a művelet.");
    }
    private void event(MaintenancePeriod period, AppUser actor, String type, String from, String note) {
        events.saveAndFlush(new AuditEvent(period.getEquipment(), period.getLoan() == null ? null : period.getLoan().getReservation(),
                actor, type, from, period.getStatus().name(), note, clock.instant()));
    }
    private AppUser manager(long id) {
        var actor = users.findById(id).filter(AppUser::isEnabled).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "A fiók nem aktív."));
        if (actor.getRole() == Role.USER) throw error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez eszközfelelősi jogosultság szükséges.");
        return actor;
    }
    private ApiException notFound() { return error(HttpStatus.NOT_FOUND, "MAINTENANCE_NOT_FOUND", "A karbantartás nem található."); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
