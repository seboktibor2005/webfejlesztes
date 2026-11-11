package hu.eszkozpont;

import hu.eszkozpont.dashboard.DashboardService;
import hu.eszkozpont.user.AppUser;
import hu.eszkozpont.user.Role;
import hu.eszkozpont.user.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=dashboard-tests-only-secret-at-least-32-bytes",
        "app.demo-data=false",
        "app.reservations.expiry-enabled=false"
})
@AutoConfigureMockMvc
@Import(DashboardIntegrationTest.TimeConfiguration.class)
class DashboardIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private static final String PASSWORD = "EszkozPont2026!";
    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static class TestClock extends Clock {
        private Instant time = NOW;
        @Override public Instant instant() { return time; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary TestClock dashboardClock() { return new TestClock(); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired DashboardService dashboard;
    @Autowired TestClock clock;
    private long owner;
    private long other;
    private long manager;
    private long photo;
    private long audio;
    private long location;

    @BeforeEach
    void fixture() {
        clock.time = NOW;
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        String hash = passwords.encode(PASSWORD);
        owner = user("owner", Role.USER, hash);
        other = user("other", Role.USER, hash);
        manager = user("manager", Role.MANAGER, hash);
        user("admin", Role.ADMIN, hash);
        photo = jdbc.queryForObject("insert into equipment_category(name) values ('Fotó') returning id", Long.class);
        audio = jdbc.queryForObject("insert into equipment_category(name) values ('Hang') returning id", Long.class);
        location = jdbc.queryForObject("""
                insert into storage_location(code,name,building,room)
                values ('MEDIA','Médiaműhely','A','101') returning id
                """, Long.class);
    }

    @Test
    void bothRoutesRequireLoginAndInstitutionalDataRequiresStaff() throws Exception {
        mvc.perform(get("/api/dashboard/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/management/dashboard")).andExpect(status().isUnauthorized());
        request("/api/management/dashboard", "owner").andExpect(status().isForbidden());
    }

    @Test
    void emptyDashboardReturnsZeroCountsAndNoAppointments() throws Exception {
        request("/api/dashboard/me", "owner").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.activeLoanCount").value(0))
                .andExpect(jsonPath("$.nextPickup").isEmpty())
                .andExpect(jsonPath("$.nextReturn").isEmpty());
        request("/api/management/dashboard", "manager").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.todayPickupCount").value(0))
                .andExpect(jsonPath("$.todayReturnCount").value(0))
                .andExpect(jsonPath("$.overdueLoanCount").value(0))
                .andExpect(jsonPath("$.maintenanceCount").value(0))
                .andExpect(jsonPath("$.loansByCategory").isEmpty());
    }

    @Test
    void personalDashboardUsesOnlyTheJwtSubjectAndShowsEarliestOutstandingTasks() throws Exception {
        long camera = item("Kamera", photo);
        long privateItem = item("Másik felhasználó eszköze", audio);
        reservation(owner, camera, "REQUESTED", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        reservation(owner, camera, "REQUESTED", NOW, NOW.plusSeconds(7200));
        reservation(other, privateItem, "REQUESTED", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        reservation(owner, camera, "APPROVED", NOW.minusSeconds(7200), NOW);
        long pickup = reservation(owner, camera, "APPROVED", NOW.minusSeconds(1800), NOW.plusSeconds(1800));
        reservation(owner, camera, "APPROVED", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        reservation(other, privateItem, "APPROVED", NOW.minusSeconds(3600), NOW.plusSeconds(1800));
        long earliestReturn = loan(owner, item("Mikrofon", audio), NOW.minusSeconds(10800), NOW.minusSeconds(3600), null);
        loan(owner, item("Objektív", photo), NOW.minusSeconds(14400), NOW.plusSeconds(3600), null);
        loan(owner, item("Visszahozott kamera", photo), NOW.minusSeconds(18000), NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        loan(other, privateItem, NOW.minusSeconds(21600), NOW.minusSeconds(10800), null);

        String body = request("/api/dashboard/me?userId=" + other, "owner").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.activeLoanCount").value(2))
                .andExpect(jsonPath("$.nextPickup.reservationId").value(pickup))
                .andExpect(jsonPath("$.nextPickup.equipmentName").value("Kamera"))
                .andExpect(jsonPath("$.nextReturn.reservationId").value(earliestReturn))
                .andExpect(jsonPath("$.nextReturn.equipmentName").value("Mikrofon"))
                .andExpect(jsonPath("$.todayPickupCount").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Másik felhasználó", "requester", "@test.example", "purpose");
    }

    @Test
    void alreadyCollectedReservationDoesNotBecomeTheNextPickup() throws Exception {
        long camera = item("Kamera", photo);
        long collected = loan(owner, camera, NOW.minusSeconds(3600), NOW.plusSeconds(3600), null);
        long next = reservation(owner, camera, "APPROVED", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        request("/api/dashboard/me", "owner").andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPickup.reservationId").value(next))
                .andExpect(jsonPath("$.nextReturn.reservationId").value(collected));
    }

    @Test
    void managerAndAdminGetTheSameInstitutionalCountsWithoutPersonalFields() throws Exception {
        long camera = item("Kamera", photo);
        reservation(owner, camera, "REQUESTED", NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        reservation(other, camera, "REQUESTED", NOW.plusSeconds(7200), NOW.plusSeconds(10800));
        loan(owner, camera, NOW.minusSeconds(7200), NOW.minusSeconds(3600), null);
        maintenance(camera, "OPEN");
        maintenance(camera, "CANCELLED");
        maintenance(camera, "CLOSED");

        String staffBody = request("/api/management/dashboard", "manager").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.overdueLoanCount").value(1))
                .andExpect(jsonPath("$.maintenanceCount").value(1))
                .andExpect(jsonPath("$.loansByCategory[0].categoryName").value("Fotó"))
                .andReturn().getResponse().getContentAsString();
        assertThat(request("/api/management/dashboard", "admin").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo(staffBody);
        assertThat(staffBody).doesNotContain("requester", "purpose", "reservationId", "@test.example");
    }

    @Test
    void budapestDayUsesBothMidnightsDuringThe25HourAutumnDay() {
        var start = Instant.parse("2026-10-24T22:00:00Z");
        var end = Instant.parse("2026-10-25T23:00:00Z");
        var now = Instant.parse("2026-10-25T22:30:00Z");
        clock.time = now;
        long camera = item("Kamera", photo);
        reservation(owner, camera, "APPROVED", start, now.plusSeconds(60));
        reservation(owner, camera, "APPROVED", now.plusSeconds(60), end);
        reservation(owner, camera, "APPROVED", end, end.plusSeconds(3600));
        reservation(owner, camera, "APPROVED", start, now);
        reservation(owner, camera, "APPROVED", start.minusSeconds(60), end);
        loan(owner, item("Határidő az első éjfélkor", audio), start.minusSeconds(3600), start, null);
        loan(owner, item("Most esedékes", audio), now.minusSeconds(3600), now, null);
        loan(owner, item("Határidő a következő éjfélkor", audio), now.minusSeconds(3600), end, null);
        loan(owner, item("Ma visszahozva", audio), now.minusSeconds(3600), now, now);

        var result = dashboard.management();
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(result.todayPickupCount()).isEqualTo(2);
        assertThat(result.todayReturnCount()).isEqualTo(2);
        assertThat(result.overdueLoanCount()).isEqualTo(1);
    }

    @Test
    void categoryTotalsCountActualIssuesWithinTheThirtyCalendarDays() {
        clock.time = Instant.parse("2026-10-25T22:30:00Z");
        var from = Instant.parse("2026-09-25T22:00:00Z");
        long camera = item("Kamera", photo);
        long microphone = item("Mikrofon", audio);
        loan(owner, camera, from.minusSeconds(1), from.plusSeconds(7200), from.plusSeconds(3600));
        loan(owner, camera, from, from.plusSeconds(7200), from.plusSeconds(3600));
        loan(owner, camera, clock.time, clock.time.plusSeconds(7200), null);
        loan(other, microphone, from.plusSeconds(3600), from.plusSeconds(7200), from.plusSeconds(7200));
        loan(other, item("Jövőbeli próbarekord", audio), clock.time.plusSeconds(1), clock.time.plusSeconds(3600), null);
        reservation(owner, camera, "APPROVED", clock.time.plusSeconds(3600), clock.time.plusSeconds(7200));

        var result = dashboard.management();
        assertThat(result.historyFrom()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(result.loansByCategory()).extracting("categoryName").containsExactly("Fotó", "Hang");
        assertThat(result.loansByCategory()).extracting("count").containsExactly(2L, 1L);
    }

    private long user(String name, Role role, String hash) {
        var user = new AppUser();
        user.setDisplayName(name);
        user.setEmail(name + "@test.example");
        user.setRole(role);
        user.setPasswordHash(hash);
        return users.saveAndFlush(user).getId();
    }

    private ResultActions request(String path, String user) throws Exception {
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", user + "@test.example", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private long item(String name, long categoryId) {
        return jdbc.queryForObject("""
                insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at)
                values ('TESZT-' || nextval('equipment_item_id_seq'),?,?,?,now(),now()) returning id
                """, Long.class, name, categoryId, location);
    }

    private long reservation(long ownerId, long equipmentId, String state, Instant start, Instant end) {
        return jdbc.queryForObject("""
                insert into reservation(requester_id,equipment_id,starts_at,ends_at,purpose,status,created_at,updated_at)
                values (?,?,?,?,'Dashboard próba',?,now(),now()) returning id
                """, Long.class, ownerId, equipmentId, utc(start), utc(end), state);
    }

    private long loan(long ownerId, long equipmentId, Instant issued, Instant due, Instant returned) {
        long reservationId = reservation(ownerId, equipmentId, returned == null ? "APPROVED" : "COMPLETED", issued, due);
        jdbc.update("""
                insert into loan(reservation_id,equipment_id,issued_by,issued_at,due_at,issue_condition,returned_at,returned_by,return_condition)
                values (?,?,?,?,?,'GOOD',?,?,?)
                """, reservationId, equipmentId, manager, utc(issued), utc(due),
                returned == null ? null : utc(returned), returned == null ? null : manager, returned == null ? null : "GOOD");
        return reservationId;
    }

    private void maintenance(long equipmentId, String state) {
        jdbc.update("""
                insert into maintenance_period(equipment_id,created_by,starts_at,ends_at,reason,status,closed_at,created_at,updated_at)
                values (?,?,?,?,'Dashboard próba',?,?,now(),now())
                """, equipmentId, manager, utc(NOW.minusSeconds(3600)), utc(NOW.plusSeconds(3600)),
                state, state.equals("CLOSED") ? utc(NOW) : null);
    }

    private OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
