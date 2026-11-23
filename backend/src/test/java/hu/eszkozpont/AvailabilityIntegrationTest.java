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
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"JWT_SECRET=availability-tests-only-secret-at-least-32-bytes", "app.demo-data=false", "app.reservations.expiry-enabled=false"})
@AutoConfigureMockMvc
@Import(AvailabilityIntegrationTest.TimeConfiguration.class)
class AvailabilityIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private static final Instant START = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(2, ChronoUnit.HOURS);
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static class TestClock extends Clock {
        private Instant value = NOW;
        void set(Instant value) { this.value = value; }
        @Override public Instant instant() { return value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres"); registry.add("spring.datasource.password", () -> "postgres");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    private String userToken, managerToken, adminToken;
    private long ownerId, managerId, categoryId, locationId, itemId;

    @BeforeEach void fixture() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        String hash = passwords.encode("EszkozPont2026!");
        ownerId = user("owner@test.example", Role.USER, hash);
        user("viewer@test.example", Role.USER, hash);
        managerId = user("manager@test.example", Role.MANAGER, hash);
        user("admin@test.example", Role.ADMIN, hash);
        userToken = login("viewer@test.example"); managerToken = login("manager@test.example"); adminToken = login("admin@test.example");
        categoryId = jdbc.queryForObject("insert into equipment_category(name) values ('Fotó') returning id", Long.class);
        locationId = jdbc.queryForObject("insert into storage_location(code,name,building,room) values ('MEDIA','Médiaműhely','A','101') returning id", Long.class);
        itemId = item("Kamera A", "FOTO-A");
    }

    @Test void anonymousRequestsCannotReadEitherEndpoint() throws Exception {
        mvc.perform(get("/api/equipment").param("startsAt", START.toString()).param("endsAt", END.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/equipment/" + itemId + "/availability").param("from", START.toString()).param("to", END.toString())).andExpect(status().isUnauthorized());
    }

    @Test void calendarRevealsOnlyClippedIntervalsNotAnotherUsersPrivateData() throws Exception {
        long reservation = reservation(itemId, "APPROVED", START.minusSeconds(3600), END.plusSeconds(3600));
        getJson("/api/reservations/" + reservation, userToken).andExpect(status().isNotFound());
        var result = calendar(START, END, userToken).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("equipmentId", "from", "to", "checkedAt", "blockingIntervals", "overdueLoan", "overdueMaintenance");
        var interval = body.get("blockingIntervals").get(0);
        assertThat(interval.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("startsAt", "endsAt", "kind");
        assertThat(interval.get("startsAt").asText()).isEqualTo(START.toString());
        assertThat(interval.get("endsAt").asText()).isEqualTo(END.toString());
        assertThat(interval.get("kind").asText()).isEqualTo("RESERVATION");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Titkos", "owner@test.example", "requester", "reason", "reservationId");
    }

    @Test void allThreeRolesSeeTheSameAnonymousCalendar() throws Exception {
        reservation(itemId, "APPROVED", START, END);
        String expected = calendar(START, END, userToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String token : List.of(managerToken, adminToken)) assertThat(calendar(START, END, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(expected);
    }

    @Test void onlyApprovedReservationsAndOpenMaintenanceBlockTheCalendar() throws Exception {
        for (String state : List.of("REQUESTED", "CANCELLED", "EXPIRED", "COMPLETED", "REJECTED")) reservation(itemId, state, START, END);
        maintenance(itemId, "CLOSED", START, END); maintenance(itemId, "CANCELLED", START, END);
        calendar(START, END, userToken).andExpect(jsonPath("$.blockingIntervals.length()").value(0));
        reservation(itemId, "APPROVED", START, START.plusSeconds(3600));
        maintenance(itemId, "OPEN", START.plusSeconds(3600), END);
        calendar(START, END, userToken).andExpect(jsonPath("$.blockingIntervals.length()").value(2))
                .andExpect(jsonPath("$.blockingIntervals[0].kind").value("RESERVATION")).andExpect(jsonPath("$.blockingIntervals[1].kind").value("MAINTENANCE"));
    }

    @Test void adjoiningIntervalsDoNotAppearInsideTheRequestedWindow() throws Exception {
        reservation(itemId, "APPROVED", START.minusSeconds(3600), START);
        reservation(itemId, "APPROVED", END, END.plusSeconds(3600));
        maintenance(itemId, "OPEN", END, END.plusSeconds(3600));
        calendar(START, END, userToken).andExpect(jsonPath("$.blockingIntervals.length()").value(0));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test void malformedMissingReversedAndTooLongCalendarRangesFail() throws Exception {
        mvc.perform(get(calendarUrl()).header("Authorization", "Bearer " + userToken).param("from", START.toString())).andExpect(status().isBadRequest());
        mvc.perform(get(calendarUrl()).header("Authorization", "Bearer " + userToken).param("from", "hibas").param("to", END.toString())).andExpect(status().isBadRequest());
        calendar(START, START, userToken).andExpect(status().isBadRequest());
        calendar(END, START, userToken).andExpect(status().isBadRequest());
        calendar(START, START.plus(90, ChronoUnit.DAYS).plusSeconds(1), userToken).andExpect(status().isBadRequest());
        calendar(START, START.plus(90, ChronoUnit.DAYS), userToken).andExpect(status().isOk());
    }

    @Test void calendarAllowsCurrentWeeksPastDaysButDoesNotWriteHistory() throws Exception {
        reservation(itemId, "APPROVED", START, END);
        calendar(NOW.minus(2, ChronoUnit.DAYS), NOW.plus(5, ChronoUnit.DAYS), userToken).andExpect(status().isOk());
        calendar(START, END, userToken).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select version from reservation", Long.class)).isZero();
    }

    @Test void inactiveEquipmentOrReferenceAndMissingIdsReturn404() throws Exception {
        jdbc.update("update equipment_item set lifecycle='RETIRED' where id=?", itemId);
        calendar(START, END, userToken).andExpect(status().isNotFound());
        jdbc.update("update equipment_item set lifecycle='ACTIVE' where id=?", itemId);
        jdbc.update("update equipment_category set active=false where id=?", categoryId);
        calendar(START, END, managerToken).andExpect(status().isNotFound());
        jdbc.update("update equipment_category set active=true where id=?", categoryId);
        jdbc.update("update storage_location set active=false where id=?", locationId);
        calendar(START, END, adminToken).andExpect(status().isNotFound());
        mvc.perform(get("/api/equipment/99999/availability").header("Authorization", "Bearer " + userToken).param("from", START.toString()).param("to", END.toString())).andExpect(status().isNotFound());
    }

    @Test void searchRequiresBothDatesAndValidReservationPeriods() throws Exception {
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken).param("startsAt", START.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken).param("endsAt", END.toString())).andExpect(status().isBadRequest());
        for (Instant end : List.of(START, START.minusSeconds(3600), START.plusSeconds(3540), START.plus(14, ChronoUnit.DAYS).plusSeconds(60), END.plusSeconds(1))) {
            search(START, end).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        }
        search(NOW, NOW.plusSeconds(3600)).andExpect(status().isBadRequest());
        search(NOW.plus(60, ChronoUnit.DAYS).plusSeconds(60), NOW.plus(60, ChronoUnit.DAYS).plusSeconds(3660)).andExpect(status().isBadRequest());
        search(START.plusNanos(1), END).andExpect(status().isBadRequest());
        search(START, START.plusSeconds(3600)).andExpect(status().isOk());
        search(NOW.plus(60, ChronoUnit.DAYS), NOW.plus(74, ChronoUnit.DAYS)).andExpect(status().isOk());
    }

    @Test void unfilteredCatalogueStillListsBusyEquipment() throws Exception {
        reservation(itemId, "APPROVED", START, END);
        getJson("/api/equipment", userToken).andExpect(jsonPath("$.page.totalElements").value(1));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test void pendingAndFinishedRequestsDoNotExcludeSearchResults() throws Exception {
        for (String state : List.of("REQUESTED", "CANCELLED", "EXPIRED", "COMPLETED", "REJECTED")) reservation(itemId, state, START, END);
        search(START, END).andExpect(jsonPath("$.content[0].id").value(itemId));
    }

    @Test void openMaintenanceExcludesResultsButClosedAndCancelledDoNot() throws Exception {
        maintenance(itemId, "CLOSED", START, END); maintenance(itemId, "CANCELLED", START, END);
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
        maintenance(itemId, "OPEN", START.plusSeconds(60), END.minusSeconds(60));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test void overdueMaintenanceBlocksEvenANonOverlappingFutureWindow() throws Exception {
        maintenance(itemId, "OPEN", NOW.minusSeconds(7200), NOW);
        calendar(START, END, userToken).andExpect(jsonPath("$.overdueMaintenance").value(true)).andExpect(jsonPath("$.blockingIntervals.length()").value(0));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(0));
        jdbc.update("update maintenance_period set status='CLOSED', closed_at=?", NOW.atOffset(ZoneOffset.UTC));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test void overdueLoanBoundaryIsStrictAndReturnReleasesFutureSearch() throws Exception {
        long reservation = reservation(itemId, "APPROVED", NOW.minusSeconds(7200), NOW);
        loan(reservation, NOW.minusSeconds(7200), NOW);
        calendar(START, END, userToken).andExpect(jsonPath("$.overdueLoan").value(false));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
        clock.set(NOW.plus(1, ChronoUnit.MICROS));
        calendar(START, END, userToken).andExpect(jsonPath("$.overdueLoan").value(true));
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(0));
        jdbc.update("update loan set returned_at=?,returned_by=?,return_condition='GOOD'", NOW.atOffset(ZoneOffset.UTC), managerId);
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test void databaseFiltersBeforePagingAndCountsOnlyAvailableMatches() throws Exception {
        long b = item("Kamera B", "FOTO-B"); long c = item("Kamera C", "FOTO-C"); long d = item("Kamera D", "FOTO-D");
        reservation(itemId, "APPROVED", START, END); maintenance(b, "OPEN", START, END);
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken)
                .param("startsAt", START.toString()).param("endsAt", END.toString()).param("q", "Kamera")
                .param("categoryId", String.valueOf(categoryId)).param("locationId", String.valueOf(locationId))
                .param("size", "1").param("page", "1").param("sort", "name,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(d));
        assertThat(c).isNotEqualTo(d);
    }

    @Test void archivedAndRetiredEquipmentStayHiddenWithDateFilters() throws Exception {
        long retired = item("Kivont", "OLD-1"); jdbc.update("update equipment_item set lifecycle='RETIRED' where id=?", retired);
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
        jdbc.update("update storage_location set active=false where id=?", locationId);
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test void offsetDatesReferToTheSameAbsolutePeriod() throws Exception {
        reservation(itemId, "APPROVED", START, END);
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken)
                .param("startsAt", START.atOffset(ZoneOffset.ofHours(2)).toString()).param("endsAt", END.atOffset(ZoneOffset.ofHours(2)).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test void aSearchDoesNotReserveTheItemAndTheWriteChecksAgain() throws Exception {
        search(START, END).andExpect(jsonPath("$.page.totalElements").value(1));
        reservation(itemId, "APPROVED", START, END);
        mvc.perform(post("/api/reservations").header("Authorization", "Bearer " + userToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("equipmentId", itemId, "startsAt", START.toString(), "endsAt", END.toString(), "purpose", "Egyetemi fotós projekt"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from reservation", Integer.class)).isEqualTo(1);
    }

    private long user(String email, Role role, String hash) {
        var user = new AppUser(); user.setDisplayName("Titkos Név"); user.setEmail(email); user.setRole(role); user.setPasswordHash(hash);
        return users.saveAndFlush(user).getId();
    }
    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", "EszkozPont2026!")))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private long item(String name, String code) {
        return jdbc.queryForObject("insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) values (?,?,?,?,now(),now()) returning id", Long.class, code, name, categoryId, locationId);
    }
    private long reservation(long equipmentId, String state, Instant start, Instant end) {
        return jdbc.queryForObject("insert into reservation(requester_id,equipment_id,starts_at,ends_at,purpose,status,decision_reason,created_at,updated_at) values (?,?,?,?,'Titkos projekt',?,'Titkos indok',now(),now()) returning id", Long.class, ownerId, equipmentId, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC), state);
    }
    private void maintenance(long equipmentId, String state, Instant start, Instant end) {
        jdbc.update("insert into maintenance_period(equipment_id,created_by,starts_at,ends_at,reason,status,closed_at,created_at,updated_at) values (?,?,?,?,'Titkos javítás',?,?,now(),now())", equipmentId, managerId, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC), state, state.equals("CLOSED") ? NOW.atOffset(ZoneOffset.UTC) : null);
    }
    private void loan(long reservationId, Instant issued, Instant due) {
        jdbc.update("insert into loan(reservation_id,equipment_id,issued_by,issued_at,due_at,issue_condition) values (?,?,?,?,?,'GOOD')", reservationId, itemId, managerId, issued.atOffset(ZoneOffset.UTC), due.atOffset(ZoneOffset.UTC));
    }
    private String calendarUrl() { return "/api/equipment/" + itemId + "/availability"; }
    private ResultActions calendar(Instant from, Instant to, String token) throws Exception {
        return mvc.perform(get(calendarUrl()).header("Authorization", "Bearer " + token).param("from", from.toString()).param("to", to.toString()));
    }
    private ResultActions search(Instant start, Instant end) throws Exception {
        return mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken).param("startsAt", start.toString()).param("endsAt", end.toString()));
    }
    private ResultActions getJson(String url, String token) throws Exception { return mvc.perform(get(url).header("Authorization", "Bearer " + token)); }
}
