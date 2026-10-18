package com.renttool.infrastructure;

import static org.hamcrest.Matchers.*;
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

/**
 * Lo que el proveedor maneja solo: cuántas unidades tiene y los datos de su empresa. Y lo que el
 * cliente ve de él: el filtro por proveedor del catálogo; a cambio, el proveedor ve cómo contactar
 * al cliente.
 */
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
class ProviderSelfServiceApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String provider, other, client;
  static long providerId, otherId, tool, otherTool;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  long json(MvcResult r, String field) throws Exception {
    return JSON.readTree(r.getResponse().getContentAsString()).get(field).asLong();
  }

  long publish(String token, String name, String description) throws Exception {
    return json(
        mvc.perform(
                post("/api/v1/tools")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"" + name + "\",\"categoryId\":1,\"description\":\""
                            + description + "\",\"dailyRate\":20000}"))
            .andExpect(status().isCreated())
            .andReturn(),
        "id");
  }

  ResultActions count(String token, long toolId, int count) throws Exception {
    return mvc.perform(
        put("/api/v1/tools/" + toolId + "/units/count")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content("{\"count\":" + count + "}"));
  }

  @Test
  @Order(1)
  void providerChoosesHowManyUnitsItHas() throws Exception {
    provider = TestAccounts.provider(mvc, "self-provider@test.example");
    other = TestAccounts.provider(mvc, "self-other@test.example");
    providerId = json(mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + provider)).andReturn(), "id");
    otherId = json(mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + other)).andReturn(), "id");
    tool = publish(provider, "Taladro inalámbrico", "Marca Zentrix, batería de 20 V");
    otherTool = publish(other, "Taladro de banco", "Para taller");

    count(provider, tool, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("length()").value(3))
        .andExpect(jsonPath("[*].code", containsInAnyOrder("TAL-01", "TAL-02", "TAL-03")));
    mvc.perform(get("/api/v1/tools/" + tool)).andExpect(jsonPath("operationalUnits").value(3));
    count(other, tool, 5).andExpect(status().isForbidden());
    count(provider, tool, 201).andExpect(status().isBadRequest());

    // Bajar retira unidades; volver a subir no reutiliza sus códigos.
    count(provider, tool, 2).andExpect(jsonPath("length()").value(2));
    count(provider, tool, 3)
        .andExpect(jsonPath("[*].code", hasItem("TAL-04")))
        .andExpect(jsonPath("[*].code", not(hasItem("TAL-03"))));
  }

  @Test
  @Order(2)
  void clientsFilterTheCatalogByProviderAndBrand() throws Exception {
    count(other, otherTool, 1).andExpect(status().isOk());
    mvc.perform(get("/api/v1/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("[*].id", hasItems((int) providerId, (int) otherId)));

    mvc.perform(get("/api/v1/tools").param("q", "taladro").param("providerId", "" + providerId))
        .andExpect(jsonPath("content[*].id", contains((int) tool)));
    mvc.perform(get("/api/v1/tools").param("q", "taladro"))
        .andExpect(jsonPath("content[*].id", hasItems((int) tool, (int) otherTool)));
    // La marca suele estar solo en la descripción.
    mvc.perform(get("/api/v1/tools").param("q", "zentrix"))
        .andExpect(jsonPath("content[*].id", contains((int) tool)));
  }

  @Test
  @Order(3)
  void clientsNeedAPhoneAndTheProviderSeesIt() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    "{\"name\":\"Sin teléfono\",\"email\":\"nophone@test.example\","
                        + "\"password\":\"correct-test-password\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("fieldErrors.phone").exists());

    client = TestAccounts.client(mvc, "self-client@test.example");
    mvc.perform(
            put("/api/v1/me")
                .header("Authorization", "Bearer " + client)
                .contentType("application/json")
                .content("{\"name\":\"Laura Gómez\",\"phone\":\"+57 300 765 4321\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("phone").value("+57 300 765 4321"));

    long id =
        json(
            mvc.perform(
                    post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + client)
                        .contentType("application/json")
                        .content(
                            "{\"toolId\":" + tool + ",\"start\":\"" + TODAY.plusDays(2)
                                + "\",\"end\":\"" + TODAY.plusDays(4) + "\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn(),
            "id");
    mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", "Bearer " + provider))
        .andExpect(jsonPath("clientEmail").value("self-client@test.example"))
        .andExpect(jsonPath("clientPhone").value("+57 300 765 4321"));
    mvc.perform(post("/api/v1/reservations/" + id + "/accept").header("Authorization", "Bearer " + provider))
        .andExpect(status().isOk());

    // Las dos unidades asignadas a la reserva aceptada no se pueden retirar.
    count(provider, tool, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("detail", containsString("hasta 2")));
    count(provider, tool, 2).andExpect(status().isOk());
  }

  @Test
  @Order(4)
  void providerEditsItsCompanyButNotItsNit() throws Exception {
    String nit =
        JSON.readTree(
                mvc.perform(
                        get("/api/v1/me/provider-profile")
                            .header("Authorization", "Bearer " + provider))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("nit")
            .asString();
    String company =
        "{\"legalName\":\"Herramientas Zentrix SAS\",\"tradeName\":\"Zentrix\","
            + "\"legalRepresentative\":\"Nuevo Dueño\",\"representativeDocument\":\"CC 555\","
            + "\"phone\":\"3109998877\",\"address\":\"Calle 9 # 9-9\",\"city\":\"Medellín\","
            + "\"nit\":\"123456789\"}";
    mvc.perform(
            put("/api/v1/me/provider-profile")
                .header("Authorization", "Bearer " + provider)
                .contentType("application/json")
                .content(company))
        .andExpect(status().isOk())
        .andExpect(jsonPath("name").value("Zentrix"))
        .andExpect(jsonPath("profile.legalRepresentative").value("Nuevo Dueño"))
        .andExpect(jsonPath("profile.city").value("Medellín"))
        .andExpect(jsonPath("profile.nit").value(nit));
    mvc.perform(
            put("/api/v1/me/provider-profile")
                .header("Authorization", "Bearer " + client)
                .contentType("application/json")
                .content(company))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/providers"))
        .andExpect(jsonPath("[?(@.id == " + providerId + ")].name").value(contains("Zentrix")));
  }
}
