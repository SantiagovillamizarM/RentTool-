package com.renttool.infrastructure;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class ProviderApplicationApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String EMAIL = "apply-client@test.example";
  private static final String TOOL =
      "{\"name\":\"Mezcladora\",\"categoryId\":2,\"description\":\"Prueba\",\"dailyRate\":50000}";

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String admin, client, profileJson;
  static long firstId, secondId;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  /** El perfil de una alta por administrador sirve tal cual como solicitud. */
  static String profileOnly() {
    var payload = TestAccounts.providerPayload("ignored@test.example", "temporal-password");
    var json = JSON.readTree(payload).get("profile");
    return json.toString().replace("\"legalName\":\"Proveedor", "\"tradeName\":\"ObraYa\",\"legalName\":\"Proveedor");
  }

  ResultActions apply(String token, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/me/provider-application")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(body));
  }

  long id(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  @Order(1)
  void clientApplies() throws Exception {
    admin = TestAccounts.admin(mvc);
    client = TestAccounts.client(mvc, EMAIL);
    profileJson = profileOnly();
    mvc.perform(get("/api/v1/me/provider-application").header("Authorization", "Bearer " + client))
        .andExpect(status().isNoContent());
    firstId =
        id(
            apply(client, profileJson)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("status").value("PENDIENTE"))
                .andExpect(jsonPath("displayName").value("ObraYa")));
    apply(client, profileJson).andExpect(status().isConflict());
    String provider = TestAccounts.provider(mvc, "apply-provider@test.example");
    apply(provider, profileOnly()).andExpect(status().isForbidden());
    apply(admin, profileOnly()).andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void administratorRejectsWithAReasonShownOnce() throws Exception {
    mvc.perform(get("/api/v1/admin/provider-applications").header("Authorization", "Bearer " + client))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/provider-applications").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[?(@.application.id == " + firstId + ")].applicant.email").value(EMAIL));
    mvc.perform(
            post("/api/v1/admin/provider-applications/" + firstId + "/reject")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/admin/provider-applications/" + firstId + "/reject")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"reason\":\"El RUT no coincide con la razón social\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me/provider-application").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("status").value("RECHAZADA"))
        .andExpect(jsonPath("rejectionReason").value("El RUT no coincide con la razón social"))
        .andExpect(jsonPath("needsNotice").value(true));
    mvc.perform(
            post("/api/v1/me/provider-application/acknowledge")
                .header("Authorization", "Bearer " + client))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me/provider-application").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("needsNotice").value(false));
  }

  @Test
  @Order(3)
  void acceptanceTurnsTheClientIntoAProvider() throws Exception {
    secondId = id(apply(client, profileJson).andExpect(status().isCreated()));
    mvc.perform(
            post("/api/v1/admin/provider-applications/" + secondId + "/accept")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("application.status").value("ACEPTADA"));
    mvc.perform(
            post("/api/v1/admin/provider-applications/" + secondId + "/accept")
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isConflict());

    // El token anterior todavía dice CLIENTE: no sirve para operar hasta renovarlo.
    mvc.perform(
            post("/api/v1/tools")
                .header("Authorization", "Bearer " + client)
                .contentType("application/json")
                .content(TOOL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("code").value("ROLE_CHANGED"));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + client))
        .andExpect(jsonPath("role").value("PROVEEDOR"))
        .andExpect(jsonPath("name").value("ObraYa"));
    String renewed =
        TestAccounts.field(
            mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.role").value("PROVEEDOR"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "token");
    mvc.perform(
            post("/api/v1/tools")
                .header("Authorization", "Bearer " + renewed)
                .contentType("application/json")
                .content(TOOL))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/v1/me/provider-application").header("Authorization", "Bearer " + renewed))
        .andExpect(jsonPath("status").value("ACEPTADA"))
        .andExpect(jsonPath("displayName").value("ObraYa"))
        .andExpect(jsonPath("needsNotice").value(true));
    mvc.perform(get("/api/v1/me/provider-profile").header("Authorization", "Bearer " + renewed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("tradeName").value("ObraYa"));
    mvc.perform(
            put("/api/v1/me")
                .header("Authorization", "Bearer " + renewed)
                .contentType("application/json")
                .content("{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/providers?size=100").header("Authorization", "Bearer " + admin))
        .andExpect(content().string(org.hamcrest.Matchers.containsString(EMAIL)));
  }
}
