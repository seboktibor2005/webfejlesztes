package hu.eszkozpont.equipment.reference;

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
import java.time.Clock;
import java.util.*;

@Service
@Validated
@Transactional(readOnly = true)
public class ReferenceDataService {
    private final CategoryRepository categories;
    private final LocationRepository locations;
    private final EquipmentRepository equipment;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final ReferenceDataMapper mapper;
    private final ReferenceDataLock lock;
    private final Clock clock;

    public ReferenceDataService(CategoryRepository categories, LocationRepository locations, EquipmentRepository equipment,
            LoanRepository loans, ReservationRepository reservations, UserRepository users, ReferenceDataMapper mapper,
            ReferenceDataLock lock, Clock clock) {
        this.categories = categories; this.locations = locations; this.equipment = equipment; this.loans = loans;
        this.reservations = reservations; this.users = users; this.mapper = mapper; this.lock = lock; this.clock = clock;
    }

    public PageResponse<CategoryAdminResponse> categories(long actorId, String q, Boolean active, int page, int size) {
        admin(actorId); validateList(q, page, size);
        Specification<EquipmentCategory> filter = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (q != null && !q.isBlank()) predicates.add(cb.like(cb.lower(root.get("name")), pattern(q), '\\'));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.of(categories.findAll(filter, PageRequest.of(page, size, Sort.by("name", "id"))).map(mapper::toResponse));
    }

    public PageResponse<LocationAdminResponse> locations(long actorId, String q, Boolean active, int page, int size) {
        admin(actorId); validateList(q, page, size);
        Specification<StorageLocation> filter = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (q != null && !q.isBlank()) predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern(q), '\\'), cb.like(cb.lower(root.get("code")), pattern(q), '\\')));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.of(locations.findAll(filter, PageRequest.of(page, size, Sort.by("code", "id"))).map(mapper::toResponse));
    }

    public CategoryAdminResponse category(long actorId, long id) { admin(actorId); return mapper.toResponse(category(id)); }
    public LocationAdminResponse location(long actorId, long id) { admin(actorId); return mapper.toResponse(location(id)); }

    @Transactional
    public CategoryAdminResponse createCategory(long actorId, @NotNull @Valid CategoryCreateRequest request) {
        lock.exclusive(); admin(actorId); uniqueCategory(request.name(), 0);
        return mapper.toResponse(categories.saveAndFlush(mapper.fromCreate(request)));
    }

    @Transactional
    public CategoryAdminResponse updateCategory(long actorId, long id, @NotNull @Valid CategoryUpdateRequest request) {
        lock.exclusive(); admin(actorId);
        var category = category(id); version(category.getVersion(), request.version()); uniqueCategory(request.name(), id);
        mapper.update(request, category); categories.flush();
        return mapper.toResponse(category);
    }

    @Transactional
    public void categoryActive(long actorId, long id, @NotNull @Valid ActiveRequest request) {
        lock.exclusive(); admin(actorId);
        var category = category(id); version(category.getVersion(), request.version());
        if (category.isActive() && !request.active()) ensureIdle(equipment.findIdsByCategory(id));
        category.setActive(request.active()); categories.flush();
    }

    @Transactional
    public void deleteCategory(long actorId, long id, long expectedVersion) {
        lock.exclusive(); admin(actorId);
        var category = category(id); version(category.getVersion(), expectedVersion);
        if (equipment.existsByCategory_Id(id)) throw conflict("REFERENCE_IN_USE", "A kategóriához eszköz tartozik. Törlés helyett archiválható, ha nincs élő igény vagy kölcsönzés.");
        categories.delete(category); categories.flush();
    }

    @Transactional
    public LocationAdminResponse createLocation(long actorId, @NotNull @Valid LocationCreateRequest request) {
        lock.exclusive(); admin(actorId); uniqueLocation(request.code(), 0);
        return mapper.toResponse(locations.saveAndFlush(mapper.fromCreate(request)));
    }

    @Transactional
    public LocationAdminResponse updateLocation(long actorId, long id, @NotNull @Valid LocationUpdateRequest request) {
        lock.exclusive(); admin(actorId);
        var location = location(id); version(location.getVersion(), request.version()); uniqueLocation(request.code(), id);
        mapper.update(request, location); locations.flush();
        return mapper.toResponse(location);
    }

    @Transactional
    public void locationActive(long actorId, long id, @NotNull @Valid ActiveRequest request) {
        lock.exclusive(); admin(actorId);
        var location = location(id); version(location.getVersion(), request.version());
        if (location.isActive() && !request.active()) ensureIdle(equipment.findIdsByLocation(id));
        location.setActive(request.active()); locations.flush();
    }

    @Transactional
    public void deleteLocation(long actorId, long id, long expectedVersion) {
        lock.exclusive(); admin(actorId);
        var location = location(id); version(location.getVersion(), expectedVersion);
        if (equipment.existsByLocation_Id(id)) throw conflict("REFERENCE_IN_USE", "A tárolási helyhez eszköz tartozik. Törlés helyett archiválható, ha nincs élő igény vagy kölcsönzés.");
        locations.delete(location); locations.flush();
    }

    private void ensureIdle(List<Long> itemIds) {
        // A kizáró törzsadatzár mellett nem kerülhet új eszköz a listába, és nem cserélhet kapcsolatot.
        // Azonos eszközsor-zárak, növekvő sorrendben: foglalással és átadással is sorosítunk.
        for (long id : itemIds) equipment.lockById(id).orElseThrow();
        var now = clock.instant();
        for (long id : itemIds) {
            if (loans.existsByEquipment_IdAndReturnedAtIsNull(id) || reservations.hasLiveReservation(id, now)) {
                throw conflict("REFERENCE_BUSY", "Az érintett eszközökhöz élő foglalási igény vagy nyitott kölcsönzés tartozik. Előbb ezeket rendezd.");
            }
        }
    }

    private EquipmentCategory category(long id) { return categories.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "A kategória nem található.")); }
    private StorageLocation location(long id) { return locations.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "A tárolási hely nem található.")); }
    private void uniqueCategory(String name, long exceptId) { if (categories.normalizedNameExists(name, exceptId)) throw conflict("CATEGORY_NAME_TAKEN", "Ez a kategórianév már használatban van."); }
    private void uniqueLocation(String code, long exceptId) { if (locations.normalizedCodeExists(code, exceptId)) throw conflict("LOCATION_CODE_TAKEN", "Ez a helyszínkód már használatban van."); }
    private void version(long actual, long expected) {
        if (expected < 0) throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Érvénytelen verzió.");
        if (actual != expected) throw conflict("STALE_VERSION", "A törzsadat időközben megváltozott. Töltsd be a friss adatokat.");
    }
    private void admin(long actorId) {
        var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jelentkezz be újra."));
        if (!actor.isEnabled() || actor.getRole() != Role.ADMIN) throw error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez aktív adminisztrátori jogosultság szükséges.");
    }
    private void validateList(String q, int page, int size) { if (page < 0 || size < 1 || size > 100 || q != null && q.length() > 120) throw error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Érvénytelen keresés vagy lapozás."); }
    private String pattern(String q) { return "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private ApiException conflict(String code, String message) { return error(HttpStatus.CONFLICT, code, message); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
