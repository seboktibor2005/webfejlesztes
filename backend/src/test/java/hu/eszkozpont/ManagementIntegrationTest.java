package hu.eszkozpont;

import hu.eszkozpont.user.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Az üzleti óra léptetéséhez csak a teszt tokenje él tovább; a termék beállítása változatlan.
@SpringBootTest(properties = {"JWT_SECRET=management-tests-only-secret-at-least-32-bytes", "app.demo-data=false", "app.jwt.ttl=PT24H"})
@AutoConfigureMockMvc
@Import(ManagementIntegrationTest.TimeConfiguration.class)
class ManagementIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private static final Instant START = NOW.plus(2, ChronoUnit.HOURS);
    private static final Instant END = START.plus(2, ChronoUnit.HOURS);
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static class TestClock extends Clock {
        private volatile Instant value = NOW;
        void set(Instant instant) { value = instant; }
        @Override public Instant instant() { return value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TestClock clock;
    private String ownerToken, managerToken, adminToken, hash;
    private long ownerId, adminId, itemId, categoryId, locationId, secondLocationId;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        hash = passwords.encode("EszkozPont2026!");
        ownerId = user("anna@test.example", Role.USER);
        user("manager@test.example", Role.MANAGER);
        adminId = user("admin@test.example", Role.ADMIN);
        ownerToken = login("anna@test.example"); managerToken = login("manager@test.example"); adminToken = login("admin@test.example");
        categoryId = jdbc.queryForObject("INSERT INTO equipment_category(name) VALUES ('Kamera') RETURNING id", Long.class);
        locationId = location("MEDIA"); secondLocationId = location("LAB");
        itemId = equipment("FOTO-001");
    }

    @Test void equipmentCreationNormalizesCodeUsesDtoAndRecordsHistory() throws Exception {
        var result = postJson("/api/management/equipment", managerToken, createEquipment("  foto-new  "))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.inventoryCode").value("FOTO-NEW"))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.category.id").value(categoryId));
        long id = id(result);
        getJson(result.andReturn().getResponse().getHeader("Location"), managerToken).andExpect(status().isOk());
        getJson("/api/management/equipment/" + id + "/events", managerToken).andExpect(jsonPath("$.content[0].eventType").value("EQUIPMENT_CREATED"));
    }

    @Test void duplicateInvalidAndInactiveEquipmentReferencesAreRejected() throws Exception {
        postJson("/api/management/equipment", managerToken, createEquipment("foto-001")).andExpect(status().isConflict());
        postJson("/api/management/equipment", managerToken, createEquipment("bad code")).andExpect(status().isBadRequest());
        var body = new HashMap<String, Object>(createEquipment("NEW-1")); body.put("categoryId", 99999);
        postJson("/api/management/equipment", managerToken, body).andExpect(status().isBadRequest());
        jdbc.update("update equipment_category set active=false where id=?", categoryId);
        postJson("/api/management/equipment", managerToken, createEquipment("NEW-1")).andExpect(status().isBadRequest());
        assertThat(count("equipment_item")).isEqualTo(1);
    }

    @Test void equipmentUpdateUsesVersionAndKeepsImmutableCodeAndLifecycle() throws Exception {
        putEquipment(updateEquipment(0, locationId, "Új név")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        putEquipment(updateEquipment(0, locationId, "Elavult név")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        var body = new HashMap<String, Object>(updateEquipment(1, locationId, "Új név")); body.put("inventoryCode", "OTHER");
        putEquipment(body).andExpect(status().isBadRequest());
        body.remove("inventoryCode"); body.put("lifecycle", "RETIRED"); putEquipment(body).andExpect(status().isBadRequest());
        getJson("/api/equipment/" + itemId, ownerToken).andExpect(jsonPath("$.name").value("Új név"));
    }

    @Test void storageCannotChangeWhileLoanIsOpenButMetadataCan() throws Exception {
        long reservation = reservation(true);
        clock.set(START);
        postJson("/api/management/reservations/" + reservation + "/checkout", managerToken, Map.of("version", 1, "issueCondition", "GOOD")).andExpect(status().isCreated());
        putEquipment(updateEquipment(0, secondLocationId, "Másik helyen")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_LOCKED"));
        putEquipment(updateEquipment(0, locationId, "Pontosított név")).andExpect(status().isOk());
        retire(1).andExpect(status().isConflict());
    }

    @Test void retirementKeepsHistoryAndHidesEquipmentFromStudentCatalogue() throws Exception {
        retire(0).andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("RETIRED"));
        getJson("/api/equipment/" + itemId, ownerToken).andExpect(status().isNotFound());
        getJson("/api/management/equipment/" + itemId, managerToken).andExpect(status().isOk());
        getJson("/api/management/equipment/" + itemId + "/events", adminToken).andExpect(jsonPath("$.content[0].eventType").value("EQUIPMENT_RETIRED"));
        retire(1).andExpect(status().isConflict());
        assertThat(count("equipment_item")).isEqualTo(1);
    }

    @Test void livePendingOrApprovedReservationsPreventRetirement() throws Exception {
        long reservation = reservation(false);
        retire(0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EQUIPMENT_IN_USE"));
        postJson("/api/management/reservations/" + reservation + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isOk());
        retire(0).andExpect(status().isConflict());
        clock.set(END);
        retire(0).andExpect(status().isOk());
    }

    @Test void maintenanceCreationIsPagedFilterableAndAudited() throws Exception {
        long id = id(maintenance(START, END).andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy.email").doesNotExist()));
        getJson("/api/management/maintenance/" + id, managerToken).andExpect(jsonPath("$.equipment.id").value(itemId));
        getJson("/api/management/maintenance?equipmentId=" + itemId + "&status=OPEN&size=1", managerToken).andExpect(jsonPath("$.page.totalElements").value(1));
        getJson("/api/management/maintenance?status=CLOSED", managerToken).andExpect(jsonPath("$.content").isEmpty());
        getJson("/api/management/equipment/" + itemId + "/events", managerToken).andExpect(jsonPath("$.content[0].eventType").value("MAINTENANCE_OPENED"));
    }

    @Test void maintenanceRejectsInvalidPeriodsInputsAndRetiredEquipment() throws Exception {
        for (var pair : List.of(new Instant[]{START, START}, new Instant[]{START, NOW}, new Instant[]{START.plusSeconds(1), END})) {
            maintenance(pair[0], pair[1]).andExpect(status().isBadRequest());
        }
        postJson("/api/management/maintenance", managerToken, Map.of()).andExpect(status().isBadRequest());
        retire(0).andExpect(status().isOk());
        maintenance(START, END).andExpect(status().isConflict());
        for (String query : List.of("size=101", "page=-1", "equipmentId=-1", "status=FAKE")) {
            getJson("/api/management/maintenance?" + query, managerToken).andExpect(status().isBadRequest());
        }
    }

    @Test void approvedReservationBlocksManualMaintenanceButPendingDoesNot() throws Exception {
        long reservation = reservation(false);
        long maintenance = id(maintenance(START, END).andExpect(status().isCreated()));
        postJson("/api/management/reservations/" + reservation + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isConflict());
        cancelMaintenance(maintenance).andExpect(status().isOk());
        postJson("/api/management/reservations/" + reservation + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isOk());
        maintenance(START, END).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAINTENANCE_CONFLICT"));
        maintenance(END, END.plusSeconds(3600)).andExpect(status().isCreated());
    }

    @Test void overlappingAndOverdueMaintenanceCannotBeDuplicated() throws Exception {
        long id = id(maintenance(START, END).andExpect(status().isCreated()));
        maintenance(START.plusSeconds(3600), END.plusSeconds(3600)).andExpect(status().isConflict());
        clock.set(END.plusSeconds(60));
        getJson("/api/management/maintenance/" + id, managerToken).andExpect(jsonPath("$.overdue").value(true));
        maintenance(END.plusSeconds(3600), END.plusSeconds(7200)).andExpect(status().isConflict());
        closeMaintenance(id).andExpect(status().isOk());
        maintenance(END.plusSeconds(3600), END.plusSeconds(7200)).andExpect(status().isCreated());
    }

    @Test void futureMaintenanceCanBeCancelledButStartedMaintenanceMustBeClosed() throws Exception {
        long first = id(maintenance(START, END).andExpect(status().isCreated()));
        closeMaintenance(first).andExpect(status().isConflict());
        postJson("/api/management/maintenance/" + first + "/cancel", managerToken, Map.of("reason", "  ")).andExpect(status().isBadRequest());
        cancelMaintenance(first).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        cancelMaintenance(first).andExpect(status().isConflict());
        long next = id(maintenance(START, END).andExpect(status().isCreated()));
        clock.set(START);
        cancelMaintenance(next).andExpect(status().isConflict());
        closeMaintenance(next).andExpect(status().isOk());
        closeMaintenance(next).andExpect(status().isConflict());
    }

    @Test void openLoanBlocksMaintenanceDuringLoanAndOverdueLoanBlocksFutureMaintenance() throws Exception {
        long reservation = reservation(true); clock.set(START);
        postJson("/api/management/reservations/" + reservation + "/checkout", managerToken, Map.of("version", 1, "issueCondition", "GOOD")).andExpect(status().isCreated());
        maintenance(START, END).andExpect(status().isConflict());
        clock.set(END);
        maintenance(END, END.plusSeconds(3600)).andExpect(status().isConflict());
        clock.set(END.plusSeconds(1));
        maintenance(END.plusSeconds(3600), END.plusSeconds(7200)).andExpect(status().isConflict());
    }

    @Test void unknownManagementRecordsAndWrongRolesAreRejected() throws Exception {
        for (String path : List.of("/api/management/equipment/" + itemId, "/api/management/equipment/" + itemId + "/events", "/api/management/maintenance", "/api/admin/users/" + ownerId)) {
            getJson(path, ownerToken).andExpect(status().isForbidden());
        }
        getJson("/api/admin/users/" + ownerId, managerToken).andExpect(status().isForbidden());
        postJson("/api/management/equipment", ownerToken, createEquipment("NEW")).andExpect(status().isForbidden());
        mvc.perform(get("/api/management/maintenance")).andExpect(status().isUnauthorized());
        getJson("/api/management/maintenance/99999", managerToken).andExpect(status().isNotFound());
        getJson("/api/management/equipment/99999", managerToken).andExpect(status().isNotFound());
        getJson("/api/admin/users/99999", adminToken).andExpect(status().isNotFound());
    }

    @Test void userUpdateChangesNameAndRoleAndRevokesOldTokens() throws Exception {
        updateUser(ownerId, adminToken, "Pontosított Név", "MANAGER", true, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        getJson("/api/me", ownerToken).andExpect(status().isUnauthorized());
        String fresh = login("anna@test.example");
        getJson("/api/me", fresh).andExpect(jsonPath("$.displayName").value("Pontosított Név"));
        getJson("/api/management/maintenance", fresh).andExpect(status().isOk());
        getJson("/api/admin/users", fresh).andExpect(status().isForbidden());
    }

    @Test void disablingAndReenablingDoesNotReviveTheOldToken() throws Exception {
        updateUser(ownerId, adminToken, "Teszt Felhasználó", "USER", false, 0).andExpect(status().isOk());
        getJson("/api/me", ownerToken).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", "anna@test.example", "password", "EszkozPont2026!"))))
                .andExpect(status().isUnauthorized());
        updateUser(ownerId, adminToken, "Teszt Felhasználó", "USER", true, 1).andExpect(status().isOk());
        getJson("/api/me", ownerToken).andExpect(status().isUnauthorized());
        getJson("/api/me", login("anna@test.example")).andExpect(status().isOk());
    }

    @Test void noOpUserUpdateDoesNotInvalidateSessions() throws Exception {
        updateUser(ownerId, adminToken, "Teszt Felhasználó", "USER", true, 0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        getJson("/api/me", ownerToken).andExpect(status().isOk());
    }

    @Test void userValidationAndStaleVersionPreventUnauthorizedFieldsAndLostChanges() throws Exception {
        updateUser(ownerId, adminToken, " ", "USER", true, 0).andExpect(status().isBadRequest());
        updateUser(ownerId, adminToken, "Név", "FAKE", true, 0).andExpect(status().isBadRequest());
        updateUser(ownerId, adminToken, "Név", "USER", true, 99).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        var request = new HashMap<String, Object>(userBody("Név", "USER", true, 0)); request.put("passwordHash", "new");
        patchJson("/api/admin/users/" + ownerId, adminToken, request).andExpect(status().isBadRequest());
        request.remove("passwordHash"); request.put("email", "other@example.test");
        patchJson("/api/admin/users/" + ownerId, adminToken, request).andExpect(status().isBadRequest());
        patchJson("/api/admin/users/" + ownerId, managerToken, userBody("Név", "ADMIN", true, 0)).andExpect(status().isForbidden());
        getJson("/api/me", ownerToken).andExpect(status().isOk());
    }

    @Test void lastActiveAdministratorCannotBeDisabledOrDemoted() throws Exception {
        updateUser(adminId, adminToken, "Teszt Felhasználó", "USER", true, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        updateUser(adminId, adminToken, "Teszt Felhasználó", "ADMIN", false, 0).andExpect(status().isConflict());
        long inactiveAdmin = user("inactive@test.example", Role.ADMIN);
        jdbc.update("update app_user set enabled=false where id=?", inactiveAdmin);
        updateUser(adminId, adminToken, "Teszt Felhasználó", "USER", true, 0).andExpect(status().isConflict());
        getJson("/api/admin/users", adminToken).andExpect(status().isOk());
    }

    @Test void selfDemotionWorksWhenAnotherActiveAdminRemains() throws Exception {
        user("second@test.example", Role.ADMIN);
        updateUser(adminId, adminToken, "Teszt Felhasználó", "USER", true, 0).andExpect(status().isOk());
        getJson("/api/admin/users", adminToken).andExpect(status().isUnauthorized());
        getJson("/api/admin/users", login("admin@test.example")).andExpect(status().isForbidden());
    }

    @Test void simultaneousMaintenanceAndApprovalHaveOnlyOneWinner() throws Exception {
        long reservation = reservation(false);
        var result = compete("select id from equipment_item where id=" + itemId + " for update",
                () -> maintenance(START, END),
                () -> postJson("/api/management/reservations/" + reservation + "/approve", managerToken, Map.of("version", 0)));
        assertThat(result).contains(409);
        assertThat(result.stream().filter(code -> code == 200 || code == 201).count()).isEqualTo(1);
        boolean approved = jdbc.queryForObject("select status='APPROVED' from reservation where id=?", Boolean.class, reservation);
        assertThat(count("maintenance_period")).isEqualTo(approved ? 0 : 1);
    }

    @Test void simultaneousRetirementAndRequestCannotLeaveALiveRequestOnRetiredEquipment() throws Exception {
        var result = compete("select id from equipment_item where id=" + itemId + " for update",
                () -> retire(0), () -> requestReservation());
        assertThat(result).contains(409);
        boolean retired = jdbc.queryForObject("select lifecycle='RETIRED' from equipment_item where id=?", Boolean.class, itemId);
        assertThat(count("reservation")).isEqualTo(retired ? 0 : 1);
    }

    @Test void twoConcurrentSelfDemotionsKeepOneActiveAdministrator() throws Exception {
        long second = user("second@test.example", Role.ADMIN);
        String secondToken = login("second@test.example");
        assertThat(compete("select pg_advisory_xact_lock(173528, 1)",
                () -> updateUser(adminId, adminToken, "Teszt Felhasználó", "USER", true, 0),
                () -> updateUser(second, secondToken, "Teszt Felhasználó", "USER", true, 0)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from app_user where role='ADMIN' and enabled", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentUpdatesToOneUserCannotOverwriteEachOther() throws Exception {
        assertThat(compete("select pg_advisory_xact_lock(173528, 1)",
                () -> updateUser(ownerId, adminToken, "Első név", "USER", true, 0),
                () -> updateUser(ownerId, adminToken, "Második név", "USER", true, 0)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select token_version from app_user where id=?", Long.class, ownerId)).isEqualTo(1);
    }

    @Test void administratorRevokedWhileWaitingCannotModifyAnotherUser() throws Exception {
        long second = user("second@test.example", Role.ADMIN);
        String secondToken = login("second@test.example");
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) { statement.execute("select pg_advisory_xact_lock(173528, 1)"); }
            try {
                var request = executor.submit(() -> updateUser(ownerId, secondToken, "Tiltott változás", "USER", true, 0).andReturn().getResponse().getStatus());
                awaitLocks(1);
                try (var statement = blocker.prepareStatement("update app_user set role='USER', token_version=token_version+1, version=version+1 where id=?")) {
                    statement.setLong(1, second); statement.executeUpdate();
                }
                blocker.commit();
                assertThat(request.get(15, TimeUnit.SECONDS)).isEqualTo(403);
                assertThat(users.findById(ownerId).orElseThrow().getDisplayName()).isEqualTo("Teszt Felhasználó");
            } finally { blocker.rollback(); }
        }
    }

    @Test void auditFailureRollsBackEquipmentEditAndMaintenanceCreation() throws Exception {
        jdbc.execute("alter table audit_event add constraint test_no_management_event check(event_type not in ('EQUIPMENT_UPDATED','MAINTENANCE_OPENED'))");
        try {
            putEquipment(updateEquipment(0, locationId, "Nem maradhat meg")).andExpect(status().isConflict());
            maintenance(START, END).andExpect(status().isConflict());
            assertThat(count("maintenance_period")).isZero();
            assertThat(jdbc.queryForObject("select name from equipment_item where id=?", String.class, itemId)).isEqualTo("Kamera");
        } finally { jdbc.execute("alter table audit_event drop constraint test_no_management_event"); }
    }

    private List<Integer> compete(String lockSql, Callable<ResultActions> first, Callable<ResultActions> second) throws Exception {
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) { statement.execute(lockSql); }
            try {
                var a = executor.submit(() -> first.call().andReturn().getResponse().getStatus());
                var b = executor.submit(() -> second.call().andReturn().getResponse().getStatus());
                awaitLocks(2); blocker.commit();
                return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
    }
    private void awaitLocks(int count) { await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class)).isGreaterThanOrEqualTo(count)); }
    private ResultActions maintenance(Instant start, Instant end) throws Exception { return postJson("/api/management/maintenance", managerToken, Map.of("equipmentId", itemId, "startsAt", start.toString(), "endsAt", end.toString(), "reason", "Időszakos műszaki ellenőrzés.")); }
    private ResultActions cancelMaintenance(long id) throws Exception { return postJson("/api/management/maintenance/" + id + "/cancel", managerToken, Map.of("reason", "A javítást átütemeztük.")); }
    private ResultActions closeMaintenance(long id) throws Exception { return postJson("/api/management/maintenance/" + id + "/close", managerToken, Map.of("note", "Javítva, ellenőrizve.")); }
    private ResultActions retire(long version) throws Exception { return postJson("/api/management/equipment/" + itemId + "/retire", managerToken, Map.of("version", version, "reason", "Selejtezés, tartós meghibásodás.")); }
    private Map<String, Object> createEquipment(String code) { return Map.of("inventoryCode", code, "name", "  Új kamera  ", "description", "Leírás", "categoryId", categoryId, "locationId", locationId); }
    private Map<String, Object> updateEquipment(long version, long location, String name) { return Map.of("version", version, "name", name, "description", "Leírás", "categoryId", categoryId, "locationId", location); }
    private ResultActions putEquipment(Object body) throws Exception { return mvc.perform(put("/api/management/equipment/" + itemId).header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private Map<String, Object> userBody(String name, String role, boolean enabled, long version) { return Map.of("displayName", name, "role", role, "enabled", enabled, "version", version); }
    private ResultActions updateUser(long id, String token, String name, String role, boolean enabled, long version) throws Exception { return patchJson("/api/admin/users/" + id, token, userBody(name, role, enabled, version)); }
    private ResultActions patchJson(String path, String token, Object body) throws Exception { return mvc.perform(patch(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions requestReservation() throws Exception { return postJson("/api/reservations", ownerToken, Map.of("equipmentId", itemId, "startsAt", START.toString(), "endsAt", END.toString(), "purpose", "Egyetemi projekt felvétele.")); }
    private long reservation(boolean approved) throws Exception { long id = id(requestReservation().andExpect(status().isCreated())); if (approved) postJson("/api/management/reservations/" + id + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isOk()); return id; }
    private ResultActions postJson(String path, String token, Object body) throws Exception { return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions getJson(String path, String token) throws Exception { return mvc.perform(get(path).header("Authorization", "Bearer " + token)); }
    private long id(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong(); }
    private String login(String email) throws Exception { var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "EszkozPont2026!")))).andExpect(status().isOk()); return json.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText(); }
    private long user(String email, Role role) { var user = new AppUser(); user.setEmail(email); user.setDisplayName("Teszt Felhasználó"); user.setRole(role); user.setPasswordHash(hash); return users.saveAndFlush(user).getId(); }
    private long location(String code) { return jdbc.queryForObject("insert into storage_location(code,name,building,room) values (?,?,'A','101') returning id", Long.class, code, code); }
    private long equipment(String code) { return jdbc.queryForObject("insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) values (?,'Kamera',?,?,now(),now()) returning id", Long.class, code, categoryId, locationId); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
}
