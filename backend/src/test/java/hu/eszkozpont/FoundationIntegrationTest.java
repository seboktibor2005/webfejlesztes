package hu.eszkozpont;

import hu.eszkozpont.user.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"JWT_SECRET=test-only-jwt-secret-at-least-32-bytes-long", "app.demo-data=false", "app.reservations.expiry-enabled=false"})
@AutoConfigureMockMvc
class FoundationIntegrationTest {
    private static final EmbeddedPostgres POSTGRES;
    static {
        try { POSTGRES = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1").start(); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
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
    @Autowired JwtEncoder jwtEncoder;
    private String userToken;
    private String managerToken;
    private String adminToken;
    private long category;
    private long location;
    private long equipment;

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute("TRUNCATE audit_event, loan, maintenance_period, reservation, equipment_item, equipment_category, storage_location, app_user RESTART IDENTITY CASCADE");
        userToken = createAndLogin("user@test.example", Role.USER);
        managerToken = createAndLogin("manager@test.example", Role.MANAGER);
        adminToken = createAndLogin("admin@test.example", Role.ADMIN);
        category = jdbc.queryForObject("INSERT INTO equipment_category(name) VALUES ('Fotó') RETURNING id", Long.class);
        location = jdbc.queryForObject("INSERT INTO storage_location(code,name,building,room) VALUES ('MEDIA','Médiaműhely','A épület','101') RETURNING id", Long.class);
        equipment = item("FOTO-001", "Canon kamera", "ACTIVE");
        item("FOTO-002", "Sony kamera", "ACTIVE");
        item("FOTO-003", "Régi kamera", "RETIRED");
    }

    @Test void registrationNormalizesEmailHashesPasswordAndNeverReturnsIt() throws Exception {
        var result = register("ANNA@EXAMPLE.TEST", "SajatJelszo2026!").andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/me"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value("anna@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        var saved = users.findByEmail("anna@example.test").orElseThrow();
        assertThat(passwords.matches("SajatJelszo2026!", saved.getPasswordHash())).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("SajatJelszo2026!");
        register("anna@example.test", "SajatJelszo2026!").andExpect(status().isConflict());
    }

    @Test void invalidRegistrationAndPrivilegeInjectionAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"email\":\"hibas\",\"password\":\"rovid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(content().string(not(containsString("rovid"))));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Anna\",\"email\":\"anna@example.test\",\"password\":\"SajatJelszo2026!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        register("anna@example.test", "á".repeat(40)).andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(3);
    }

    @Test void loginFailureAndDisabledAccountHaveTheSameResponse() throws Exception {
        login("user@test.example", "rosszjelszo").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("missing@test.example", "EszkozPont2026!").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        jdbc.update("UPDATE app_user SET enabled=false WHERE email='user@test.example'");
        login("user@test.example", "EszkozPont2026!").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test void actualJwtAuthenticatesAndLogoutRevokesIt() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + userToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test void profileUpdateChangesOnlyTheAuthenticatedUsersNameAndKeepsTheirSession() throws Exception {
        mvc.perform(put("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Név\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Név\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + userToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"  Kovács Anna  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Kovács Anna"))
                .andExpect(jsonPath("$.email").value("user@test.example")).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Kovács Anna"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Teszt Felhasználó"));
    }

    @Test void passwordChangeChecksTheCurrentPasswordAndRevokesAllPreviousSessions() throws Exception {
        var email = "user@test.example";
        var previousPassword = "EszkozPont2026!";
        var nextPassword = "UjJelszo2026!";
        var secondLogin = login(email, previousPassword).andExpect(status().isOk()).andReturn().getResponse();
        var secondToken = json.readTree(secondLogin.getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", previousPassword, "newPassword", nextPassword))))
                .andExpect(status().isUnauthorized());
        changePassword("helytelen jelszó", nextPassword)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INCORRECT_CURRENT_PASSWORD"))
                .andExpect(content().string(not(containsString(nextPassword))));
        changePassword(previousPassword, "á".repeat(40)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("newPassword")));
        changePassword(previousPassword, previousPassword).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
        changePassword(previousPassword, nextPassword).andExpect(status().isNoContent());
        for (var token : List.of(userToken, secondToken)) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(passwords.matches(nextPassword, user.getPasswordHash())).isTrue();
        login(email, previousPassword).andExpect(status().isUnauthorized());
        login(email, nextPassword).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + managerToken)).andExpect(status().isOk());
    }

    @Test void disabledUserAndChangedRoleInvalidateExistingTokens() throws Exception {
        jdbc.update("UPDATE app_user SET enabled=false WHERE email='user@test.example'");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE app_user SET role='USER' WHERE email='admin@test.example'");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken)).andExpect(status().isUnauthorized());
    }

    @Test void anonymousAndTamperedTokensAreUnauthorized() throws Exception {
        mvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken.substring(0, userToken.lastIndexOf('.') + 1) + "invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void signedButExpiredOrWrongAudienceOrMalformedSubjectTokensAreRejected() throws Exception {
        var user = users.findByEmail("user@test.example").orElseThrow();
        var now = Instant.now();
        for (int scenario = 0; scenario < 4; scenario++) {
            var claims = JwtClaimsSet.builder().issuer("eszkozpont")
                    .subject(scenario == 2 ? "not-a-user-id" : user.getId().toString())
                    .issuedAt(now.minusSeconds(60)).expiresAt(scenario == 0 ? now.minusSeconds(1) : now.plusSeconds(60))
                    .claim("role", "USER").claim("tokenVersion", 0);
            if (scenario != 3) claims.audience(List.of(scenario == 1 ? "another-app" : "eszkozpont-web"));
            String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test void rolesAreEnforcedOnTheBackend() throws Exception {
        mvc.perform(get("/api/management/equipment").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + managerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/management/equipment").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(3));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test void catalogHasRealRelationshipsAndHidesRetiredItems() throws Exception {
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].category.name").value("Fotó"))
                .andExpect(jsonPath("$.content[0].location.room").value("101"));
        mvc.perform(get("/api/equipment/" + equipment).header("Authorization", "Bearer " + userToken)).andExpect(status().isOk());
        mvc.perform(get("/api/equipment/99999").header("Authorization", "Bearer " + userToken)).andExpect(status().isNotFound());
    }

    @Test void searchingFilteringPaginationAndSortValidationWork() throws Exception {
        mvc.perform(get("/api/equipment").param("q", "CANON").param("categoryId", String.valueOf(category))
                .param("locationId", String.valueOf(location)).param("size", "1").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("Canon kamera"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/equipment").param("q", "%").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        for (var params : List.of(Map.of("size", "101"), Map.of("page", "-1"), Map.of("sort", "category.passwordHash,asc"))) {
            var request = get("/api/equipment").header("Authorization", "Bearer " + userToken);
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
    }

    @Test void inactiveCategoryHidesItsEquipmentAndUntrustedCorsOriginIsRejected() throws Exception {
        jdbc.update("UPDATE equipment_category SET active=false WHERE id=?", category);
        mvc.perform(get("/api/equipment").header("Authorization", "Bearer " + userToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(options("/api/equipment").header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test void concurrentRegistrationsCannotDuplicateNormalizedEmail() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of("anna@example.test", "ANNA@EXAMPLE.TEST").stream().map(email -> executor.submit(() -> {
                ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return register(email, "SajatJelszo2026!").andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("displayName", "Minta Anna", "email", email, "password", password))));
    }
    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions changePassword(String currentPassword, String newPassword) throws Exception {
        return mvc.perform(post("/api/me/password").header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }
    private String createAndLogin(String email, Role role) throws Exception {
        var user = new AppUser(); user.setDisplayName("Teszt Felhasználó"); user.setEmail(email);
        user.setRole(role); user.setPasswordHash(passwords.encode("EszkozPont2026!")); users.saveAndFlush(user);
        var response = login(email, "EszkozPont2026!").andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("accessToken").asText();
    }
    private long item(String code, String name, String lifecycle) {
        return jdbc.queryForObject("INSERT INTO equipment_item(inventory_code,name,category_id,location_id,lifecycle,created_at,updated_at) VALUES (?,?,?,?,?,now(),now()) RETURNING id",
                Long.class, code, name, category, location, lifecycle);
    }
}
