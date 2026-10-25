package com.renttool.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.domain.inventory.UnitStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import tools.jackson.databind.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:operations;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
    TestAccounts.ADMIN_EMAIL_PROPERTY, TestAccounts.ADMIN_PASSWORD_PROPERTY,
    // El reloj de la prueba se mueve a mano: las revisiones automáticas se llaman explícitamente.
    "renttool.reservations.delivery-check=PT1000H", "renttool.reservations.expiry-check=PT1000H"
})
@Import(RentalOperationsApiTest.TimeConfig.class)
class RentalOperationsApiTest {
  static class TestClock extends Clock {
    Instant now = Instant.parse("2026-10-20T15:00:00Z");
    public ZoneId getZone() { return ZoneId.of("America/Bogota"); }
    public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    public Instant instant() { return now; }
  }
  @TestConfiguration static class TimeConfig {
    @Bean @Primary TestClock testClock() { return new TestClock(); }
  }
  static final AtomicInteger SEQ = new AtomicInteger();
  final ObjectMapper json = new ObjectMapper();
  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired TestClock clock;
  @Autowired UnitRepository units;
  MockMvc mvc;
  String provider, providerEmail, client, other, tool;
  List<Long> unitIds;
  LocalDate today = LocalDate.of(2026, 10, 20);

  @BeforeEach void setup() throws Exception {
    clock.now = Instant.parse("2026-10-20T15:00:00Z");
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    int n = SEQ.incrementAndGet();
    providerEmail = "operations-provider-" + n + "@test.example";
    provider = TestAccounts.provider(mvc, providerEmail);
    client = TestAccounts.client(mvc, "operations-client-" + n + "@test.example");
    other = TestAccounts.client(mvc, "operations-other-" + n + "@test.example");
    String response = mvc.perform(post("/api/v1/tools").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"categoryId\":1,\"name\":\"Taladro operaciones\",\"description\":\"Equipo\",\"dailyRate\":100}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    tool = TestAccounts.field(response, "id");
    response = mvc.perform(put("/api/v1/tools/" + tool + "/units/count")
        .header("Authorization", "Bearer " + provider).contentType("application/json").content("{\"count\":2}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    unitIds = new ArrayList<>();
    for (var item : json.readTree(response)) unitIds.add(Long.valueOf(item.path("id").asString()));
  }

  String reserve(LocalDate start, LocalDate end) throws Exception {
    String response = mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + start +
            "\",\"end\":\"" + end + "\",\"quantity\":2}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = TestAccounts.field(response, "id");
    TestAccounts.pay(mvc, client, id);
    mvc.perform(post("/api/v1/reservations/" + id + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());
    return id;
  }

  void pay(String id) throws Exception {
    mvc.perform(post("/api/v1/reservations/" + id + "/payments").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(TestAccounts.TEST_CARD)).andExpect(status().isOk());
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delivery(String id, String token) {
    return post("/api/v1/reservations/" + id + "/delivery").header("Authorization", "Bearer " + token)
        .contentType("application/json").content("{\"notes\":\"Con accesorios\"}");
  }

  String returnBody(String secondCondition, String note) {
    return "{\"notes\":\"Revisión completa\",\"units\":[{\"unitId\":" + unitIds.get(0) +
        ",\"condition\":\"BUENO\"},{\"unitId\":" + unitIds.get(1) + ",\"condition\":\"" +
        secondCondition + "\",\"notes\":\"" + note + "\"}]}";
  }

  @Test void paidDeliveryDamagedReturnAndResolutionUpdateAvailability() throws Exception {
    String id = reserve(today, today.plusDays(2));
    pay(id); // reintentar el pago devuelve el mismo pago aprobado
    // Empieza hoy: al aceptarla, la entrega se registró sola a nombre del proveedor.
    mvc.perform(get("/api/v1/reservations/" + id + "/operations").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("status").value("EN_USO"))
        .andExpect(jsonPath("operation.deliveryNotes").value("Entrega automática en la fecha de inicio"));
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("rentalStatus").value("EN_USO"));
    mvc.perform(delivery(id, client)).andExpect(status().isForbidden());
    mvc.perform(delivery(id, provider)).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/reservations/" + id + "/operations").header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"units\":[]}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("DANADO", ""))).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/reservations/" + id + "/operations").header("Authorization", "Bearer " + client))
        .andExpect(status().isOk()).andExpect(jsonPath("status").value("EN_USO"));

    String response = mvc.perform(post("/api/v1/reservations/" + id + "/return")
        .header("Authorization", "Bearer " + provider).contentType("application/json")
        .content(returnBody("DANADO", "Cable roto")))
        .andExpect(status().isOk()).andExpect(jsonPath("status").value("DEVUELTA"))
        .andExpect(jsonPath("incidents[0].description").value("Cable roto"))
        .andReturn().getResponse().getContentAsString();
    String incident = json.readTree(response).path("incidents").get(0).path("id").asString();
    assertEquals(UnitStatus.MANTENIMIENTO, units.findById(unitIds.get(1)).orElseThrow().status());
    mvc.perform(get("/api/v1/tools/" + tool + "/availability").param("start", today.plusDays(3).toString())
        .param("end", today.plusDays(4).toString())).andExpect(status().isOk())
        .andExpect(jsonPath("availableUnits").value(1));
    String code = units.findById(unitIds.get(1)).orElseThrow().code();
    mvc.perform(put("/api/v1/tools/" + tool + "/units/" + unitIds.get(1))
        .header("Authorization", "Bearer " + provider).contentType("application/json")
        .content("{\"code\":\"" + code + "\",\"status\":\"OPERATIVA\"}")).andExpect(status().isConflict());
    mvc.perform(post("/api/v1/incidents/" + incident + "/resolve").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"resolution\":\"Reparado\",\"readyToUse\":true}"))
        .andExpect(status().isForbidden());
    String admin = TestAccounts.admin(mvc);
    mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/incidents/" + incident + "/resolve").header("Authorization", "Bearer " + admin)
        .contentType("application/json").content("{\"resolution\":\"Cable reemplazado y probado\",\"readyToUse\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("resolution").value("Cable reemplazado y probado"));
    assertEquals(UnitStatus.OPERATIVA, units.findById(unitIds.get(1)).orElseThrow().status());
    mvc.perform(get("/api/v1/tools/" + tool + "/availability").param("start", today.toString())
        .param("end", today.plusDays(1).toString())).andExpect(status().isOk())
        .andExpect(jsonPath("availableUnits").value(2));
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("BUENO", ""))).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(status().isOk()).andExpect(jsonPath("rentalStatus").value("DEVUELTA"));
  }

  @Test void overdueEquipmentCannotBeDeliveredAgainOrRemovedByCount() throws Exception {
    String next = reserve(today.plusDays(3), today.plusDays(4));
    String first = reserve(today, today.plusDays(2)); // empieza hoy: se entrega sola al aceptarla
    // Mientras no venza, la unidad entregada no bloquea fechas posteriores a su devolución.
    mvc.perform(get("/api/v1/tools/" + tool + "/availability").param("start", today.plusDays(6).toString())
        .param("end", today.plusDays(7).toString())).andExpect(jsonPath("availableUnits").value(2));
    clock.now = clock.now.plus(Duration.ofDays(3));
    provider = TestAccounts.login(mvc, providerEmail, TestAccounts.PASSWORD); // el token anterior ya venció
    mvc.perform(get("/api/v1/tools/" + tool + "/availability").param("start", today.plusDays(6).toString())
        .param("end", today.plusDays(7).toString())).andExpect(status().isOk())
        .andExpect(jsonPath("availableUnits").value(0));
    mvc.perform(delivery(next, provider)).andExpect(status().isConflict());
    mvc.perform(put("/api/v1/tools/" + tool + "/units/count").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"count\":0}")).andExpect(status().isConflict());
    mvc.perform(post("/api/v1/reservations/" + first + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("BUENO", ""))).andExpect(status().isOk());
    mvc.perform(delivery(next, provider)).andExpect(status().isOk());
  }

  @Test void lostUnitStaysRetiredAndReportsRemainInHistory() throws Exception {
    String id = reserve(today, today.plusDays(2)); // se entrega sola al aceptarla
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + other)
        .contentType("application/json").content("{\"type\":\"OTRO\",\"description\":\"Observación\"}"))
        .andExpect(status().isForbidden()); // solo el cliente de la reserva, no otro cliente
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"type\":\"OTRO\",\"description\":\"Accesorio pendiente\"}"))
        .andExpect(status().isOk());
    String damageResponse = mvc.perform(post("/api/v1/reservations/" + id + "/incidents")
        .header("Authorization", "Bearer " + provider).contentType("application/json")
        .content("{\"unitId\":" + unitIds.get(1) + ",\"type\":\"DANO\",\"description\":\"Golpe durante el uso\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String damageId = TestAccounts.field(damageResponse, "id");
    mvc.perform(post("/api/v1/incidents/" + damageId + "/resolve")
        .header("Authorization", "Bearer " + provider).contentType("application/json")
        .content("{\"resolution\":\"Revisado\",\"readyToUse\":true}")).andExpect(status().isConflict());
    String response = mvc.perform(post("/api/v1/reservations/" + id + "/return")
        .header("Authorization", "Bearer " + provider).contentType("application/json")
        .content(returnBody("PERDIDO", "Unidad no devuelta"))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertEquals(UnitStatus.RETIRADA, units.findById(unitIds.get(1)).orElseThrow().status());
    JsonNode loss = null;
    for (var incident : json.readTree(response).path("incidents"))
      if ("PERDIDA".equals(incident.path("type").asString())) loss = incident;
    assertNotNull(loss);
    String incident = loss.path("id").asString();
    mvc.perform(post("/api/v1/incidents/" + incident + "/resolve").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"resolution\":\"Pérdida registrada; unidad retirada\",\"readyToUse\":false}"))
        .andExpect(status().isOk());
    assertEquals(UnitStatus.RETIRADA, units.findById(unitIds.get(1)).orElseThrow().status());
    mvc.perform(post("/api/v1/incidents/" + incident + "/resolve").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"resolution\":\"Otra vez\",\"readyToUse\":false}")).andExpect(status().isConflict());
  }

  String providerId() throws Exception {
    return TestAccounts.field(mvc.perform(get("/api/v1/tools/" + tool)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), "providerId");
  }

  @Test void catalogAvailabilityFiltersBeforePaginationAndHonorsDateBoundaries() throws Exception {
    // Más reciente y más barata, pero sin unidades: no debe ocupar la primera página.
    mvc.perform(post("/api/v1/tools").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"categoryId\":1,\"name\":\"Sin unidades\",\"description\":\"Equipo\",\"dailyRate\":1}"))
        .andExpect(status().isCreated());
    String filter = "/api/v1/tools?providerId=" + providerId() + "&start=" + today +
        "&end=" + today.plusDays(2) + "&quantity=2&size=1&sort=PRICE_ASC";
    mvc.perform(get(filter)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].id").value(Long.valueOf(tool)));
    mvc.perform(get(filter + "&page=1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0)).andExpect(jsonPath("$.totalElements").value(1));
    reserve(today, today.plusDays(2));
    mvc.perform(get(filter)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    // La devolución del día +2 libera las unidades aunque el equipo esté entregado y sin devolver.
    mvc.perform(get("/api/v1/tools?providerId=" + providerId() + "&start=" + today.plusDays(2) +
        "&end=" + today.plusDays(3) + "&quantity=2")).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test void invalidAvailabilityIsRejectedEvenWithoutCatalogMatches() throws Exception {
    String prefix = "/api/v1/tools?q=nonexistent-no-matches";
    for (String params : List.of("&start=" + today, "&end=" + today.plusDays(1),
        "&start=" + today + "&end=" + today, "&quantity=0", "&quantity=21", "&quantity=2",
        "&start=" + today.minusDays(1) + "&end=" + today.plusDays(1),
        "&start=" + today + "&end=" + today.plusDays(91))) {
      mvc.perform(get(prefix + params)).andExpect(status().isBadRequest());
    }
  }

  @Test void statisticsRespectOwnershipAndCountApprovedPaymentsOnce() throws Exception {
    String id = reserve(today, today.plusDays(2)); // se entrega sola al aceptarla
    pay(id);
    pay(id); // reintentar no duplica el importe ni las reservas pagadas.
    String url = "/api/v1/statistics?from=" + today + "&to=" + today;
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
    mvc.perform(get(url).header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reservations").value(1))
        .andExpect(jsonPath("$.byStatus.ACEPTADA").value(1))
        .andExpect(jsonPath("$.sales").value(400))
        .andExpect(jsonPath("$.inUse").value(1))
        .andExpect(jsonPath("$.popularTools[0].toolId").value(Long.valueOf(tool)));
    String otherProvider = TestAccounts.provider(mvc, "stats-other-" + SEQ.incrementAndGet() + "@test.example");
    mvc.perform(get(url).header("Authorization", "Bearer " + otherProvider))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reservations").value(0))
        .andExpect(jsonPath("$.sales").value(0));
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("DANADO", "Cable dañado")))
        .andExpect(status().isOk());
    mvc.perform(get(url).header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk()).andExpect(jsonPath("$.inUse").value(0))
        .andExpect(jsonPath("$.openIncidents").value(1));
    mvc.perform(get(url).header("Authorization", "Bearer " + TestAccounts.admin(mvc)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/statistics?from=" + today + "&to=" + today.minusDays(1))
        .header("Authorization", "Bearer " + provider)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/statistics?from=" + today + "&to=" + today.plusDays(366))
        .header("Authorization", "Bearer " + provider)).andExpect(status().isBadRequest());
  }

  @Test void providerPerformanceIsAdminOnlyAndCountsAcceptedReservationsAsSales() throws Exception {
    reserve(today, today.plusDays(2));
    String url = "/api/v1/admin/provider-performance?from=" + today + "&to=" + today;
    mvc.perform(get(url).header("Authorization", "Bearer " + provider)).andExpect(status().isForbidden());
    mvc.perform(get(url).header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
    String row = "$.providers[?(@.email == '" + providerEmail + "')]";
    mvc.perform(get(url).header("Authorization", "Bearer " + TestAccounts.admin(mvc)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(row + ".sales").value(400.0))
        .andExpect(jsonPath(row + ".accepted").value(1))
        .andExpect(jsonPath(row + ".requests").value(1))
        .andExpect(jsonPath(row + ".acceptanceRate").value(1.0))
        .andExpect(jsonPath(row + ".activeTools").value(1));
    // Al día siguiente la reserva queda fuera del rango consultado.
    mvc.perform(get("/api/v1/admin/provider-performance?from=" + today.plusDays(1) + "&to=" + today.plusDays(1))
        .header("Authorization", "Bearer " + TestAccounts.admin(mvc)))
        .andExpect(status().isOk()).andExpect(jsonPath(row + ".requests").value(0))
        .andExpect(jsonPath(row + ".alerts[0]").value("No recibió solicitudes en el período"));
  }

  @Test void cancelledReservationsAreCountedInStatistics() throws Exception {
    String response = mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + today.plusDays(5) +
            "\",\"end\":\"" + today.plusDays(6) + "\",\"quantity\":1}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String pending = TestAccounts.field(response, "id");
    TestAccounts.pay(mvc, client, pending);
    String accepted = reserve(today.plusDays(8), today.plusDays(9));
    for (String id : List.of(pending, accepted))
      mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
          .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/statistics?from=" + today + "&to=" + today).header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byStatus.CANCELADA").value(2))
        .andExpect(jsonPath("$.byStatus.ACEPTADA").value(0))
        .andExpect(jsonPath("$.sales").value(0)) // los pagos se reembolsaron
        .andExpect(jsonPath("$.paidAwaitingAcceptance").value(0));
  }

  @Test void clientReportsAProblemWhileUsingTheEquipment() throws Exception {
    String body = "{\"type\":\"OTRO\",\"description\":\"El taladro se apaga solo\"}";
    String future = reserve(today.plusDays(5), today.plusDays(6));
    mvc.perform(post("/api/v1/reservations/" + future + "/incidents").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(body)).andExpect(status().isConflict()); // aún sin entregar
    String id = reserve(today, today.plusDays(2)); // se entrega sola al aceptarla
    mvc.perform(get("/api/v1/reservations/" + id + "/operations").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("canReport").value(true)).andExpect(jsonPath("canManage").value(false));
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + other)
        .contentType("application/json").content(body)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(body)).andExpect(status().isOk())
        .andExpect(jsonPath("description").value("El taladro se apaga solo"));
    mvc.perform(get("/api/v1/statistics?from=" + today + "&to=" + today).header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("$.openIncidents").value(1));
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("BUENO", ""))).andExpect(status().isOk());
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(body)).andExpect(status().isConflict()); // ya lo devolvió
  }

  @Test void lateReturnAndDamageAreChargedAndClientRatesOnce() throws Exception {
    String id = reserve(today, today.plusDays(2));
    String rate = "{\"rating\":4,\"comment\":\"Buen taladro\"}";
    mvc.perform(post("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(rate)).andExpect(status().isConflict()); // aún no devuelve
    // Una unidad en buen estado no admite cobro.
    String goodWithCharge = "{\"units\":[{\"unitId\":" + unitIds.get(0) + ",\"condition\":\"BUENO\",\"charge\":10},"
        + "{\"unitId\":" + unitIds.get(1) + ",\"condition\":\"BUENO\"}]}";
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(goodWithCharge)).andExpect(status().isBadRequest());

    clock.now = Instant.parse("2026-10-24T15:00:00Z"); // dos días después de la fecha de devolución
    // Los tokens anteriores ya vencieron.
    provider = TestAccounts.login(mvc, providerEmail, TestAccounts.PASSWORD);
    client = TestAccounts.login(mvc, "operations-client-" + SEQ.get() + "@test.example", TestAccounts.PASSWORD);
    other = TestAccounts.login(mvc, "operations-other-" + SEQ.get() + "@test.example", TestAccounts.PASSWORD);
    String body = "{\"units\":[{\"unitId\":" + unitIds.get(0) + ",\"condition\":\"BUENO\"},"
        + "{\"unitId\":" + unitIds.get(1) + ",\"condition\":\"DANADO\",\"notes\":\"Mandril roto\",\"charge\":5000}]}";
    String response = mvc.perform(post("/api/v1/reservations/" + id + "/return")
        .header("Authorization", "Bearer " + provider).contentType("application/json").content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var charges = json.readTree(response).path("charges");
    assertEquals(2, charges.path("lateDays").asInt());
    assertEquals(400.0, charges.path("lateFee").asDouble()); // 100 por día × 2 días × 2 unidades
    assertEquals(5000.0, charges.path("damageCharges").asDouble());
    assertEquals(5400.0, charges.path("total").asDouble());

    mvc.perform(post("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + other)
        .contentType("application/json").content(rate)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"rating\":6}")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("canReview").value(true));
    mvc.perform(post("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(rate)).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(rate)).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/reservations/" + id + "/review").header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("canReview").value(false)).andExpect(jsonPath("review.rating").value(4));
    mvc.perform(get("/api/v1/tools/" + tool + "/reviews")) // público
        .andExpect(status().isOk()).andExpect(jsonPath("count").value(1))
        .andExpect(jsonPath("reviews[0].comment").value("Buen taladro"));
  }

  @Test void extensionChecksAvailabilityAndDepositSettlesTheCharges() throws Exception {
    mvc.perform(put("/api/v1/tools/" + tool).header("Authorization", "Bearer " + provider)
        .contentType("application/json")
        .content("{\"categoryId\":1,\"name\":\"Taladro operaciones\",\"description\":\"Equipo\",\"dailyRate\":100,\"deposit\":1000}"))
        .andExpect(status().isOk()).andExpect(jsonPath("deposit").value(1000));
    String id = reserve(today, today.plusDays(2)); // 2 unidades: depósito 2000, total 400
    var reservation = json.readTree(mvc.perform(get("/api/v1/reservations/" + id)
        .header("Authorization", "Bearer " + client)).andReturn().getResponse().getContentAsString());
    assertEquals(2000.0, reservation.path("deposit").asDouble());

    // Otro cliente aparta una unidad del 23 al 25.
    String response = mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + other)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + today.plusDays(3)
            + "\",\"end\":\"" + today.plusDays(5) + "\",\"quantity\":1}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String second = TestAccounts.field(response, "id");
    TestAccounts.pay(mvc, other, second);
    mvc.perform(post("/api/v1/reservations/" + second + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());

    String extendTo = "{\"end\":\"%s\"}";
    mvc.perform(post("/api/v1/reservations/" + id + "/extension").header("Authorization", "Bearer " + other)
        .contentType("application/json").content(extendTo.formatted(today.plusDays(3))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations/" + id + "/extension").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(extendTo.formatted(today.plusDays(1))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/reservations/" + id + "/extension").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(extendTo.formatted(today.plusDays(4))))
        .andExpect(status().isConflict()); // el 23 ya lo tiene el otro cliente
    mvc.perform(post("/api/v1/reservations/" + id + "/extension").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(extendTo.formatted(today.plusDays(3))))
        .andExpect(status().isOk()).andExpect(jsonPath("end").value(today.plusDays(3).toString()))
        .andExpect(jsonPath("days").value(3));

    String body = "{\"units\":[{\"unitId\":" + unitIds.get(0) + ",\"condition\":\"BUENO\"},"
        + "{\"unitId\":" + unitIds.get(1) + ",\"condition\":\"BUENO\"}]}";
    response = mvc.perform(post("/api/v1/reservations/" + id + "/return")
        .header("Authorization", "Bearer " + provider).contentType("application/json").content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var charges = json.readTree(response).path("charges");
    assertEquals(200.0, charges.path("extensionBalance").asDouble()); // 600 nuevo total - 400 pagado
    assertEquals(200.0, charges.path("total").asDouble());
    assertEquals(1800.0, charges.path("depositRefund").asDouble());
    assertEquals(0.0, charges.path("amountDue").asDouble());
    mvc.perform(post("/api/v1/reservations/" + id + "/extension").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(extendTo.formatted(today.plusDays(10))))
        .andExpect(status().isConflict()); // ya se devolvió

    // Ingresos: 600 de la reserva extendida + 200 de la del otro cliente.
    mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", "Bearer " + client)
        .param("from", today.toString()).param("to", today.toString())).andExpect(status().isForbidden());
    response = mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", "Bearer " + provider)
        .param("from", today.toString()).param("to", today.toString()).param("groupBy", "TOOL"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var row = json.readTree(response).path("rows").get(0);
    assertEquals(2, row.path("reservations").asInt());
    assertEquals(800.0, row.path("income").asDouble());
    assertEquals(100.0, row.path("share").asDouble());
    mvc.perform(get("/api/v1/statistics/revenue").header("Authorization", "Bearer " + provider)
        .param("from", today.toString()).param("to", today.toString()).param("groupBy", "MONTH"))
        .andExpect(jsonPath("rows[0].key").value("2026-10"))
        .andExpect(jsonPath("rows[0].label").value("Octubre 2026"));
  }

  @Test void clientsKeepAWishList() throws Exception {
    String path = "/api/v1/me/favorites/" + tool;
    mvc.perform(post(path).header("Authorization", "Bearer " + provider)).andExpect(status().isForbidden());
    mvc.perform(post(path).header("Authorization", "Bearer " + client)).andExpect(status().isNoContent());
    mvc.perform(post(path).header("Authorization", "Bearer " + client)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me/favorites").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(Long.valueOf(tool)));
    mvc.perform(post("/api/v1/me/favorites/999999").header("Authorization", "Bearer " + client))
        .andExpect(status().isNotFound());
    mvc.perform(delete(path).header("Authorization", "Bearer " + client)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me/favorites").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test void reservationsCanBeFilteredByStatus() throws Exception {
    String id = reserve(today.plusDays(5), today.plusDays(6));
    mvc.perform(get("/api/v1/reservations").param("status", "ACEPTADA").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(Long.valueOf(id)));
    mvc.perform(get("/api/v1/reservations").param("status", "RECHAZADA").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("totalElements").value(0));
    mvc.perform(get("/api/v1/reservations").param("status", "OTRO").header("Authorization", "Bearer " + client))
        .andExpect(status().isBadRequest());
  }

  @Test void adminListsClientsAndControlsTheirAccess() throws Exception {
    String admin = TestAccounts.admin(mvc);
    mvc.perform(get("/api/v1/admin/clients").header("Authorization", "Bearer " + client))
        .andExpect(status().isForbidden());
    String response = mvc.perform(get("/api/v1/admin/clients").param("size", "100")
        .header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String otherId = null;
    for (var item : json.readTree(response).path("content"))
      if (item.path("email").asString().startsWith("operations-other-" + SEQ.get())) otherId = item.path("id").asString();
    assertNotNull(otherId);
    mvc.perform(put("/api/v1/admin/clients/" + otherId + "/status").header("Authorization", "Bearer " + admin)
        .contentType("application/json").content("{\"active\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("active").value(false));
    mvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + other))
        .andExpect(status().isUnauthorized());
    mvc.perform(put("/api/v1/admin/clients/" + otherId + "/status").header("Authorization", "Bearer " + admin)
        .contentType("application/json").content("{\"active\":true}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + other))
        .andExpect(status().isOk());
  }
}
