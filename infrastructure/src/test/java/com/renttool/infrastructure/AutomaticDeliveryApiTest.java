package com.renttool.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.application.operations.port.in.ManageRentalsUseCase;
import com.renttool.application.reservation.port.in.ExpireReservationsUseCase;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.inventory.UnitStatus;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/** Entrega automática, reservas que no se pudieron entregar, plazos y la definición de venta. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:automatic-delivery;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
    TestAccounts.ADMIN_EMAIL_PROPERTY, TestAccounts.ADMIN_PASSWORD_PROPERTY,
    // El reloj de la prueba se mueve a mano: las revisiones automáticas se llaman explícitamente.
    "renttool.reservations.delivery-check=PT1000H", "renttool.reservations.expiry-check=PT1000H"
})
@Import(RentalOperationsApiTest.TimeConfig.class)
class AutomaticDeliveryApiTest {
  static final AtomicInteger SEQ = new AtomicInteger();
  static final Instant TEN_AM = Instant.parse("2026-10-20T15:00:00Z"); // 10:00 en Bogotá
  final ObjectMapper json = new ObjectMapper();
  final LocalDate today = LocalDate.of(2026, 10, 20);
  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired RentalOperationsApiTest.TestClock clock;
  @Autowired UnitRepository units;
  @Autowired ReservationRepository reservations;
  @Autowired PaymentStore payments;
  @Autowired ManageRentalsUseCase rentals;
  @Autowired ExpireReservationsUseCase expiry;
  MockMvc mvc;
  String providerEmail, clientEmail, provider, client, tool;
  List<Long> unitIds;

  @BeforeEach void setup() throws Exception {
    clock.now = TEN_AM;
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    int n = SEQ.incrementAndGet();
    providerEmail = "auto-provider-" + n + "@test.example";
    clientEmail = "auto-client-" + n + "@test.example";
    provider = TestAccounts.provider(mvc, providerEmail);
    client = TestAccounts.client(mvc, clientEmail);
    tool = TestAccounts.field(mvc.perform(post("/api/v1/tools").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"categoryId\":1,\"name\":\"Pulidora\",\"description\":\"Equipo\",\"dailyRate\":100}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
    String response = mvc.perform(put("/api/v1/tools/" + tool + "/units/count")
        .header("Authorization", "Bearer " + provider).contentType("application/json").content("{\"count\":2}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    unitIds = new ArrayList<>();
    for (var item : json.readTree(response)) unitIds.add(Long.valueOf(item.path("id").asString()));
  }

  String request(LocalDate start, LocalDate end, int quantity) throws Exception {
    return TestAccounts.field(mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + start +
            "\",\"end\":\"" + end + "\",\"quantity\":" + quantity + "}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
  }

  String acceptedAndPaid(LocalDate start, LocalDate end, int quantity) throws Exception {
    String id = request(start, end, quantity);
    TestAccounts.pay(mvc, client, id);
    mvc.perform(post("/api/v1/reservations/" + id + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());
    return id;
  }

  /** Avanza el reloj; las sesiones anteriores caducan, así que se vuelve a iniciar sesión. */
  void advance(Duration duration) throws Exception {
    clock.now = clock.now.plus(duration);
    provider = TestAccounts.login(mvc, providerEmail, TestAccounts.PASSWORD);
    client = TestAccounts.login(mvc, clientEmail, TestAccounts.PASSWORD);
  }

  Set<Long> unitsOf(String id) {
    return reservations.findById(Long.valueOf(id)).orElseThrow().unitIds();
  }

  Payment.Status paymentOf(String id) {
    return payments.history(Long.valueOf(id)).get(0).status();
  }

  @Test void paymentHoldsUnitsSoNobodyElseCanPayForThem() throws Exception {
    String first = request(today.plusDays(2), today.plusDays(4), 2);
    TestAccounts.pay(mvc, client, first);
    assertEquals(2, unitsOf(first).size(), "al pagar, la solicitud aparta sus unidades");
    mvc.perform(get("/api/v1/tools/" + tool + "/availability").param("start", today.plusDays(3).toString())
        .param("end", today.plusDays(4).toString())).andExpect(jsonPath("availableUnits").value(0));
    mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + today.plusDays(3) +
            "\",\"end\":\"" + today.plusDays(4) + "\",\"quantity\":1}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/reservations/" + first + "/reject").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{}")).andExpect(status().isOk());
    assertTrue(unitsOf(first).isEmpty(), "al rechazar, las unidades quedan libres");
    assertEquals(Payment.Status.VOIDED, paymentOf(first));
  }

  @Test void deliveryIsAutomaticAndReplacesAUnitThatWentToMaintenance() throws Exception {
    String id = acceptedAndPaid(today.plusDays(1), today.plusDays(3), 1);
    long assigned = unitsOf(id).iterator().next();
    var unit = units.findById(assigned).orElseThrow();
    units.save(unit.change(unit.code(), UnitStatus.MANTENIMIENTO));
    advance(Duration.ofDays(1));
    assertTrue(rentals.deliverIfStarted(Long.valueOf(id)));
    var now = unitsOf(id);
    assertEquals(1, now.size());
    assertNotEquals(assigned, now.iterator().next(), "se entregó otra unidad libre de la herramienta");
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("rentalStatus").value("EN_USO"));
    assertFalse(rentals.deliverIfStarted(Long.valueOf(id)), "no se entrega dos veces");
  }

  @Test void blockedDeliveryCanBeCancelledWithRefundOrIsCancelledWhenThePeriodEnds() throws Exception {
    String blocked = acceptedAndPaid(today.plusDays(1), today.plusDays(3), 2);
    String later = acceptedAndPaid(today.plusDays(4), today.plusDays(5), 2);
    for (Long unitId : unitIds) {
      var unit = units.findById(unitId).orElseThrow();
      units.save(unit.change(unit.code(), UnitStatus.MANTENIMIENTO));
    }
    advance(Duration.ofDays(1));
    assertFalse(rentals.deliverIfStarted(Long.valueOf(blocked)), "ninguna unidad está lista");
    mvc.perform(get("/api/v1/reservations/" + blocked).header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("rentalStatus").value("ENTREGA_BLOQUEADA"));
    // El cliente ya no puede cancelar porque empezó; el proveedor sí, con reembolso.
    mvc.perform(delete("/api/v1/reservations/" + blocked).header("Authorization", "Bearer " + client))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/reservations/" + blocked).header("Authorization", "Bearer " + provider))
        .andExpect(status().isNoContent());
    assertEquals(Payment.Status.VOIDED, paymentOf(blocked));

    advance(Duration.ofDays(4)); // pasó la devolución de «later» sin entrega
    assertTrue(expiry.cancelUndeliveredEnded() >= 1);
    assertEquals(ReservationStatus.CANCELADA, reservations.findById(Long.valueOf(later)).orElseThrow().status());
    assertEquals(Payment.Status.VOIDED, paymentOf(later));
  }

  @Test void deadlinesAreEnforcedImmediatelyWhenPayingOrAccepting() throws Exception {
    String unpaid = request(today.plusDays(3), today.plusDays(4), 1);
    String paid = request(today.plusDays(5), today.plusDays(6), 1);
    TestAccounts.pay(mvc, client, paid);
    advance(Duration.ofHours(25));
    mvc.perform(post("/api/v1/reservations/" + unpaid + "/payments").header("Authorization", "Bearer " + client)
        .contentType("application/json").content(TestAccounts.TEST_CARD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("detail").value("El plazo para pagar esta solicitud ya venció"));
    assertEquals(ReservationStatus.VENCIDA, reservations.findById(Long.valueOf(unpaid)).orElseThrow().status());
    mvc.perform(post("/api/v1/reservations/" + paid + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isConflict());
    assertEquals(ReservationStatus.VENCIDA, reservations.findById(Long.valueOf(paid)).orElseThrow().status());
    assertEquals(Payment.Status.VOIDED, paymentOf(paid), "al vencer se reembolsa");
  }

  @Test void sameDayRequestsNeedTwoHoursBeforeMidnight() throws Exception {
    clock.now = Instant.parse("2026-10-21T03:30:00Z"); // 22:30 del 20 en Bogotá
    client = TestAccounts.login(mvc, clientEmail, TestAccounts.PASSWORD);
    mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + client)
        .contentType("application/json").content("{\"toolId\":" + tool + ",\"start\":\"" + today +
            "\",\"end\":\"" + today.plusDays(1) + "\",\"quantity\":1}"))
        .andExpect(status().isBadRequest());
    request(today.plusDays(1), today.plusDays(2), 1);
  }

  @Test void providerAndAdminShowTheSameSales() throws Exception {
    acceptedAndPaid(today.plusDays(1), today.plusDays(3), 1); // venta de 200
    String awaiting = request(today.plusDays(1), today.plusDays(2), 1);
    TestAccounts.pay(mvc, client, awaiting); // pagada, sin aceptar: 100
    mvc.perform(get("/api/v1/statistics?from=" + today + "&to=" + today).header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("$.sales").value(200)).andExpect(jsonPath("$.paidAwaitingAcceptance").value(100));
    String row = "$.providers[?(@.email == '" + providerEmail + "')]";
    mvc.perform(get("/api/v1/admin/provider-performance?from=" + today + "&to=" + today)
        .header("Authorization", "Bearer " + TestAccounts.admin(mvc)))
        .andExpect(jsonPath(row + ".sales").value(200.0));
  }
}
