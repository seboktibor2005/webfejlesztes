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

// Az üzleti órát órákkal előreléptetjük; kizárólag ebben a tesztben hosszabb a token élettartama.
@SpringBootTest(properties = {"JWT_SECRET=loan-tests-only-secret-at-least-32-bytes", "app.demo-data=false", "app.jwt.ttl=PT24H", "app.reservations.expiry-enabled=false"})
@AutoConfigureMockMvc
@Import(LoanIntegrationTest.TimeConfiguration.class)
class LoanIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private static final Instant START = NOW.plus(2, ChronoUnit.HOURS);
    private static final Instant END = START.plus(2, ChronoUnit.HOURS);
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static class TestClock extends Clock {
        private volatile Instant instant = NOW;
        void set(Instant value) { instant = value; }
        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
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
    @Autowired TestClock clock;
    private String ownerToken, otherToken, managerToken, adminToken;
    private long ownerId, managerId, itemId, categoryId, locationId;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        var hash = passwords.encode("EszkozPont2026!");
        ownerId = user("anna@test.example", Role.USER, hash);
        user("bence@test.example", Role.USER, hash);
        managerId = user("manager@test.example", Role.MANAGER, hash);
        user("admin@test.example", Role.ADMIN, hash);
        ownerToken = login("anna@test.example"); otherToken = login("bence@test.example");
        managerToken = login("manager@test.example"); adminToken = login("admin@test.example");
        categoryId = jdbc.queryForObject("INSERT INTO equipment_category(name) VALUES ('Kamera') RETURNING id", Long.class);
        locationId = jdbc.queryForObject("INSERT INTO storage_location(code,name,building,room) VALUES ('MEDIA','Médiaműhely','A','101') RETURNING id", Long.class);
        itemId = jdbc.queryForObject("INSERT INTO equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) VALUES ('FOTO-001','Kamera',?,?,now(),now()) RETURNING id",
                Long.class, categoryId, locationId);
    }

    @Test void checkoutCreatesOneLoanWithServerControlledActorsAndDeadline() throws Exception {
        long reservation = approved(START, END);
        clock.set(START);
        var result = checkout(reservation, managerToken).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueAt").value(END.toString()))
                .andExpect(jsonPath("$.issuedBy.id").value(managerId))
                .andExpect(jsonPath("$.requester.email").doesNotExist())
                .andExpect(jsonPath("$.overdue").value(false)).andReturn().getResponse();
        getJson(result.getHeader("Location"), managerToken).andExpect(status().isOk());
        getJson("/api/reservations/" + reservation, ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.loan.id").exists());
        getJson("/api/me/reservations", ownerToken).andExpect(jsonPath("$.content[0].hasOpenLoan").value(true));
        assertThat(count("audit_event")).isEqualTo(3);
    }

    @Test void checkoutWindowIncludesStartButExcludesEnd() throws Exception {
        long first = approved(START, END);
        checkout(first, managerToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTSIDE_PICKUP_WINDOW"));
        clock.set(END);
        checkout(first, managerToken).andExpect(status().isConflict());
        clock.set(START);
        checkout(first, adminToken).andExpect(status().isCreated());
    }

    @Test void onlyApprovedRequestsAndCurrentVersionsCanBeCheckedOut() throws Exception {
        long id = requested(START, END);
        clock.set(START);
        checkout(id, managerToken).andExpect(status().isConflict());
        postJson("/api/management/reservations/99999/checkout", managerToken, Map.of("version", 0, "issueCondition", "GOOD"))
                .andExpect(status().isNotFound());
        clock.set(NOW);
        approve(id);
        clock.set(START);
        postJson("/api/management/reservations/" + id + "/checkout", managerToken, Map.of("version", 0, "issueCondition", "GOOD"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        checkout(id, managerToken).andExpect(status().isCreated());
        checkout(id, managerToken).andExpect(status().isConflict());
    }

    @Test void userAndAnonymousAccessCannotIssueReturnOrCloseMaintenance() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        checkout(id, ownerToken).andExpect(status().isForbidden());
        mvc.perform(get("/api/management/loans")).andExpect(status().isUnauthorized());
        long loan = issued(id);
        for (String path : List.of("/api/management/loans", "/api/management/loans/" + loan)) {
            getJson(path, ownerToken).andExpect(status().isForbidden());
        }
        takeBack(loan, ownerToken, Map.of("condition", "GOOD")).andExpect(status().isForbidden());
        postJson("/api/management/maintenance/1/close", ownerToken, Map.of()).andExpect(status().isForbidden());
        getJson("/api/reservations/" + id, otherToken).andExpect(status().isNotFound());
        getJson("/api/management/loans/99999", managerToken).andExpect(status().isNotFound());
        takeBack(99999, managerToken, Map.of("condition", "GOOD")).andExpect(status().isNotFound());
    }

    @Test void invalidConditionsVersionsAndProtectedFieldsAreRejected() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        var path = "/api/management/reservations/" + id + "/checkout";
        for (var body : List.of(Map.of(), Map.of("version", -1, "issueCondition", "GOOD"),
                Map.of("version", 1, "issueCondition", "DAMAGED"), Map.of("version", 1, "issueCondition", "GOOD", "issuedBy", ownerId))) {
            postJson(path, managerToken, body).andExpect(status().isBadRequest());
        }
        long loan = issued(id);
        takeBack(loan, managerToken, Map.of()).andExpect(status().isBadRequest());
        takeBack(loan, managerToken, Map.of("condition", "GOOD", "returnedAt", NOW.toString())).andExpect(status().isBadRequest());
        takeBack(loan, managerToken, Map.of("condition", "GOOD", "note", "x".repeat(1001))).andExpect(status().isBadRequest());
    }

    @Test void inactiveEquipmentCategoryLocationAndBorrowerBlockCheckout() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        for (var mutation : List.of(
                new String[]{"equipment_item", "lifecycle='RETIRED'", "lifecycle='ACTIVE'"},
                new String[]{"equipment_category", "active=false", "active=true"},
                new String[]{"storage_location", "active=false", "active=true"},
                new String[]{"app_user", "enabled=false", "enabled=true"})) {
            long key = switch (mutation[0]) { case "app_user" -> ownerId; case "equipment_category" -> categoryId; case "storage_location" -> locationId; default -> itemId; };
            jdbc.update("update " + mutation[0] + " set " + mutation[1] + " where id=?", key);
            checkout(id, managerToken).andExpect(status().isConflict());
            jdbc.update("update " + mutation[0] + " set " + mutation[2] + " where id=?", key);
        }
        checkout(id, managerToken).andExpect(status().isCreated());
    }

    @Test void anyOpenLoanBlocksTheNextCheckoutEvenAtTheExactDeadline() throws Exception {
        long first = approved(START, END);
        long next = approved(END, END.plusSeconds(7200));
        clock.set(START); issued(first);
        clock.set(END);
        checkout(next, managerToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EQUIPMENT_UNAVAILABLE"));
        clock.set(END.plusSeconds(1));
        getJson("/api/reservations/" + next, ownerToken).andExpect(jsonPath("$.atRisk").value(true));
        assertThat(count("loan")).isEqualTo(1);
    }

    @Test void plannedAndOverdueOpenMaintenanceAreCheckedAtCheckout() throws Exception {
        long id = approved(START, END);
        long maintenance = maintenance(START.plusSeconds(3600), END.plusSeconds(3600));
        clock.set(START);
        checkout(id, managerToken).andExpect(status().isConflict());
        jdbc.update("update maintenance_period set starts_at=?,ends_at=? where id=?", offset(NOW.minusSeconds(3600)), offset(NOW), maintenance);
        checkout(id, managerToken).andExpect(status().isConflict());
        close(maintenance).andExpect(status().isOk());
        checkout(id, managerToken).andExpect(status().isCreated());
    }

    @Test void normalReturnCompletesReservationAndPreservesTheRecord() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        clock.set(START.plusSeconds(60));
        takeBack(loan, adminToken, Map.of("condition", "WORN", "note", "  Működőképes, hiánytalan.  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.returnCondition").value("WORN"))
                .andExpect(jsonPath("$.returnNote").value("Működőképes, hiánytalan."))
                .andExpect(jsonPath("$.returnedLate").value(false));
        getJson("/api/reservations/" + id, ownerToken).andExpect(jsonPath("$.status").value("COMPLETED"));
        takeBack(loan, managerToken, Map.of("condition", "GOOD")).andExpect(status().isConflict());
        assertThat(count("loan")).isEqualTo(1); assertThat(count("audit_event")).isEqualTo(4);
    }

    @Test void returnStillWorksForRetiredEquipmentAndDisabledBorrowers() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        jdbc.update("update equipment_item set lifecycle='RETIRED' where id=?", itemId);
        jdbc.update("update app_user set enabled=false where id=?", ownerId);
        takeBack(loan, managerToken, Map.of("condition", "GOOD")).andExpect(status().isOk());
    }

    @Test void damageRequiresReasonAndFutureMaintenanceEndWithoutPartialWrites() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        for (var body : List.of(Map.of("condition", "DAMAGED"),
                Map.of("condition", "DAMAGED", "note", "   ", "maintenanceEndsAt", END.toString()),
                Map.of("condition", "DAMAGED", "note", "Sérült a burkolat.", "maintenanceEndsAt", START.toString()),
                Map.of("condition", "GOOD", "maintenanceEndsAt", END.toString()))) {
            takeBack(loan, managerToken, body).andExpect(status().isBadRequest());
        }
        assertThat(count("maintenance_period")).isZero();
        assertThat(jdbc.queryForObject("select returned_at is null from loan where id=?", Boolean.class, loan)).isTrue();
    }

    @Test void damageCreatesSafetyHoldKeepsFutureApprovalAndClosingReleasesIt() throws Exception {
        long id = approved(START, END);
        long next = approved(END, END.plusSeconds(7200));
        clock.set(START); long loan = issued(id);
        var result = takeBack(loan, managerToken, Map.of("condition", "DAMAGED", "note", "Megsérült az objektív.",
                "maintenanceEndsAt", END.plusSeconds(3600).toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.status").value("OPEN")).andReturn();
        long maintenance = json.readTree(result.getResponse().getContentAsString()).get("maintenance").get("id").asLong();
        getJson("/api/reservations/" + next, ownerToken).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.atRisk").value(true));
        getJson("/api/me/reservations?status=APPROVED", ownerToken).andExpect(jsonPath("$.content[0].atRisk").value(true));
        clock.set(END);
        checkout(next, managerToken).andExpect(status().isConflict());
        close(maintenance).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        close(maintenance).andExpect(status().isConflict());
        checkout(next, managerToken).andExpect(status().isCreated());
        getJson("/api/reservations/" + id + "/events", ownerToken).andExpect(jsonPath("$.content[5].eventType").value("MAINTENANCE_CLOSED"));
    }

    @Test void overdueIsDerivedAtRequestTimeAndStopsGrowingAfterReturn() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        clock.set(END);
        getJson("/api/management/loans/" + loan, managerToken).andExpect(jsonPath("$.overdue").value(false));
        clock.set(END.plusNanos(1));
        getJson("/api/management/loans/" + loan, managerToken).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.overdueMinutes").value(1));
        getJson("/api/me/reservations", ownerToken).andExpect(jsonPath("$.content[0].overdue").value(true));
        clock.set(END.plusSeconds(61));
        takeBack(loan, managerToken, Map.of("condition", "GOOD")).andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false)).andExpect(jsonPath("$.returnedLate").value(true))
                .andExpect(jsonPath("$.overdueMinutes").value(2));
        clock.set(END.plusSeconds(7200));
        getJson("/api/management/loans/" + loan, managerToken).andExpect(jsonPath("$.overdueMinutes").value(2));
    }

    @Test void overdueLoanBlocksNewRequestsAndApprovalUntilItIsReturned() throws Exception {
        long id = approved(START, END);
        long pending = requested(END.plusSeconds(3600), END.plusSeconds(7200));
        clock.set(START); long loan = issued(id);
        clock.set(END.plusSeconds(1));
        postJson("/api/management/reservations/" + pending + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isConflict());
        takeBack(loan, managerToken, Map.of("condition", "GOOD")).andExpect(status().isOk());
        approve(pending);
    }

    @Test void worklistFiltersArePagedAndContradictoryFiltersAreRejected() throws Exception {
        long id = approved(START, END);
        clock.set(START); issued(id);
        getJson("/api/management/loans?overdue=true", managerToken).andExpect(jsonPath("$.content").isEmpty());
        clock.set(END.plusSeconds(60));
        getJson("/api/management/loans?overdue=true&open=true&size=1", adminToken)
                .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].overdue").value(true));
        getJson("/api/management/loans?open=false", managerToken).andExpect(jsonPath("$.content").isEmpty());
        for (var query : List.of("open=false&overdue=true", "size=101", "page=-1", "open=FAKE")) {
            getJson("/api/management/loans?" + query, managerToken).andExpect(status().isBadRequest());
        }
    }

    @Test void anIssuedReservationCannotBeCancelledByAManager() throws Exception {
        long id = approved(START, END);
        clock.set(START); issued(id);
        postJson("/api/management/reservations/" + id + "/cancel", managerToken, Map.of("version", 1, "reason", "Próbavisszavonás."))
                .andExpect(status().isConflict());
    }

    @Test void auditFailureRollsBackCheckout() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        jdbc.execute("alter table audit_event add constraint test_no_issue check(event_type <> 'LOAN_ISSUED')");
        try {
            checkout(id, managerToken).andExpect(status().isConflict());
            assertThat(count("loan")).isZero();
        } finally { jdbc.execute("alter table audit_event drop constraint test_no_issue"); }
    }

    @Test void auditFailureRollsBackDamagedReturnAndMaintenanceTogether() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        jdbc.execute("alter table audit_event add constraint test_no_return check(event_type <> 'LOAN_RETURNED')");
        try {
            takeBack(loan, managerToken, Map.of("condition", "DAMAGED", "note", "Eltört a rögzítés.", "maintenanceEndsAt", END.toString()))
                    .andExpect(status().isConflict());
            assertThat(count("maintenance_period")).isZero();
            assertThat(jdbc.queryForObject("select returned_at is null from loan where id=?", Boolean.class, loan)).isTrue();
            assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, id)).isEqualTo("APPROVED");
            assertThat(count("audit_event")).isEqualTo(3);
        } finally { jdbc.execute("alter table audit_event drop constraint test_no_return"); }
    }

    @Test void concurrentCheckoutsCreateExactlyOneLoanAndEvent() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        assertThat(compete(() -> checkout(id, managerToken), () -> checkout(id, adminToken))).containsExactlyInAnyOrder(201, 409);
        assertThat(count("loan")).isEqualTo(1); assertThat(count("audit_event")).isEqualTo(3);
    }

    @Test void concurrentDamagedReturnsCreateExactlyOneMaintenanceAndCompletion() throws Exception {
        long id = approved(START, END);
        clock.set(START); long loan = issued(id);
        var body = Map.of("condition", "DAMAGED", "note", "Eltört a burkolat.", "maintenanceEndsAt", END.toString());
        assertThat(compete(() -> takeBack(loan, managerToken, body), () -> takeBack(loan, adminToken, body))).containsExactlyInAnyOrder(200, 409);
        assertThat(count("maintenance_period")).isEqualTo(1); assertThat(count("audit_event")).isEqualTo(5);
    }

    @Test void concurrentCancellationAndCheckoutCannotLeaveACancelledLoan() throws Exception {
        long id = approved(START, END);
        clock.set(START);
        var results = compete(() -> checkout(id, managerToken), () -> postJson("/api/management/reservations/" + id + "/cancel",
                adminToken, Map.of("version", 1, "reason", "Intézményi lemondás.")));
        assertThat(results).contains(409);
        var state = jdbc.queryForObject("select status from reservation where id=?", String.class, id);
        assertThat(count("loan")).isEqualTo(state.equals("APPROVED") ? 1 : 0);
        assertThat(state).isIn("APPROVED", "CANCELLED");
    }

    @Test void databaseAlsoRejectsTwoOpenLoansForTheSameEquipment() throws Exception {
        long first = approved(START, END);
        long next = approved(END, END.plusSeconds(7200));
        clock.set(START); issued(first);
        assertThatThrownBy(() -> jdbc.update("insert into loan(reservation_id,equipment_id,issued_by,issued_at,due_at,issue_condition) values (?,?,?,?,?,'GOOD')",
                next, itemId, managerId, offset(START), offset(END))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("loan")).isEqualTo(1);
    }

    private List<Integer> compete(Callable<ResultActions> first, Callable<ResultActions> second) throws Exception {
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("select id from equipment_item where id=? for update")) {
                statement.setLong(1, itemId); statement.executeQuery().close();
            }
            try {
                var a = executor.submit(() -> first.call().andReturn().getResponse().getStatus());
                var b = executor.submit(() -> second.call().andReturn().getResponse().getStatus());
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'", Integer.class)).isGreaterThanOrEqualTo(2));
                blocker.commit();
                return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
    }
    private ResultActions checkout(long id, String token) throws Exception {
        return postJson("/api/management/reservations/" + id + "/checkout", token, Map.of("version", 1, "issueCondition", "GOOD"));
    }
    private long issued(long id) throws Exception { return id(checkout(id, managerToken).andExpect(status().isCreated())); }
    private ResultActions takeBack(long loan, String token, Object body) throws Exception { return postJson("/api/management/loans/" + loan + "/return", token, body); }
    private ResultActions close(long id) throws Exception { return postJson("/api/management/maintenance/" + id + "/close", managerToken, Map.of("note", "Javítva, működőképesség ellenőrizve.")); }
    private long requested(Instant start, Instant end) throws Exception {
        return id(postJson("/api/reservations", ownerToken, Map.of("equipmentId", itemId, "startsAt", start.toString(), "endsAt", end.toString(), "purpose", "Egyetemi interjúprojekt felvétele.")).andExpect(status().isCreated()));
    }
    private long approved(Instant start, Instant end) throws Exception { long id = requested(start, end); approve(id); return id; }
    private void approve(long id) throws Exception { postJson("/api/management/reservations/" + id + "/approve", managerToken, Map.of("version", 0)).andExpect(status().isOk()); }
    private long id(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong(); }
    private ResultActions postJson(String path, String token, Object body) throws Exception { return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))); }
    private ResultActions getJson(String path, String token) throws Exception { return mvc.perform(get(path).header("Authorization", "Bearer " + token)); }
    private long user(String email, Role role, String hash) { var user = new AppUser(); user.setEmail(email); user.setDisplayName("Teszt Felhasználó"); user.setRole(role); user.setPasswordHash(hash); return users.saveAndFlush(user).getId(); }
    private String login(String email) throws Exception { var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "EszkozPont2026!")))).andExpect(status().isOk()); return json.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText(); }
    private OffsetDateTime offset(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private long maintenance(Instant start, Instant end) { return jdbc.queryForObject("insert into maintenance_period(equipment_id,created_by,starts_at,ends_at,reason,status,created_at,updated_at) values (?,?,?,?,'Ellenőrzés','OPEN',now(),now()) returning id", Long.class, itemId, managerId, offset(start), offset(end)); }
}
