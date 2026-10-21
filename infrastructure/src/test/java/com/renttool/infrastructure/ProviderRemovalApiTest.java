package com.renttool.infrastructure;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:renttool;MODE=MySQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "spring.jpa.hibernate.ddl-auto=validate",
      "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
      "renttool.upload-dir=${java.io.tmpdir}/renttool-api-test-uploads",
      TestAccounts.ADMIN_EMAIL_PROPERTY,
      TestAccounts.ADMIN_PASSWORD_PROPERTY
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProviderRemovalApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));
  private static final String EMAIL = "rm-provider@test.example";

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String admin, provider, client;
  static long providerId, toolA, toolB, pending, upcoming, inProgress;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  long id(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
  }

  long publish(String name) throws Exception {
    long tool =
        id(
            mvc.perform(
                    post("/api/v1/tools")
                        .header("Authorization", "Bearer " + provider)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\""
                                + name
                                + "\",\"categoryId\":2,\"description\":\"Prueba\","
                                + "\"dailyRate\":30000}"))
                .andExpect(status().isCreated()));
    for (int n = 1; n <= 3; n++)
      mvc.perform(
              post("/api/v1/tools/" + tool + "/units")
                  .header("Authorization", "Bearer " + provider)
                  .contentType("application/json")
                  .content("{\"code\":\"RM-" + n + "\",\"status\":\"OPERATIVA\"}"))
          .andExpect(status().isCreated());
    return tool;
  }

  long reserve(long tool, int from, int to) throws Exception {
    return id(
        mvc.perform(
                post("/api/v1/reservations")
                    .header("Authorization", "Bearer " + client)
                    .contentType("application/json")
                    .content(
                        "{\"toolId\":"
                            + tool
                            + ",\"start\":\""
                            + TODAY.plusDays(from)
                            + "\",\"end\":\""
                            + TODAY.plusDays(to)
                            + "\",\"quantity\":1}"))
            .andExpect(status().isCreated()));
  }

  void accept(long reservation) throws Exception {
    TestAccounts.pay(mvc, client, reservation);
    mvc.perform(
            post("/api/v1/reservations/" + reservation + "/accept")
                .header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());
  }

  @Test
  @Order(1)
  void previewWarnsWhatWillHappenWithoutChangingAnything() throws Exception {
    admin = TestAccounts.admin(mvc);
    provider = TestAccounts.provider(mvc, EMAIL);
    client = TestAccounts.client(mvc, "rm-client@test.example");
    providerId =
        JSON.readTree(
                mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + provider))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    toolA = publish("Retiro A");
    toolB = publish("Retiro B");
    pending = reserve(toolA, 3, 5);
    upcoming = reserve(toolA, 6, 8);
    accept(upcoming);
    inProgress = reserve(toolB, 0, 2);
    accept(inProgress);

    mvc.perform(
            get("/api/v1/admin/providers/" + providerId + "/removal")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("activeTools").value(2))
        .andExpect(jsonPath("pendingRequests").value(1))
        .andExpect(jsonPath("upcomingRentals").value(1))
        .andExpect(jsonPath("rentalsInProgress").value(1))
        .andExpect(jsonPath("taxId").exists());
    mvc.perform(
            get("/api/v1/admin/providers/" + providerId + "/removal")
                .header("Authorization", "Bearer " + client))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/tools/" + toolA)).andExpect(status().isOk());
  }

  @Test
  @Order(2)
  void removalRetiresCatalogCancelsReservationsAndClosesTheAccount() throws Exception {
    mvc.perform(delete("/api/v1/admin/providers/" + providerId).header("Authorization", "Bearer " + client))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/admin/providers/" + providerId).header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("activeTools").value(2))
        .andExpect(jsonPath("pendingRequests").value(1))
        .andExpect(jsonPath("upcomingRentals").value(1))
        .andExpect(jsonPath("rentalsInProgress").value(1));

    // Catálogo público: sus herramientas ya no aparecen.
    mvc.perform(get("/api/v1/tools/" + toolA)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/tools/" + toolB)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/tools?q=Retiro")).andExpect(jsonPath("totalElements").value(0));

    // Reservas: se cancelan con motivo las que no empezaron; la que está en curso sigue.
    for (long r : new long[] {pending, upcoming})
      mvc.perform(get("/api/v1/reservations/" + r).header("Authorization", "Bearer " + client))
          .andExpect(jsonPath("status").value("CANCELADA"))
          .andExpect(jsonPath("note").value("El proveedor se retiró de la plataforma"));
    mvc.perform(get("/api/v1/reservations/" + inProgress).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("status").value("ACEPTADA"));
    mvc.perform(
            get("/api/v1/reservations/" + upcoming + "/history")
                .header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("$[2].action").value("CANCELADA"))
        .andExpect(jsonPath("$[2].actorRole").value("ADMINISTRADOR"))
        .andExpect(jsonPath("$[2].detail").value("El proveedor se retiró de la plataforma"));

    // Cuenta: su sesión deja de servir y no puede volver a entrar.
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + provider))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    "{\"email\":\"" + EMAIL + "\",\"password\":\"" + TestAccounts.PASSWORD + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(3)
  void removedProviderDisappearsFromAdministration() throws Exception {
    var list =
        mvc.perform(get("/api/v1/admin/providers?size=100").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Assertions.assertFalse(list.contains(EMAIL));
    mvc.perform(get("/api/v1/admin/providers/" + providerId).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/admin/providers/" + providerId).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/admin/providers/" + providerId + "/status")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"active\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/admin/providers")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(TestAccounts.providerPayload(EMAIL, "temporal-password")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("detail")
                .value("Este correo pertenece a un proveedor eliminado; usa otro correo"));
  }
}
