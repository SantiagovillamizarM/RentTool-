package com.renttool.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:renttool;MODE=MySQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "spring.jpa.hibernate.ddl-auto=validate",
      "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
      "renttool.upload-dir=${java.io.tmpdir}/renttool-api-test-uploads"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String provider, otherProvider, client, secondClient;
  static long tool;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  JsonNode body(ResultActions result) throws Exception {
    return JSON.readTree(result.andReturn().getResponse().getContentAsString());
  }

  String register(String email, String role) throws Exception {
    return body(
            mvc.perform(
                    post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Persona "
                                + role
                                + "\",\"email\":\""
                                + email
                                + "\",\"password\":\"correct-test-password\",\"role\":\""
                                + role
                                + "\"}"))
                .andExpect(status().isCreated()))
        .get("token")
        .asString();
  }

  long publishWithUnits(String token, int units) throws Exception {
    long id =
        body(
                mvc.perform(
                        post("/api/v1/tools")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"Andamio\",\"categoryId\":4,\"description\":\"Prueba\","
                                    + "\"dailyRate\":40000}"))
                    .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    for (int n = 1; n <= units; n++)
      mvc.perform(
              post("/api/v1/tools/" + id + "/units")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content("{\"code\":\"AND-" + n + "\",\"status\":\"OPERATIVA\"}"))
          .andExpect(status().isCreated());
    return id;
  }

  ResultActions request(String token, long toolId, int from, int to, int quantity)
      throws Exception {
    return mvc.perform(
        post("/api/v1/reservations")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(
                "{\"toolId\":"
                    + toolId
                    + ",\"start\":\""
                    + TODAY.plusDays(from)
                    + "\",\"end\":\""
                    + TODAY.plusDays(to)
                    + "\",\"quantity\":"
                    + quantity
                    + "}"));
  }

  @Test
  @Order(1)
  void availabilityIsPublicAndValidated() throws Exception {
    provider = register("res-provider@test.example", "PROVEEDOR");
    otherProvider = register("res-other@test.example", "PROVEEDOR");
    client = register("res-client@test.example", "CLIENTE");
    secondClient = register("res-client2@test.example", "CLIENTE");
    tool = publishWithUnits(provider, 2);

    mvc.perform(
            get("/api/v1/tools/" + tool + "/availability")
                .param("start", TODAY.plusDays(10).toString())
                .param("end", TODAY.plusDays(13).toString())
                .param("quantity", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("availableUnits").value(2))
        .andExpect(jsonPath("available").value(true))
        .andExpect(jsonPath("days").value(3))
        .andExpect(jsonPath("total").value(240000));
    mvc.perform(
            get("/api/v1/tools/" + tool + "/availability")
                .param("start", TODAY.plusDays(13).toString())
                .param("end", TODAY.plusDays(10).toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/tools/" + tool + "/availability")
                .param("start", TODAY.minusDays(1).toString())
                .param("end", TODAY.plusDays(1).toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(2)
  void fullRequestAcceptCancelFlow() throws Exception {
    mvc.perform(
            post("/api/v1/reservations")
                .contentType("application/json")
                .content("{\"toolId\":" + tool + "}"))
        .andExpect(status().isUnauthorized());
    request(provider, tool, 10, 13, 1).andExpect(status().isForbidden());
    request(client, tool, 10, 13, 3).andExpect(status().isConflict());

    long id =
        body(
                request(client, tool, 10, 13, 2)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("status").value("SOLICITADA"))
                    .andExpect(jsonPath("total").value(240000))
                    .andExpect(jsonPath("toolName").value("Andamio")))
            .get("id")
            .asLong();

    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + secondClient))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1))
        .andExpect(jsonPath("content[0].clientName").value("Persona CLIENTE"));

    mvc.perform(
            post("/api/v1/reservations/" + id + "/accept")
                .header("Authorization", "Bearer " + otherProvider))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/reservations/" + id + "/accept")
                .header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("ACEPTADA"))
        .andExpect(jsonPath("unitIds.length()").value(2));

    // Las dos unidades quedan ocupadas del 10 al 13; el 13 se devuelven y vuelven a estar libres.
    request(secondClient, tool, 12, 14, 1).andExpect(status().isConflict());
    request(secondClient, tool, 13, 15, 1).andExpect(status().isCreated());

    mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", "Bearer " + provider))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("status").value("CANCELADA"))
        .andExpect(jsonPath("unitIds.length()").value(0));
    request(secondClient, tool, 12, 14, 1).andExpect(status().isCreated());
  }

  @Test
  @Order(3)
  void rejectKeepsTheReason() throws Exception {
    long id = body(request(client, tool, 20, 21, 1)).get("id").asLong();
    mvc.perform(
            post("/api/v1/reservations/" + id + "/reject")
                .header("Authorization", "Bearer " + provider)
                .contentType("application/json")
                .content("{\"reason\":\"Equipo en revisión\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("status").value("RECHAZADA"))
        .andExpect(jsonPath("note").value("Equipo en revisión"));
    mvc.perform(
            post("/api/v1/reservations/" + id + "/accept")
                .header("Authorization", "Bearer " + provider))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(4)
  void concurrentAcceptsCannotShareTheLastUnit() throws Exception {
    long single = publishWithUnits(provider, 1);
    long first = body(request(client, single, 30, 32, 1)).get("id").asLong();
    long second = body(request(secondClient, single, 31, 33, 1)).get("id").asLong();

    var start = new CountDownLatch(1);
    Callable<Integer> acceptFirst = () -> accept(start, first);
    Callable<Integer> acceptSecond = () -> accept(start, second);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results = List.of(pool.submit(acceptFirst), pool.submit(acceptSecond));
      start.countDown();
      var statuses = List.of(results.get(0).get(), results.get(1).get());
      assertTrue(statuses.contains(200), "una aceptación gana: " + statuses);
      assertTrue(statuses.contains(409), "la otra encuentra la unidad ocupada: " + statuses);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @Order(5)
  void concurrentAcceptsOfTheSameRequestApplyOnce() throws Exception {
    long tool = publishWithUnits(provider, 2);
    long id = body(request(client, tool, 40, 41, 1)).get("id").asLong();

    var start = new CountDownLatch(1);
    Callable<Integer> acceptIt = () -> accept(start, id);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results = List.of(pool.submit(acceptIt), pool.submit(acceptIt));
      start.countDown();
      var statuses = List.of(results.get(0).get(), results.get(1).get());
      assertTrue(statuses.contains(200) && statuses.contains(409), "solo una vez: " + statuses);
    } finally {
      pool.shutdownNow();
    }
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("unitIds.length()").value(1));
    mvc.perform(
            get("/api/v1/tools/" + tool + "/availability")
                .param("start", TODAY.plusDays(40).toString())
                .param("end", TODAY.plusDays(41).toString()))
        .andExpect(jsonPath("availableUnits").value(1));
  }

  private int accept(CountDownLatch start, long reservationId) throws Exception {
    start.await();
    return mvc.perform(
            post("/api/v1/reservations/" + reservationId + "/accept")
                .header("Authorization", "Bearer " + provider))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  @Order(6)
  void swaggerDocumentsReservations() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("paths['/api/v1/reservations'].post.security[0].bearerAuth").exists())
        .andExpect(
            jsonPath("paths['/api/v1/tools/{toolId}/availability'].get.security").doesNotExist());
  }
}
