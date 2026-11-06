package hu.eszkozpont;

import hu.eszkozpont.user.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"JWT_SECRET=scheduler-tests-only-secret-at-least-32-bytes", "app.demo-data=false", "app.reservations.expiry-enabled=true", "app.reservations.expiry-delay=PT0.1S"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationExpirySchedulerIntegrationTest {
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres"); registry.add("spring.datasource.password", () -> "postgres");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void theActualScheduledJobExpiresAnUncollectedRequestWithoutAnHttpCommand() throws Exception {
        var user = new AppUser(); user.setDisplayName("Ütemező Teszt"); user.setEmail("scheduler@test.example"); user.setPasswordHash(passwords.encode("EszkozPont2026!"));
        long userId = users.saveAndFlush(user).getId();
        long category = jdbc.queryForObject("insert into equipment_category(name) values ('Teszt') returning id", Long.class);
        long location = jdbc.queryForObject("insert into storage_location(code,name,building,room) values ('TEST','Teszt','A','1') returning id", Long.class);
        long item = jdbc.queryForObject("insert into equipment_item(inventory_code,name,category_id,location_id,created_at,updated_at) values ('TEST-1','Teszt',?,?,now(),now()) returning id", Long.class, category, location);
        var now = Instant.now();
        long reservation = jdbc.queryForObject("insert into reservation(requester_id,equipment_id,starts_at,ends_at,purpose,status,created_at,updated_at) values (?,?,?,?,'Automatikus lejárat próbája','REQUESTED',now(),now()) returning id", Long.class,
                userId, item, now.minusSeconds(7200).atOffset(ZoneOffset.UTC), now.minusSeconds(60).atOffset(ZoneOffset.UTC));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("select status from reservation where id=?", String.class, reservation)).isEqualTo("EXPIRED"));
        await().during(Duration.ofMillis(350)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(jdbc.queryForObject("select count(*) from audit_event where reservation_id=?", Integer.class, reservation)).isEqualTo(1));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", "EszkozPont2026!")))).andExpect(status().isOk()).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/reservations/" + reservation).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/reservations/" + reservation + "/events").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("RESERVATION_EXPIRED")).andExpect(jsonPath("$.content[0].actor").isEmpty());
    }
}
