package com.renttool.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.renttool.application.identity.port.in.ProvisionAccountUseCase;
import com.renttool.domain.identity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Perfil demo: Midnight publica su catálogo con imágenes y unidades, y la cuenta demo anterior sale
 * del catálogo. Usa su propia base en memoria para no mezclar datos con las demás pruebas.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:renttool-demo;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "renttool.jwt-secret=test-only-secret-at-least-32-characters-long",
      "renttool.upload-dir=${java.io.tmpdir}/renttool-demo-test-uploads",
      "DEMO_PASSWORD=demo-password-123"
    })
@ActiveProfiles("demo")
class DemoCatalogTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired ProvisionAccountUseCase accounts;
  @Autowired CommandLineRunner populate;

  MockMvc mvc() {
    return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  int count(MockMvc mvc, String query) throws Exception {
    return JSON.readTree(
            mvc.perform(get("/api/v1/tools?size=100" + query))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("totalElements")
        .asInt();
  }

  @Test
  void midnightPublishesItsCatalogInThreeSections() throws Exception {
    var mvc = mvc();
    assertEquals(18, count(mvc, ""));
    assertEquals(12, count(mvc, "&categoryId=1"), "Eléctricas");
    assertEquals(1, count(mvc, "&categoryId=2"), "Iluminación");
    assertEquals(5, count(mvc, "&categoryId=3"), "Manuales");

    var tools = JSON.readTree(mvc.perform(get("/api/v1/tools?size=100")).andReturn().getResponse().getContentAsString()).get("content");
    for (var t : tools) {
      assertEquals(1, t.get("images").size(), "cada herramienta tiene su imagen: " + t.get("name"));
      assertTrue(t.get("operationalUnits").asInt() > 0, "y unidades para reservarse");
    }
    var image = tools.get(0).get("images").get(0).get("location").asString();
    mvc.perform(get(image)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    assertEquals(0, count(mvc, "&q=Amoladora"), "la pulidora reemplaza a la amoladora");

    var midnight = accounts.findAccount("midnight@renttool.demo").orElseThrow();
    assertEquals(Role.PROVEEDOR, midnight.role());
    assertEquals("Midnight", midnight.name());
    String token =
        TestAccounts.login(mvc, "midnight@renttool.demo", "demo-password-123");
    mvc.perform(get("/api/v1/me/provider-profile").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("legalRepresentative").value("Santiago Villamizar Mantilla"))
        .andExpect(jsonPath("nit").value("901478236"));
  }

  @Test
  void runningItAgainDoesNotDuplicateTheCatalog() throws Exception {
    populate.run();
    assertEquals(18, count(mvc(), ""));
  }

  /** Bases que ya tenían el demo anterior: sus herramientas salen y la cuenta queda de baja. */
  @Test
  void legacyDemoAccountLeavesTheCatalog(
      @Autowired com.renttool.application.inventory.port.in.ManageToolsUseCase tools)
      throws Exception {
    var legacy =
        accounts.ensureAccount(
            "Equipo RentTool Demo", "proveedor@renttool.demo", "demo-password-123", Role.PROVEEDOR);
    tools.publish(
        new com.renttool.domain.inventory.Actor(legacy.id(), true, false),
        new com.renttool.application.inventory.port.in.ManageToolsUseCase.Command(
            1L, "Amoladora angular 4½″", "Demo anterior", java.math.BigDecimal.TEN));
    assertEquals(1, count(mvc(), "&q=Amoladora"));

    populate.run();
    assertEquals(0, count(mvc(), "&q=Amoladora"));
    assertTrue(accounts.findAccount("proveedor@renttool.demo").orElseThrow().isRemoved());
    assertEquals(18, count(mvc(), ""), "Midnight no se ve afectado");
  }
}
