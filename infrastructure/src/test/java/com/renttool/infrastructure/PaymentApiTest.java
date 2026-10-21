package com.renttool.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.domain.billing.Payment;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:payments;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
    TestAccounts.ADMIN_EMAIL_PROPERTY, TestAccounts.ADMIN_PASSWORD_PROPERTY
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentApiTest {
  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired PaymentStore store;
  MockMvc mvc;
  static String provider, client, other, tool;
  static final java.time.LocalDate START =
      java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(3);

  @BeforeEach void setup() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    if (provider != null) return;
    provider = TestAccounts.provider(mvc, "billing-provider@test.example");
    client = TestAccounts.client(mvc, "billing-client@test.example");
    other = TestAccounts.client(mvc, "billing-other@test.example");
    String response = mvc.perform(post("/api/v1/tools")
            .header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"categoryId\":1,\"name\":\"Taladro original\",\"description\":\"Equipo\",\"dailyRate\":25000}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    tool = TestAccounts.field(response, "id");
    mvc.perform(post("/api/v1/tools/" + tool + "/units")
            .header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"code\":\"PAY-1\",\"status\":\"OPERATIVA\"}"))
        .andExpect(status().isCreated());
  }

  String reserve(int offset) throws Exception {
    var start = START.plusDays(offset);
    String response = mvc.perform(post("/api/v1/reservations")
            .header("Authorization", "Bearer " + client)
            .contentType("application/json")
            .content("{\"toolId\":" + tool + ",\"start\":\"" + start + "\",\"end\":\"" +
                start.plusDays(2) + "\",\"quantity\":1}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return TestAccounts.field(response, "id");
  }

  static String card(String number) {
    return "{\"method\":\"TARJETA\",\"number\":\"" + number + "\",\"holder\":\"ANA RUIZ\","
        + "\"expMonth\":12,\"expYear\":2099,\"cvv\":\"123\"}";
  }

  ResultActions pay(String id, String token, String body) throws Exception {
    return mvc.perform(post("/api/v1/reservations/" + id + "/payments")
        .header("Authorization", "Bearer " + token).contentType("application/json").content(body));
  }

  @Test @Order(1) void clientPaysWhenRequestingAndOnlyThenTheProviderCanAccept() throws Exception {
    String id = reserve(0);
    mvc.perform(post("/api/v1/reservations/" + id + "/accept")
        .header("Authorization", "Bearer " + provider)).andExpect(status().isConflict());
    pay(id, other, card("4242424242424242")).andExpect(status().isForbidden());
    pay(id, client, "{}").andExpect(status().isBadRequest());
    pay(id, client, card("4242424242424241")).andExpect(status().isBadRequest()); // Luhn
    pay(id, client, card("4111111111111111")).andExpect(status().isBadRequest()) // tarjeta no de prueba
        .andExpect(jsonPath("detail").value(containsString("Tarjeta no aceptada")));
    pay(id, client, "{\"method\":\"TARJETA\",\"number\":\"4242424242424242\",\"holder\":\"ANA\","
        + "\"expMonth\":1,\"expYear\":2020,\"cvv\":\"123\"}").andExpect(status().isBadRequest());

    pay(id, client, card("4000 0000 0000 0002")).andExpect(status().isOk())
        .andExpect(jsonPath("status").value("DECLINED"))
        .andExpect(jsonPath("message").value("Fondos insuficientes"));
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice")
        .header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());

    String response = pay(id, client, "{\"method\":\"NEQUI\",\"phone\":\"300 111 1111\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("amount").value(50000))
        .andExpect(jsonPath("status").value("APPROVED"))
        .andExpect(jsonPath("detail").value("Nequi •••• 1111"))
        .andReturn().getResponse().getContentAsString();
    String reference = TestAccounts.field(response, "reference");
    pay(id, client, card("4242424242424242")).andExpect(status().isOk())
        .andExpect(jsonPath("reference").value(reference));
    assertEquals(2, store.history(Long.valueOf(id)).size());
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("paid").value(true));
    mvc.perform(get("/api/v1/reservations/" + id + "/payments").header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/reservations/" + id + "/accept")
        .header("Authorization", "Bearer " + provider)).andExpect(status().isOk());
    // El catálogo puede cambiar; la factura conserva los datos del momento del pago.
    mvc.perform(put("/api/v1/tools/" + tool).header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"categoryId\":1,\"name\":\"Nombre nuevo\",\"description\":\"Equipo\",\"dailyRate\":99999}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice").header("Authorization", "Bearer " + client))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Taladro original")))
        .andExpect(content().string(containsString("50000")))
        .andExpect(content().string(containsString("Nequi")))
        .andExpect(content().string(containsString("Total pagado")));
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice")
        .header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
    assertEquals(reference, store.invoice(Long.valueOf(id)).orElseThrow().paymentReference());
  }

  @Test @Order(2) void rejectingAPaidRequestRefundsAndVoidsTheInvoice() throws Exception {
    String id = reserve(10);
    pay(id, client, card("4242 4242 4242 4242")).andExpect(jsonPath("detail").value("Visa •••• 4242"));
    mvc.perform(post("/api/v1/reservations/" + id + "/reject").header("Authorization", "Bearer " + provider)
        .contentType("application/json").content("{\"reason\":\"Equipo en revisión\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/reservations/" + id + "/payments").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("payable").value(false))
        .andExpect(jsonPath("payments[0].status").value("VOIDED"))
        .andExpect(jsonPath("payments[0].message").value("El proveedor rechazó la solicitud"));
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("paid").value(false));
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice").header("Authorization", "Bearer " + client))
        .andExpect(content().string(containsString("FACTURA ANULADA")));
    // Solo se guarda la franquicia y los últimos 4 dígitos, nunca el número completo.
    for (Payment p : store.history(Long.valueOf(id)))
      assertFalse(p.toString().contains("4242424242424242"));
  }

  @Test @Order(3) void cancellingAPaidReservationRefundsIt() throws Exception {
    String id = reserve(20);
    TestAccounts.pay(mvc, client, id);
    mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", "Bearer " + client))
        .andExpect(status().isNoContent());
    assertEquals(Payment.Status.VOIDED, store.history(Long.valueOf(id)).get(0).status());
    pay(id, client, card("4242424242424242")).andExpect(status().isConflict());
  }
}
