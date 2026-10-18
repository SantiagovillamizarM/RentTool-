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
    TestAccounts.ADMIN_EMAIL_PROPERTY, TestAccounts.ADMIN_PASSWORD_PROPERTY
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
  String provider, client, other, tool;
  List<Long> unitIds;
  LocalDate today = LocalDate.of(2026, 10, 20);

  @BeforeEach void setup() throws Exception {
    clock.now = Instant.parse("2026-10-20T15:00:00Z");
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    int n = SEQ.incrementAndGet();
    provider = TestAccounts.provider(mvc, "operations-provider-" + n + "@test.example");
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
    mvc.perform(post("/api/v1/reservations/" + id + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());
    return id;
  }

  void pay(String id) throws Exception {
    mvc.perform(post("/api/v1/reservations/" + id + "/payments").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"result\":\"APROBADO\"}")).andExpect(status().isOk());
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
    mvc.perform(post("/api/v1/reservations/" + id + "/return").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content(returnBody("BUENO", ""))).andExpect(status().isConflict());
    mvc.perform(delivery(id, provider)).andExpect(status().isConflict());
    pay(id);
    mvc.perform(delivery(id, client)).andExpect(status().isForbidden());
    mvc.perform(delivery(id, provider)).andExpect(status().isOk()).andExpect(jsonPath("status").value("EN_USO"));
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
    String first = reserve(today, today.plusDays(2));
    pay(first);
    String next = reserve(today.plusDays(3), today.plusDays(4));
    pay(next);
    mvc.perform(delivery(first, provider)).andExpect(status().isOk());
    clock.now = clock.now.plus(Duration.ofDays(3));
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
    String id = reserve(today, today.plusDays(2));
    pay(id);
    mvc.perform(delivery(id, provider)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/reservations/" + id + "/incidents").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"type\":\"OTRO\",\"description\":\"Observación\"}"))
        .andExpect(status().isForbidden());
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
}
