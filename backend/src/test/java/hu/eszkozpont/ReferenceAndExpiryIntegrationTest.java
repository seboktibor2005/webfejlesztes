package hu.eszkozpont;

import hu.eszkozpont.reservation.ReservationExpiryService;
import hu.eszkozpont.user.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
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

@SpringBootTest(properties = {"JWT_SECRET=reference-expiry-tests-only-secret-at-least-32-bytes", "app.demo-data=false", "app.jwt.ttl=PT24H", "app.reservations.expiry-enabled=false"})
@AutoConfigureMockMvc
@Import(ReferenceAndExpiryIntegrationTest.TimeConfiguration.class)
class ReferenceAndExpiryIntegrationTest {
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
    @Autowired ReservationExpiryService expiry;
    private String ownerToken, managerToken, adminToken;
    private long ownerId, itemId, categoryId, locationId;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        String hash = passwords.encode("EszkozPont2026!");
        ownerId = user("anna@test.example", Role.USER, hash);
        user("manager@test.example", Role.MANAGER, hash); user("admin@test.example", Role.ADMIN, hash);
        ownerToken = login("anna@test.example"); managerToken = login("manager@test.example"); adminToken = login("admin@test.example");
        categoryId = jdbc.queryForObject("insert into equipment_category(name) values ('Fotó') returning id", Long.class);
        locationId = jdbc.queryForObject("insert into storage_location(code,name,building,room) values ('MEDIA','Médiaműhely','A','101') returning id", Long.class);
        itemId = jdbc.queryForObject("insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) values ('FOTO-001','Kamera',?,?,now(),now()) returning id", Long.class, categoryId, locationId);
    }

    @Test void categoryCrudKeepsThePublicDtoSeparate() throws Exception {
        var created = postJson("/api/admin/categories", adminToken, Map.of("name", "  Fejlesztés  ", "description", "Készletek"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Fejlesztés"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0));
        long id = id(created);
        getJson(created.andReturn().getResponse().getHeader("Location"), adminToken).andExpect(status().isOk());
        putJson("/api/admin/categories/" + id, Map.of("name", "Fejlesztőkészletek", "description", "Pontosítva", "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        getJson("/api/categories", ownerToken).andExpect(jsonPath("$[0].active").doesNotExist()).andExpect(jsonPath("$[0].version").doesNotExist());
        deleteJson("/api/admin/categories/" + id + "?version=1").andExpect(status().isNoContent());
        getJson("/api/admin/categories/" + id, adminToken).andExpect(status().isNotFound());
    }

    @Test void locationCrudNormalizesCodeAndMapsAllFields() throws Exception {
        long id = id(postJson("/api/admin/locations", adminToken, locationBody(" lab-2 ")).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("LAB-2")));
        var update = new HashMap<String, Object>(locationBody("lab-3")); update.put("version", 0); update.put("room", "204");
        putJson("/api/admin/locations/" + id, update).andExpect(status().isOk()).andExpect(jsonPath("$.room").value("204")).andExpect(jsonPath("$.version").value(1));
        deleteJson("/api/admin/locations/" + id + "?version=1").andExpect(status().isNoContent());
    }

    @Test void namesAndCodesAreUniqueAfterNormalization() throws Exception {
        postJson("/api/admin/categories", adminToken, Map.of("name", " fotó ")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));
        postJson("/api/admin/locations", adminToken, locationBody(" media ")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_CODE_TAKEN"));
        assertThatThrownBy(() -> jdbc.update("insert into equipment_category(name) values (' fotó ')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into storage_location(code,name,building,room) values ('media','Másik','A','1')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void validationRejectsProtectedFieldsMissingVersionsAndBadCodes() throws Exception {
        postJson("/api/admin/categories", adminToken, Map.of("name", "Új", "active", false)).andExpect(status().isBadRequest());
        postJson("/api/admin/categories", adminToken, Map.of("name", "  ")).andExpect(status().isBadRequest());
        postJson("/api/admin/locations", adminToken, locationBody("bad code")).andExpect(status().isBadRequest());
        putJson("/api/admin/categories/" + categoryId, Map.of("name", "Másik")).andExpect(status().isBadRequest());
        patchJson("/api/admin/categories/" + categoryId + "/active", Map.of("version", 0)).andExpect(status().isBadRequest());
        deleteJson("/api/admin/categories/" + categoryId).andExpect(status().isBadRequest());
        deleteJson("/api/admin/categories/" + categoryId + "?version=-1").andExpect(status().isBadRequest());
    }

    @Test void adminListsAreFilteredPagedAndHaveStableOrdering() throws Exception {
        postJson("/api/admin/categories", adminToken, Map.of("name", "A%_ kategória")).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/categories").param("q", "%_").param("size", "1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        getJson("/api/admin/locations?q=media&active=true", adminToken).andExpect(jsonPath("$.content[0].id").value(locationId));
        for (String query : List.of("size=101", "page=-1", "active=FAKE")) getJson("/api/admin/categories?" + query, adminToken).andExpect(status().isBadRequest());
    }

    @Test void wrongRolesCannotReadOrModifyReferenceData() throws Exception {
        for (String token : List.of(ownerToken, managerToken)) {
            getJson("/api/admin/categories", token).andExpect(status().isForbidden());
            getJson("/api/admin/locations/" + locationId, token).andExpect(status().isForbidden());
            postJson("/api/admin/categories", token, Map.of("name", "Tiltott")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
    }

    @Test void categoryArchiveHidesEquipmentAndCanBeReversed() throws Exception {
        active("categories", categoryId, false, 0).andExpect(status().isNoContent());
        getJson("/api/categories", ownerToken).andExpect(jsonPath("$").isEmpty());
        getJson("/api/equipment/" + itemId, ownerToken).andExpect(status().isNotFound());
        getJson("/api/admin/categories?active=false", adminToken).andExpect(jsonPath("$.page.totalElements").value(1));
        active("categories", categoryId, true, 1).andExpect(status().isNoContent());
        getJson("/api/equipment/" + itemId, ownerToken).andExpect(status().isOk());
    }

    @Test void locationArchiveAndVersionsAreEnforced() throws Exception {
        active("locations", locationId, false, 0).andExpect(status().isNoContent());
        getJson("/api/locations", ownerToken).andExpect(jsonPath("$").isEmpty());
        active("locations", locationId, true, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        active("locations", locationId, true, 1).andExpect(status().isNoContent());
        active("locations", locationId, true, 2).andExpect(status().isNoContent());
        getJson("/api/admin/locations/" + locationId, adminToken).andExpect(jsonPath("$.version").value(2));
    }

    @Test void referencedRecordsCannotBeDeletedEvenAfterArchiving() throws Exception {
        deleteJson("/api/admin/categories/" + categoryId + "?version=0").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_IN_USE"));
        deleteJson("/api/admin/locations/" + locationId + "?version=0").andExpect(status().isConflict());
        active("categories", categoryId, false, 0).andExpect(status().isNoContent());
        deleteJson("/api/admin/categories/" + categoryId + "?version=1").andExpect(status().isConflict());
    }

    @Test void liveReservationsPreventBothArchivesAndCancellationReleasesThem() throws Exception {
        long reservation = reservation(false);
        active("categories", categoryId, false, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_BUSY"));
        active("locations", locationId, false, 0).andExpect(status().isConflict());
        postJson("/api/reservations/" + reservation + "/cancel", ownerToken, Map.of("version", 0)).andExpect(status().isOk());
        active("categories", categoryId, false, 0).andExpect(status().isNoContent());
        requestReservation().andExpect(status().isConflict());
    }

    @Test void anOverdueOpenLoanStillPreventsArchivingAfterTheReservationEnds() throws Exception {
        long id = reservation(true); clock.set(START);
        long loan = id(postJson("/api/management/reservations/" + id + "/checkout", managerToken, Map.of("version", 1, "issueCondition", "GOOD")).andExpect(status().isCreated()));
        clock.set(END.plusSeconds(1));
        active("categories", categoryId, false, 0).andExpect(status().isConflict());
        active("locations", locationId, false, 0).andExpect(status().isConflict());
        postJson("/api/management/loans/" + loan + "/return", managerToken, Map.of("condition", "GOOD")).andExpect(status().isOk());
        active("locations", locationId, false, 0).andExpect(status().isNoContent());
    }

    @Test void staleUpdatesAndUnknownRecordsAreRejected() throws Exception {
        putJson("/api/admin/categories/" + categoryId, Map.of("name", "Új név", "version", 0)).andExpect(status().isOk());
        putJson("/api/admin/categories/" + categoryId, Map.of("name", "Régi képernyő", "version", 0)).andExpect(status().isConflict());
        getJson("/api/admin/categories/99999", adminToken).andExpect(status().isNotFound());
        getJson("/api/admin/locations/99999", adminToken).andExpect(status().isNotFound());
        deleteJson("/api/admin/locations/99999?version=0").andExpect(status().isNotFound());
    }

    @Test void concurrentArchiveAndBookingPreserveAvailabilityInvariant() throws Exception {
        var result = compete("select id from equipment_item where id=" + itemId + " for update",
                () -> active("categories", categoryId, false, 0).andReturn().getResponse().getStatus(),
                () -> requestReservation().andReturn().getResponse().getStatus());
        assertThat(result).contains(409);
        boolean archived = jdbc.queryForObject("select not active from equipment_category where id=?", Boolean.class, categoryId);
        assertThat(count("reservation")).isEqualTo(archived ? 0 : 1);
    }

    @Test void equipmentCreationWaitsForAnInProgressArchiveAndThenRejectsInactiveReference() throws Exception {
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) { statement.execute("select id from equipment_item where id=" + itemId + " for update"); }
            try {
                var archive = executor.submit(() -> active("categories", categoryId, false, 0).andReturn().getResponse().getStatus());
                awaitLocks(1);
                var creation = executor.submit(() -> postJson("/api/management/equipment", managerToken, Map.of("inventoryCode", "NEW-1", "name", "Új eszköz", "categoryId", categoryId, "locationId", locationId)).andReturn().getResponse().getStatus());
                awaitLocks(2); blocker.commit();
                assertThat(archive.get(15, TimeUnit.SECONDS)).isEqualTo(204);
                assertThat(creation.get(15, TimeUnit.SECONDS)).isEqualTo(400);
                assertThat(count("equipment_item")).isEqualTo(1);
            } finally { blocker.rollback(); }
        }
    }

    @Test void concurrentCategoryEditsDoNotOverwriteEachOther() throws Exception {
        assertThat(compete("select pg_advisory_xact_lock(173528, 2)",
                () -> putJson("/api/admin/categories/" + categoryId, Map.of("name", "Első", "version", 0)).andReturn().getResponse().getStatus(),
                () -> putJson("/api/admin/categories/" + categoryId, Map.of("name", "Második", "version", 0)).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 409);
    }

    @Test void expirationIncludesTheExactEndAndProducesASystemHistoryEntry() throws Exception {
        long id = reservation(false);
        clock.set(END.minusNanos(1)); assertThat(expiry.expire(id)).isFalse();
        clock.set(END); assertThat(expiry.candidates()).containsExactly(id); assertThat(expiry.expire(id)).isTrue();
        getJson("/api/reservations/" + id, ownerToken).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.version").value(1));
        getJson("/api/reservations/" + id + "/events", ownerToken).andExpect(jsonPath("$.content[1].eventType").value("RESERVATION_EXPIRED"))
                .andExpect(jsonPath("$.content[1].actor").isEmpty());
        assertThat(expiry.expire(id)).isFalse(); assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void approvedButUncollectedReservationsExpire() throws Exception {
        long id = reservation(true); clock.set(END);
        assertThat(expiry.expire(id)).isTrue();
        getJson("/api/reservations/" + id, ownerToken).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test void openAndCompletedLoansNeverExpire() throws Exception {
        long id = reservation(true); clock.set(START);
        long loan = id(postJson("/api/management/reservations/" + id + "/checkout", managerToken, Map.of("version", 1, "issueCondition", "GOOD")).andExpect(status().isCreated()));
        clock.set(END.plusSeconds(1));
        assertThat(expiry.candidates()).isEmpty(); assertThat(expiry.expire(id)).isFalse();
        getJson("/api/reservations/" + id, ownerToken).andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.loan.overdue").value(true));
        postJson("/api/management/loans/" + loan + "/return", managerToken, Map.of("condition", "GOOD")).andExpect(status().isOk());
        assertThat(expiry.expire(id)).isFalse();
    }

    @Test void cancellationAfterCandidateSelectionIsNotOverwritten() throws Exception {
        long id = reservation(false); clock.set(END);
        assertThat(expiry.candidates()).containsExactly(id);
        postJson("/api/management/reservations/" + id + "/cancel", managerToken, Map.of("version", 0, "reason", "Nem történt átvétel, kezelői lemondás.")).andExpect(status().isOk());
        assertThat(expiry.expire(id)).isFalse(); assertThat(expiry.expire(99999)).isFalse();
        assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void candidateBatchIsBoundedAndOrdered() {
        jdbc.update("""
                insert into reservation(requester_id,equipment_id,starts_at,ends_at,purpose,status,created_at,updated_at)
                select ?,?,?,?,'Lejárati tesztadat','REQUESTED',now(),now() from generate_series(1,102)
                """, ownerId, itemId, NOW.minusSeconds(7200).atOffset(ZoneOffset.UTC), NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC));
        var candidates = expiry.candidates();
        assertThat(candidates).hasSize(100).isSorted().doesNotHaveDuplicates();
    }

    @Test void failedAuditRollsBackExpirationAndCanBeRetried() {
        long id;
        try { id = reservation(false); } catch (Exception exception) { throw new RuntimeException(exception); }
        clock.set(END);
        jdbc.execute("alter table audit_event add constraint test_no_expiry check(event_type <> 'RESERVATION_EXPIRED')");
        try {
            assertThatThrownBy(() -> expiry.expire(id)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, id)).isEqualTo("REQUESTED");
            assertThat(count("audit_event")).isEqualTo(1);
        } finally { jdbc.execute("alter table audit_event drop constraint test_no_expiry"); }
        assertThat(expiry.expire(id)).isTrue();
    }

    @Test void concurrentExpiryRunsAppendOnlyOneEvent() throws Exception {
        long id = reservation(false); clock.set(END);
        assertThat(compete("select id from equipment_item where id=" + itemId + " for update", () -> expiry.expire(id), () -> expiry.expire(id)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void concurrentCancellationAndExpiryDoNotOverwriteATerminalState() throws Exception {
        long id = reservation(false); clock.set(END);
        compete("select id from equipment_item where id=" + itemId + " for update", () -> expiry.expire(id) ? "EXPIRED" : "SKIPPED",
                () -> String.valueOf(postJson("/api/management/reservations/" + id + "/cancel", managerToken, Map.of("version", 0, "reason", "Kezelői lemondás a határidő után.")).andReturn().getResponse().getStatus()));
        assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, id)).isIn("EXPIRED", "CANCELLED");
        assertThat(count("audit_event")).isEqualTo(2);
    }

    private <T> List<T> compete(String lockSql, Callable<T> first, Callable<T> second) throws Exception {
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) { statement.execute(lockSql); }
            try {
                var a = executor.submit(first); var b = executor.submit(second);
                awaitLocks(2); blocker.commit();
                return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
    }
    private void awaitLocks(int count) { await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class)).isGreaterThanOrEqualTo(count)); }
    private ResultActions active(String kind, long id, boolean active, long version) throws Exception { return patchJson("/api/admin/" + kind + "/" + id + "/active", Map.of("active", active, "version", version)); }
    private Map<String, Object> locationBody(String code) { return Map.of("code", code, "name", " Labor ", "building", " A ", "room", " 201 "); }
    private ResultActions requestReservation() throws Exception { return postJson("/api/reservations", ownerToken, Map.of("equipmentId", itemId, "startsAt", START.toString(), "endsAt", END.toString(), "purpose", "Egyetemi projekt felvétele.")); }
    private long reservation(boolean approved) throws Exception { long id = id(requestReservation().andExpect(status().isCreated())); if (approved) postJson("/api/management/reservations/" + id + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isOk()); return id; }
    private ResultActions postJson(String path, String token, Object body) throws Exception { return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions putJson(String path, Object body) throws Exception { return mvc.perform(put(path).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions patchJson(String path, Object body) throws Exception { return mvc.perform(patch(path).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions deleteJson(String path) throws Exception { return mvc.perform(delete(path).header("Authorization", "Bearer " + adminToken)); }
    private ResultActions getJson(String path, String token) throws Exception { return mvc.perform(get(path).header("Authorization", "Bearer " + token)); }
    private long id(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong(); }
    private String login(String email) throws Exception { var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "EszkozPont2026!")))).andExpect(status().isOk()); return json.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText(); }
    private long user(String email, Role role, String hash) { var user = new AppUser(); user.setEmail(email); user.setDisplayName("Teszt Felhasználó"); user.setRole(role); user.setPasswordHash(hash); return users.saveAndFlush(user).getId(); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
}
