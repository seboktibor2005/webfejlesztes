package hu.eszkozpont.reservation;

import hu.eszkozpont.audit.*;
import hu.eszkozpont.common.*;
import hu.eszkozpont.equipment.*;
import hu.eszkozpont.user.*;
import hu.eszkozpont.loan.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.*;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional(readOnly = true)
public class ReservationService {
    private final ReservationRepository reservations;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final ReservationMapper mapper;
    private final AuditEventRepository events;
    private final AuditEventMapper eventMapper;
    private final Clock clock;
    private final LoanRepository loans;
    private final LoanMapper loanMapper;

    public ReservationService(ReservationRepository reservations, EquipmentRepository equipment,
            UserRepository users, ReservationMapper mapper, AuditEventRepository events,
            AuditEventMapper eventMapper, Clock clock, LoanRepository loans, LoanMapper loanMapper) {
        this.reservations = reservations;
        this.equipment = equipment;
        this.users = users;
        this.mapper = mapper;
        this.events = events;
        this.eventMapper = eventMapper;
        this.clock = clock;
        this.loans = loans;
        this.loanMapper = loanMapper;
    }

    @Transactional
    public ReservationResponse create(long actorId, @NotNull @Valid ReservationCreateRequest request) {
        var item = lockEquipment(request.equipmentId());
        var actor = activeUser(actorId);
        var now = clock.instant();
        ReservationPeriods.validate(request.startsAt(), request.endsAt(), now);
        checkAvailable(item, request.startsAt(), request.endsAt(), now);
        var reservation = new Reservation();
        reservation.setEquipment(item);
        reservation.setRequester(actor);
        reservation.setStartsAt(request.startsAt());
        reservation.setEndsAt(request.endsAt());
        reservation.setPurpose(request.purpose());
        reservations.saveAndFlush(reservation);
        recordEvent(reservation, actor, "RESERVATION_CREATED", null, null, now);
        return response(reservation);
    }

    public PageResponse<ReservationSummaryResponse> mine(long actorId, ReservationStatus status,
            int page, int size, String sort) {
        Specification<Reservation> owned = (root, query, cb) -> cb.equal(root.get("requester").get("id"), actorId);
        return list(owned, status, page, size, sort);
    }

    public PageResponse<ReservationSummaryResponse> management(long actorId, ReservationStatus status,
            int page, int size, String sort) {
        requireManager(activeUser(actorId));
        return list((root, query, cb) -> cb.conjunction(), status, page, size, sort);
    }

    public ReservationResponse get(long actorId, long id) {
        return response(readable(actorId, id));
    }

    public PageResponse<AuditEventResponse> history(long actorId, long id, int page, int size) {
        readable(actorId, id);
        validatePage(page, size);
        return PageResponse.of(events.findAllByReservation_IdOrderByOccurredAtAscIdAsc(id, PageRequest.of(page, size))
                .map(eventMapper::toResponse));
    }

    @Transactional
    public ReservationResponse approve(long actorId, long id, @NotNull @Valid ApproveRequest request) {
        var reservation = locked(id);
        var actor = activeUser(actorId);
        requireManager(actor);
        checkVersion(reservation, request.version());
        requireRequested(reservation);
        if (Objects.equals(reservation.getRequester().getId(), actorId)) {
            throw error(HttpStatus.FORBIDDEN, "SELF_APPROVAL", "Saját igényedet nem hagyhatod jóvá.");
        }
        var now = clock.instant();
        if (!reservation.getStartsAt().isAfter(now)) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "A kezdési időpont elmúlt; ez az igény már nem hagyható jóvá.");
        }
        if (!reservation.getRequester().isEnabled()) {
            throw error(HttpStatus.CONFLICT, "REQUESTER_DISABLED", "Letiltott felhasználó igénye nem hagyható jóvá.");
        }
        checkAvailable(reservation.getEquipment(), reservation.getStartsAt(), reservation.getEndsAt(), now);
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setDecidedBy(actor);
        reservation.setDecidedAt(now);
        return finish(reservation, actor, "RESERVATION_APPROVED", ReservationStatus.REQUESTED, null, now);
    }

    @Transactional
    public ReservationResponse reject(long actorId, long id, @NotNull @Valid RejectRequest request) {
        var reservation = locked(id);
        var actor = activeUser(actorId);
        requireManager(actor);
        checkVersion(reservation, request.version());
        requireRequested(reservation);
        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setDecidedBy(actor);
        reservation.setDecidedAt(clock.instant());
        reservation.setDecisionReason(request.reason());
        return finish(reservation, actor, "RESERVATION_REJECTED", ReservationStatus.REQUESTED,
                request.reason(), reservation.getDecidedAt());
    }

    @Transactional
    public ReservationResponse cancel(long actorId, long id, @NotNull @Valid CancelRequest request, boolean management) {
        var reservation = locked(id);
        var actor = activeUser(actorId);
        if (management) {
            requireManager(actor);
            if (request.reason() == null || request.reason().length() < 5) {
                throw error(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "A kezelői lemondáshoz legalább 5 karakteres indok szükséges.");
            }
        } else if (!Objects.equals(reservation.getRequester().getId(), actorId)) {
            throw notFound();
        }
        checkVersion(reservation, request.version());
        var previous = reservation.getStatus();
        if (previous != ReservationStatus.REQUESTED && previous != ReservationStatus.APPROVED) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Ez a foglalás már nem vonható vissza.");
        }
        var now = clock.instant();
        if (reservations.hasLoan(id) || !management && !reservation.getStartsAt().isAfter(now)) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Átvett vagy már elkezdődött foglalást nem vonhatsz vissza.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        return finish(reservation, actor, "RESERVATION_CANCELLED", previous, request.reason(), now);
    }

    private PageResponse<ReservationSummaryResponse> list(Specification<Reservation> scope,
            ReservationStatus status, int page, int size, String sort) {
        validatePage(page, size);
        var order = switch (sort) {
            case "createdAt,desc" -> Sort.by("createdAt").descending();
            case "startsAt,asc" -> Sort.by("startsAt").ascending();
            case "startsAt,desc" -> Sort.by("startsAt").descending();
            default -> throw error(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Ismeretlen rendezési feltétel.");
        };
        if (status != null) scope = scope.and((root, query, cb) -> cb.equal(root.get("status"), status));
        var result = reservations.findAll(scope, PageRequest.of(page, size, order.and(Sort.by("id"))));
        var ids = result.getContent().stream().map(Reservation::getId).toList();
        var now = clock.instant();
        Map<Long, Loan> byReservation = ids.isEmpty() ? Map.of() : loans.findAllByReservation_IdIn(ids).stream()
                .collect(Collectors.toMap(loan -> loan.getReservation().getId(), Function.identity()));
        Set<Long> risks = ids.isEmpty() ? Set.of() : Set.copyOf(reservations.findAtRiskIds(ids, now));
        return PageResponse.of(result.map(reservation -> {
            var loan = byReservation.get(reservation.getId());
            boolean open = loan != null && loan.getReturnedAt() == null;
            return mapper.toSummary(reservation, open, open && loan.getDueAt().isBefore(now), risks.contains(reservation.getId()));
        }));
    }

    private Reservation readable(long actorId, long id) {
        var actor = activeUser(actorId);
        var reservation = reservations.findById(id).orElseThrow(this::notFound);
        if (actor.getRole() == Role.USER && !Objects.equals(reservation.getRequester().getId(), actorId)) throw notFound();
        return reservation;
    }

    private Reservation locked(long id) {
        // Először csak skalár azonosítót olvasunk: a foglalás állapota a sorzár UTÁN töltődik be.
        long equipmentId = reservations.findEquipmentId(id).orElseThrow(this::notFound);
        lockEquipment(equipmentId);
        return reservations.findById(id).orElseThrow(this::notFound);
    }

    private EquipmentItem lockEquipment(long id) {
        return equipment.lockById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "EQUIPMENT_NOT_FOUND", "Az eszköz nem található."));
    }

    private void checkAvailable(EquipmentItem item, Instant start, Instant end, Instant now) {
        if (item.getLifecycle() != EquipmentLifecycle.ACTIVE || !item.getCategory().isActive() || !item.getLocation().isActive()) {
            throw error(HttpStatus.CONFLICT, "EQUIPMENT_UNAVAILABLE", "Ez az eszköz jelenleg nem igényelhető.");
        }
        if (reservations.existsByEquipment_IdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                item.getId(), ReservationStatus.APPROVED, end, start)) {
            throw error(HttpStatus.CONFLICT, "RESERVATION_CONFLICT", "Az eszközre már van jóváhagyott foglalás ebben az időszakban.");
        }
        if (reservations.hasBlockingMaintenance(item.getId(), start, end, now) || reservations.hasOverdueLoan(item.getId(), now)) {
            throw error(HttpStatus.CONFLICT, "EQUIPMENT_UNAVAILABLE", "Az eszköz karbantartás vagy késedelmes visszahozás miatt nem foglalható.");
        }
    }

    private ReservationResponse finish(Reservation reservation, AppUser actor, String event,
            ReservationStatus previous, String note, Instant now) {
        reservations.flush();
        recordEvent(reservation, actor, event, previous, note, now);
        return response(reservation);
    }

    private ReservationResponse response(Reservation reservation) {
        var now = clock.instant();
        var loan = loans.findByReservation_Id(reservation.getId()).map(value -> loanMapper.toResponse(value, now)).orElse(null);
        boolean atRisk = !reservations.findAtRiskIds(List.of(reservation.getId()), now).isEmpty();
        return mapper.toResponse(reservation, loan, atRisk);
    }

    private void recordEvent(Reservation reservation, AppUser actor, String event,
            ReservationStatus previous, String note, Instant now) {
        events.saveAndFlush(new AuditEvent(reservation, actor, event,
                previous == null ? null : previous.name(), reservation.getStatus().name(), note, now));
    }

    private void checkVersion(Reservation reservation, long version) {
        if (reservation.getVersion() != version) {
            throw error(HttpStatus.CONFLICT, "STALE_VERSION", "A foglalás időközben megváltozott. Frissítsd az adatlapot.");
        }
    }

    private void requireRequested(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Csak elbírálásra váró igényről hozható döntés.");
        }
    }

    private AppUser activeUser(long id) {
        return users.findById(id).filter(AppUser::isEnabled).orElseThrow(() ->
                error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "A fiók nem aktív. Jelentkezz be újra."));
    }

    private void requireManager(AppUser user) {
        if (user.getRole() == Role.USER) throw error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez a művelethez eszközfelelősi jogosultság szükséges.");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Érvénytelen lapozás.");
    }

    private ApiException notFound() { return error(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "A foglalás nem található."); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
