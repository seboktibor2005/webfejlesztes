package hu.eszkozpont.equipment;

import hu.eszkozpont.audit.*;
import hu.eszkozpont.common.*;
import hu.eszkozpont.loan.LoanRepository;
import hu.eszkozpont.reservation.ReservationRepository;
import hu.eszkozpont.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.Objects;

@Service
@Validated
@Transactional(readOnly = true)
public class EquipmentManagementService {
    private final EquipmentRepository equipment;
    private final CategoryRepository categories;
    private final LocationRepository locations;
    private final EquipmentMapper mapper;
    private final UserRepository users;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final AuditEventRepository events;
    private final AuditEventMapper eventMapper;
    private final Clock clock;
    private final ReferenceDataLock referenceLock;

    public EquipmentManagementService(EquipmentRepository equipment, CategoryRepository categories, LocationRepository locations,
            EquipmentMapper mapper, UserRepository users, LoanRepository loans, ReservationRepository reservations,
            AuditEventRepository events, AuditEventMapper eventMapper, Clock clock, ReferenceDataLock referenceLock) {
        this.equipment = equipment; this.categories = categories; this.locations = locations; this.mapper = mapper;
        this.users = users; this.loans = loans; this.reservations = reservations; this.events = events;
        this.eventMapper = eventMapper; this.clock = clock;
        this.referenceLock = referenceLock;
    }

    public EquipmentResponse get(long actorId, long id) {
        manager(actorId);
        return mapper.toResponse(equipment.findById(id).orElseThrow(this::notFound));
    }

    public PageResponse<AuditEventResponse> history(long actorId, long id, int page, int size) {
        get(actorId, id);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Érvénytelen lapozás.");
        return PageResponse.of(events.findAllByEquipment_IdOrderByOccurredAtAscIdAsc(id, PageRequest.of(page, size)).map(eventMapper::toResponse));
    }

    @Transactional
    public EquipmentResponse create(long actorId, @NotNull @Valid EquipmentCreateRequest request) {
        referenceLock.shared();
        var actor = manager(actorId);
        if (equipment.existsByInventoryCode(request.inventoryCode())) throw error(HttpStatus.CONFLICT, "INVENTORY_CODE_TAKEN", "Ez a leltári szám már használatban van.");
        var item = mapper.fromCreate(request);
        item.setCategory(category(request.categoryId()));
        item.setLocation(location(request.locationId()));
        equipment.saveAndFlush(item);
        event(item, actor, "EQUIPMENT_CREATED", null, "Eszköz felvéve a nyilvántartásba.");
        return mapper.toResponse(item);
    }

    @Transactional
    public EquipmentResponse update(long actorId, long id, @NotNull @Valid EquipmentUpdateRequest request) {
        referenceLock.shared();
        var item = equipment.lockById(id).orElseThrow(this::notFound);
        var actor = manager(actorId);
        version(item, request.version());
        if (!Objects.equals(item.getLocation().getId(), request.locationId()) && loans.existsByEquipment_IdAndReturnedAtIsNull(id)) {
            throw error(HttpStatus.CONFLICT, "LOCATION_LOCKED", "Kint lévő eszköz tárolási helye nem módosítható. Előbb rögzítsd a visszavételt.");
        }
        item.setCategory(category(request.categoryId()));
        item.setLocation(location(request.locationId()));
        mapper.update(request, item);
        equipment.flush();
        event(item, actor, "EQUIPMENT_UPDATED", item.getLifecycle().name(), "Az eszköz nyilvántartási adatai frissültek.");
        return mapper.toResponse(item);
    }

    @Transactional
    public EquipmentResponse retire(long actorId, long id, @NotNull @Valid EquipmentRetireRequest request) {
        referenceLock.shared();
        var item = equipment.lockById(id).orElseThrow(this::notFound);
        var actor = manager(actorId);
        version(item, request.version());
        if (item.getLifecycle() != EquipmentLifecycle.ACTIVE) throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Az eszköz már ki van vonva.");
        if (loans.existsByEquipment_IdAndReturnedAtIsNull(id) || reservations.hasLiveReservation(id, clock.instant())) {
            throw error(HttpStatus.CONFLICT, "EQUIPMENT_IN_USE", "Nyitott kölcsönzés vagy még élő foglalási igény mellett az eszköz nem vonható ki.");
        }
        item.setLifecycle(EquipmentLifecycle.RETIRED);
        equipment.flush();
        event(item, actor, "EQUIPMENT_RETIRED", "ACTIVE", request.reason());
        return mapper.toResponse(item);
    }

    private EquipmentCategory category(long id) {
        return categories.findById(id).filter(EquipmentCategory::isActive).orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Aktív, létező kategóriát válassz."));
    }
    private StorageLocation location(long id) {
        return locations.findById(id).filter(StorageLocation::isActive).orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "INVALID_LOCATION", "Aktív, létező tárolási helyet válassz."));
    }
    private void event(EquipmentItem item, AppUser actor, String type, String from, String note) {
        events.saveAndFlush(new AuditEvent(item, null, actor, type, from, item.getLifecycle().name(), note, clock.instant()));
    }
    private void version(EquipmentItem item, long version) {
        if (item.getVersion() != version) throw error(HttpStatus.CONFLICT, "STALE_VERSION", "Az eszköz időközben megváltozott. Töltsd be a friss adatokat.");
    }
    private AppUser manager(long id) {
        var actor = users.findById(id).filter(AppUser::isEnabled).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "A fiók nem aktív."));
        if (actor.getRole() == Role.USER) throw error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez eszközfelelősi jogosultság szükséges.");
        return actor;
    }
    private ApiException notFound() { return error(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Az eszköz nem található."); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
