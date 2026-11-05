package hu.eszkozpont;

import hu.eszkozpont.user.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
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

@SpringBootTest(properties = {"JWT_SECRET=reservation-tests-only-secret-at-least-32-bytes", "app.demo-data=false"})
@AutoConfigureMockMvc
@Import(ReservationIntegrationTest.TimeConfiguration.class)
class ReservationIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private static final Instant START = NOW.plus(2, ChronoUnit.DAYS);
    private static final Instant END = START.plus(2, ChronoUnit.HOURS);
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
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
    private String ownerToken, otherToken, managerToken, adminToken;
    private long ownerId, managerId, itemId, otherItemId;
    private long categoryId, locationId;

    @BeforeEach
    void fixture() throws Exception {
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        // Egyetlen hash elég a rögzített tesztjelszóhoz; a belépés továbbra is valódi BCrypt-ellenőrzés.
        var hash = passwords.encode("EszkozPont2026!");
        ownerId = user("anna@test.example", Role.USER, hash);
        user("bence@test.example", Role.USER, hash);
        managerId = user("manager@test.example", Role.MANAGER, hash);
        user("admin@test.example", Role.ADMIN, hash);
        ownerToken = login("anna@test.example"); otherToken = login("bence@test.example");
        managerToken = login("manager@test.example"); adminToken = login("admin@test.example");
        categoryId = jdbc.queryForObject("INSERT INTO equipment_category(name) VALUES ('Kamera') RETURNING id", Long.class);
        locationId = jdbc.queryForObject("INSERT INTO storage_location(code,name,building,room) VALUES ('MEDIA','Médiaműhely','A','101') RETURNING id", Long.class);
        itemId = equipment("FOTO-001"); otherItemId = equipment("FOTO-002");
    }

    @Test void createReturnsLocationOwnerAndAnAtomicHistoryEntry() throws Exception {
        var result = create(ownerToken, itemId, START, END).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requester.id").value(ownerId))
                .andExpect(jsonPath("$.requester.email").doesNotExist())
                .andExpect(jsonPath("$.equipment.inventoryCode").value("FOTO-001"))
                .andExpect(jsonPath("$.version").value(0)).andReturn().getResponse();
        mvc.perform(get(result.getHeader("Location")).header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        long id = json.readTree(result.getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/reservations/" + id + "/events").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].eventType").value("RESERVATION_CREATED"));
    }

    @Test void pendingRequestsMayOverlapButAnApprovedOneBlocksNewRequests() throws Exception {
        long first = created(ownerToken, itemId, START, END);
        created(otherToken, itemId, START, END);
        approve(first, 0, managerToken).andExpect(status().isOk());
        create(otherToken, itemId, START, END).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        assertThat(count("reservation")).isEqualTo(2);
        assertThat(count("audit_event")).isEqualTo(3);
    }

    @Test void adjacentIntervalsAreAllowedAndPartialOrContainedOverlapsAreRejected() throws Exception {
        long first = created(ownerToken, itemId, START, END);
        long partial = created(otherToken, itemId, END.minus(1, ChronoUnit.HOURS), END.plus(1, ChronoUnit.HOURS));
        long containing = created(otherToken, itemId, START.minus(1, ChronoUnit.HOURS), END.plus(1, ChronoUnit.HOURS));
        approve(first, 0, managerToken).andExpect(status().isOk());
        approve(partial, 0, managerToken).andExpect(status().isConflict());
        approve(containing, 0, managerToken).andExpect(status().isConflict());
        long next = created(ownerToken, itemId, END, END.plus(1, ChronoUnit.HOURS));
        approve(next, 0, adminToken).andExpect(status().isOk());
    }

    @Test void invalidDatesDurationHorizonAndMinutePrecisionAreRejected() throws Exception {
        for (var period : List.of(new Instant[]{NOW, END}, new Instant[]{START, START},
                new Instant[]{START, START.plusSeconds(3599)}, new Instant[]{START, START.plus(7, ChronoUnit.DAYS).plusSeconds(60)},
                new Instant[]{NOW.plus(60, ChronoUnit.DAYS).plusSeconds(60), NOW.plus(61, ChronoUnit.DAYS)},
                new Instant[]{START.plusSeconds(1), END.plusSeconds(1)})) {
            create(ownerToken, itemId, period[0], period[1]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        }
        assertThat(count("reservation")).isZero(); assertThat(count("audit_event")).isZero();
    }

    @Test void exactMinimumMaximumAndHorizonBoundariesWork() throws Exception {
        created(ownerToken, itemId, START, START.plus(1, ChronoUnit.HOURS));
        created(ownerToken, itemId, START, START.plus(7, ChronoUnit.DAYS));
        var horizon = NOW.plus(60, ChronoUnit.DAYS);
        created(ownerToken, itemId, horizon, horizon.plus(1, ChronoUnit.HOURS));
    }

    @Test void dtoValidationAndProtectedFieldsPreventIdentityOrStateInjection() throws Exception {
        mvc.perform(post("/api/reservations").header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var request = new HashMap<String, Object>(request(itemId, START, END));
        request.put("requesterId", managerId);
        postJson("/api/reservations", ownerToken, request).andExpect(status().isBadRequest());
        request.remove("requesterId"); request.put("status", "APPROVED");
        postJson("/api/reservations", ownerToken, request).andExpect(status().isBadRequest());
        request.remove("status"); request.put("purpose", "         x         ");
        postJson("/api/reservations", ownerToken, request).andExpect(status().isBadRequest());
        assertThat(count("reservation")).isZero();
    }

    @Test void anonymousUsersAndWrongRolesCannotUseTheWorkflow() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(itemId, START, END))))
                .andExpect(status().isUnauthorized());
        long id = created(ownerToken, itemId, START, END);
        approve(id, 0, ownerToken).andExpect(status().isForbidden());
        mvc.perform(get("/api/management/reservations").header("Authorization", bearer(ownerToken))).andExpect(status().isForbidden());
    }

    @Test void otherUsersCannotReadCancelOrInspectPrivateHistory() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        for (String suffix : List.of("", "/events")) {
            mvc.perform(get("/api/reservations/" + id + suffix).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        }
        postJson("/api/reservations/" + id + "/cancel", otherToken, Map.of("version", 0)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/reservations").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/reservations/99999").header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
    }

    @Test void managersCannotApproveTheirOwnRequests() throws Exception {
        long id = created(managerToken, itemId, START, END);
        approve(id, 0, managerToken).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_APPROVAL"));
        approve(id, 0, adminToken).andExpect(status().isOk());
    }

    @Test void staleVersionsAndRepeatedDecisionsCannotOverwriteTheState() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        approve(id, 5, managerToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        approve(id, 0, managerToken).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        approve(id, 0, managerToken).andExpect(status().isConflict());
        approve(id, 1, managerToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void rejectionNeedsAReasonAndProducesHistory() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        postJson("/api/management/reservations/" + id + "/reject", managerToken, Map.of("version", 0, "reason", "   "))
                .andExpect(status().isBadRequest());
        postJson("/api/management/reservations/" + id + "/reject", managerToken, Map.of("version", 0, "reason", "Nem megfelelő felhasználási cél."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        approve(id, 1, managerToken).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select note from audit_event where event_type='RESERVATION_REJECTED'", String.class))
                .isEqualTo("Nem megfelelő felhasználási cél.");
    }

    @Test void ownerCanCancelAndReleaseApprovedTimeButManagerNeedsReason() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        approve(id, 0, managerToken).andExpect(status().isOk());
        postJson("/api/management/reservations/" + id + "/cancel", managerToken, Map.of("version", 1))
                .andExpect(status().isBadRequest());
        postJson("/api/reservations/" + id + "/cancel", ownerToken, Map.of("version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        long next = created(otherToken, itemId, START, END);
        approve(next, 0, managerToken).andExpect(status().isOk());
        postJson("/api/management/reservations/" + next + "/cancel", managerToken, Map.of("version", 1, "reason", "Intézményi rendezvény miatt."))
                .andExpect(status().isOk());
    }

    @Test void listsArePagedScopedFilteredAndWhitelisted() throws Exception {
        created(ownerToken, itemId, START, END); created(otherToken, otherItemId, START, END);
        mvc.perform(get("/api/me/reservations").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/management/reservations").param("size", "1").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/me/reservations").param("status", "APPROVED").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        for (var filter : List.of(Map.of("sort", "requester.passwordHash,asc"), Map.of("size", "101"), Map.of("page", "-1"), Map.of("status", "FAKE"))) {
            var builder = get("/api/me/reservations").header("Authorization", bearer(ownerToken));
            filter.forEach(builder::param); mvc.perform(builder).andExpect(status().isBadRequest());
        }
    }

    @Test void equipmentAndRequesterMustStillBeActiveWhenApproving() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        jdbc.update("update equipment_item set lifecycle='RETIRED' where id=?", itemId);
        approve(id, 0, managerToken).andExpect(status().isConflict());
        create(ownerToken, itemId, START, END).andExpect(status().isConflict());
        create(ownerToken, 99999, START, END).andExpect(status().isNotFound());
        jdbc.update("update equipment_item set lifecycle='ACTIVE' where id=?", itemId);
        jdbc.update("update equipment_category set active=false where id=?", categoryId);
        approve(id, 0, managerToken).andExpect(status().isConflict());
        jdbc.update("update equipment_category set active=true where id=?", categoryId);
        jdbc.update("update storage_location set active=false where id=?", locationId);
        approve(id, 0, managerToken).andExpect(status().isConflict());
        jdbc.update("update storage_location set active=true where id=?", locationId);
        jdbc.update("update app_user set enabled=false where id=?", ownerId);
        approve(id, 0, managerToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUESTER_DISABLED"));
    }

    @Test void maintenanceIsCheckedAgainAtApprovalIncludingOverdueMaintenance() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        long maintenance = maintenance(START, END);
        approve(id, 0, managerToken).andExpect(status().isConflict());
        jdbc.update("update maintenance_period set status='CANCELLED' where id=?", maintenance);
        long overdue = maintenance(NOW.minus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        approve(id, 0, managerToken).andExpect(status().isConflict());
        jdbc.update("update maintenance_period set status='CLOSED', closed_at=now() where id=?", overdue);
        approve(id, 0, managerToken).andExpect(status().isOk());
    }

    @Test void startedReservationsAndExistingLoansCannotBeCancelledByTheirOwner() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        jdbc.update("update reservation set starts_at=?, ends_at=? where id=?", NOW.minusSeconds(60).atOffset(ZoneOffset.UTC), NOW.plusSeconds(3600).atOffset(ZoneOffset.UTC), id);
        approve(id, 0, managerToken).andExpect(status().isConflict());
        postJson("/api/reservations/" + id + "/cancel", ownerToken, Map.of("version", 0)).andExpect(status().isConflict());
        jdbc.update("update reservation set starts_at=?,ends_at=?,status='APPROVED' where id=?", START.atOffset(ZoneOffset.UTC), END.atOffset(ZoneOffset.UTC), id);
        jdbc.update("insert into loan(reservation_id,equipment_id,issued_by,issued_at,due_at,issue_condition) values (?,?,?,?,?,'GOOD')",
                id, itemId, managerId, NOW.minusSeconds(7200).atOffset(ZoneOffset.UTC), NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC));
        postJson("/api/reservations/" + id + "/cancel", ownerToken, Map.of("version", 0)).andExpect(status().isConflict());
        create(otherToken, itemId, END, END.plusSeconds(3600)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EQUIPMENT_UNAVAILABLE"));
    }

    @Test void auditFailureRollsBackTheDecisionAsWell() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        jdbc.execute("alter table audit_event add constraint test_reject_approval check(event_type <> 'RESERVATION_APPROVED')");
        try {
            approve(id, 0, managerToken).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, id)).isEqualTo("REQUESTED");
            assertThat(count("audit_event")).isEqualTo(1);
        } finally { jdbc.execute("alter table audit_event drop constraint test_reject_approval"); }
    }

    @Test void simultaneousOverlappingApprovalsHaveExactlyOneWinner() throws Exception {
        long first = created(ownerToken, itemId, START, END);
        long second = created(otherToken, itemId, START.plusSeconds(3600), END.plusSeconds(3600));
        assertThat(competingApprovals(first, second)).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from reservation where status='APPROVED'", Integer.class)).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(3);
    }

    @Test void simultaneousApprovalOfTheSameRequestDoesNotDuplicateHistory() throws Exception {
        long id = created(ownerToken, itemId, START, END);
        assertThat(competingApprovals(id, id)).containsExactlyInAnyOrder(200, 409);
        assertThat(count("audit_event")).isEqualTo(2);
    }

    @Test void simultaneousAdjacentApprovalsBothSucceed() throws Exception {
        long first = created(ownerToken, itemId, START, END);
        long second = created(otherToken, itemId, END, END.plusSeconds(3600));
        assertThat(competingApprovals(first, second)).containsExactly(200, 200);
    }

    private List<Integer> competingApprovals(long first, long second) throws Exception {
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("select id from equipment_item where id=? for update")) {
                statement.setLong(1, itemId); statement.executeQuery().close();
            }
            try {
                var a = executor.submit(() -> approve(first, 0, managerToken).andReturn().getResponse().getStatus());
                var b = executor.submit(() -> approve(second, 0, adminToken).andReturn().getResponse().getStatus());
                // Nem pusztán két szálat indítunk: mindkét API-kérés tényleges adatbázis-sorzárra vár.
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
                        Integer.class)).isGreaterThanOrEqualTo(2));
                blocker.commit();
                return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
    }

    private ResultActions create(String token, long item, Instant start, Instant end) throws Exception {
        return postJson("/api/reservations", token, request(item, start, end));
    }
    private long created(String token, long item, Instant start, Instant end) throws Exception {
        var result = create(token, item, start, end).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(result.getContentAsString()).get("id").asLong();
    }
    private Map<String, Object> request(long item, Instant start, Instant end) {
        return Map.of("equipmentId", item, "startsAt", start.toString(), "endsAt", end.toString(), "purpose", "Egyetemi interjúprojekt felvétele.");
    }
    private ResultActions approve(long id, long version, String token) throws Exception {
        return postJson("/api/management/reservations/" + id + "/approve", token, Map.of("version", version));
    }
    private ResultActions postJson(String path, String token, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private String bearer(String token) { return "Bearer " + token; }
    private long user(String email, Role role, String hash) {
        var user = new AppUser(); user.setEmail(email); user.setDisplayName("Teszt Felhasználó"); user.setRole(role); user.setPasswordHash(hash);
        return users.saveAndFlush(user).getId();
    }
    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "EszkozPont2026!"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(result.getContentAsString()).get("accessToken").asText();
    }
    private long equipment(String code) {
        return jdbc.queryForObject("insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) values (?,'Kamera',?,?,now(),now()) returning id",
                Long.class, code, categoryId, locationId);
    }
    private long maintenance(Instant start, Instant end) {
        return jdbc.queryForObject("insert into maintenance_period(equipment_id,created_by,starts_at,ends_at,reason,status,created_at,updated_at) values (?,?,?,?,'Ellenőrzés','OPEN',now(),now()) returning id",
                Long.class, itemId, managerId, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC));
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
}
