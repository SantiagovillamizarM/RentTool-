package com.renttool.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.renttool.application.billing.port.out.PaymentStore;
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
class PaymentApiTest {
  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired PaymentStore store;
  MockMvc mvc;

  @BeforeEach void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test void demoPaymentCreatesOneHistoricalInvoiceAndChecksPermissions() throws Exception {
    String provider = TestAccounts.provider(mvc, "billing-provider@test.example");
    String client = TestAccounts.client(mvc, "billing-client@test.example");
    String other = TestAccounts.client(mvc, "billing-other@test.example");
    String response = mvc.perform(post("/api/v1/tools")
            .header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"categoryId\":1,\"name\":\"Taladro original\",\"description\":\"Equipo\",\"dailyRate\":25000}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String tool = TestAccounts.field(response, "id");
    mvc.perform(post("/api/v1/tools/" + tool + "/units")
            .header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"code\":\"PAY-1\",\"status\":\"OPERATIVA\"}"))
        .andExpect(status().isCreated());
    var start = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(3);
    response = mvc.perform(post("/api/v1/reservations")
            .header("Authorization", "Bearer " + client)
            .contentType("application/json")
            .content("{\"toolId\":" + tool + ",\"start\":\"" + start + "\",\"end\":\"" +
                start.plusDays(2) + "\",\"quantity\":1}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = TestAccounts.field(response, "id");
    String url = "/api/v1/reservations/" + id + "/payments";
    mvc.perform(post(url).header("Authorization", "Bearer " + client)
            .contentType("application/json").content("{\"result\":\"APROBADO\"}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/reservations/" + id + "/accept")
        .header("Authorization", "Bearer " + provider)).andExpect(status().isOk());
    mvc.perform(post(url).header("Authorization", "Bearer " + other)
            .contentType("application/json").content("{\"result\":\"APROBADO\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post(url).header("Authorization", "Bearer " + client)
            .contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(url).header("Authorization", "Bearer " + client)
            .contentType("application/json").content("{\"result\":\"RECHAZADO\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("status").value("DECLINED"));
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice")
        .header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());

    response = mvc.perform(post(url).header("Authorization", "Bearer " + client)
            .contentType("application/json").content("{\"result\":\"APROBADO\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("amount").value(50000))
        .andExpect(jsonPath("status").value("APPROVED"))
        .andReturn().getResponse().getContentAsString();
    String reference = TestAccounts.field(response, "reference");
    mvc.perform(post(url).header("Authorization", "Bearer " + client)
            .contentType("application/json").content("{\"result\":\"APROBADO\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("reference").value(reference));
    assertEquals(2, store.history(Long.valueOf(id)).size());
    mvc.perform(get(url).header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
    mvc.perform(get(url).header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk()).andExpect(jsonPath("invoiceNumber").exists());
    mvc.perform(delete("/api/v1/reservations/" + id)
        .header("Authorization", "Bearer " + client)).andExpect(status().isConflict());
    mvc.perform(put("/api/v1/tools/" + tool).header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"categoryId\":1,\"name\":\"Nombre nuevo\",\"description\":\"Equipo\",\"dailyRate\":99999}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice")
            .header("Authorization", "Bearer " + client))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Taladro original")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("50000")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("sin validez fiscal")));
    mvc.perform(get("/api/v1/reservations/" + id + "/invoice")
        .header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
    assertEquals(reference, store.invoice(Long.valueOf(id)).orElseThrow().paymentReference());
  }
}
