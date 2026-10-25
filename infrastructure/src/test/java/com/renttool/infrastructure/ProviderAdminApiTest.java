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
class ProviderAdminApiTest {
  private static final String TOOL =
      "{\"name\":\"Pulidora\",\"categoryId\":1,\"description\":\"Prueba\",\"dailyRate\":20000}";

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String admin, temporary;
  static long providerId;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  ResultActions create(String token, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/admin/providers")
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content(body));
  }

  @Test
  @Order(1)
  void onlyAdministratorReachesProviderAdministration() throws Exception {
    admin = TestAccounts.admin(mvc);
    String client = TestAccounts.client(mvc, "adm-client@test.example");
    String provider = TestAccounts.provider(mvc, "adm-provider@test.example");
    String body = TestAccounts.providerPayload("adm-intruso@test.example", "temporal-password");

    mvc.perform(get("/api/v1/admin/providers")).andExpect(status().isUnauthorized());
    create(client, body).andExpect(status().isForbidden());
    create(provider, body).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/providers").header("Authorization", "Bearer " + provider))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void administratorRegistersProviderWithLegalData() throws Exception {
    String body = TestAccounts.providerPayload("adm-new@test.example", "temporal-password");
    var created =
        create(admin, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("email").value("adm-new@test.example"))
            .andExpect(jsonPath("mustChangePassword").value(true))
            .andExpect(jsonPath("profile.personType").value("JURIDICA"))
            .andExpect(jsonPath("profile.city").value("Bogotá"))
            .andReturn();
    providerId =
        new ObjectMapper()
            .readTree(created.getResponse().getContentAsString())
            .get("id")
            .asLong();

    create(admin, body.replace("adm-new@", "adm-same-nit@")).andExpect(status().isConflict());
    create(admin, TestAccounts.providerPayload("adm-new@test.example", "temporal-password"))
        .andExpect(status().isConflict());
    String valid = TestAccounts.providerPayload("adm-dv@test.example", "temporal-password");
    var digit = java.util.regex.Pattern.compile("\"verificationDigit\":(\\d)").matcher(valid);
    digit.find();
    int wrong = (Integer.parseInt(digit.group(1)) + 1) % 10;
    create(admin, valid.replace(digit.group(), "\"verificationDigit\":" + wrong))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("detail").value("El dígito de verificación no corresponde al NIT"));
    mvc.perform(get("/api/v1/admin/providers").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].id").value(providerId));
  }

  @Test
  @Order(3)
  void temporaryPasswordOnlyAllowsChangingIt() throws Exception {
    temporary = TestAccounts.login(mvc, "adm-new@test.example", "temporal-password");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + temporary))
        .andExpect(status().isOk())
        .andExpect(jsonPath("role").value("PROVEEDOR"))
        .andExpect(jsonPath("mustChangePassword").value(true));
    mvc.perform(
            post("/api/v1/tools")
                .header("Authorization", "Bearer " + temporary)
                .contentType("application/json")
                .content(TOOL))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
    mvc.perform(
            put("/api/v1/me/password")
                .header("Authorization", "Bearer " + temporary)
                .contentType("application/json")
                .content(
                    "{\"currentPassword\":\"incorrecta\",\"newPassword\":\"una-clave-nueva-1\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/me/password")
                .header("Authorization", "Bearer " + temporary)
                .contentType("application/json")
                .content(
                    "{\"currentPassword\":\"temporal-password\",\"newPassword\":\"una-clave-nueva-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("mustChangePassword").value(false));
    mvc.perform(
            post("/api/v1/tools")
                .header("Authorization", "Bearer " + temporary)
                .contentType("application/json")
                .content(TOOL))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(4)
  void deactivationRevokesAccessImmediately() throws Exception {
    mvc.perform(
            put("/api/v1/admin/providers/" + providerId + "/status")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("active").value(false));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + temporary))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    "{\"email\":\"adm-new@test.example\",\"password\":\"una-clave-nueva-1\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            put("/api/v1/admin/providers/" + providerId + "/status")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"active\":true}"))
        .andExpect(status().isOk());
    TestAccounts.login(mvc, "adm-new@test.example", "una-clave-nueva-1");
  }

  @Test
  @Order(5)
  void passwordResetForcesANewChange() throws Exception {
    mvc.perform(
            put("/api/v1/admin/providers/" + providerId + "/password")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"temporaryPassword\":\"otra-temporal-1\"}"))
        .andExpect(status().isNoContent());
    String token = TestAccounts.login(mvc, "adm-new@test.example", "otra-temporal-1");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("mustChangePassword").value(true));
    mvc.perform(get("/api/v1/admin/providers/999999").header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(6)
  void logoIsOptionalAndOnlyRealImages() throws Exception {
    mvc.perform(get("/api/v1/admin/providers/" + providerId).header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("logoUrl").doesNotExist());
    var fake =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "logo.png", "image/png", "not an image".getBytes());
    mvc.perform(
            multipart("/api/v1/admin/providers/" + providerId + "/logo")
                .file(fake)
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        output);
    var logo =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "logo.png", "image/png", output.toByteArray());
    mvc.perform(
            multipart("/api/v1/admin/providers/" + providerId + "/logo")
                .file(logo)
                .header("Authorization", "Bearer " + TestAccounts.client(mvc, "adm-logo@test.example")))
        .andExpect(status().isForbidden());
    var saved =
        mvc.perform(
                multipart("/api/v1/admin/providers/" + providerId + "/logo")
                    .file(logo)
                    .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("logoUrl").value(org.hamcrest.Matchers.startsWith("/uploads/")))
            .andReturn();
    String url =
        TestAccounts.field(saved.getResponse().getContentAsString(), "logoUrl");
    mvc.perform(get(url)).andExpect(status().isOk());
    mvc.perform(delete("/api/v1/admin/providers/" + providerId + "/logo").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("logoUrl").doesNotExist());
  }

  @Test
  @Order(8)
  void administratorSessionExpiresAfterTenIdleMinutes() throws Exception {
    var started = java.time.Instant.now();
    var login =
        new ObjectMapper()
            .readTree(
                mvc.perform(
                        post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(
                                "{\"email\":\""
                                    + TestAccounts.ADMIN_EMAIL
                                    + "\",\"password\":\""
                                    + TestAccounts.ADMIN_PASSWORD
                                    + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    var expires = java.time.Instant.parse(login.get("expiresAt").asString());
    var minutes = java.time.Duration.between(started, expires).toMinutes();
    Assertions.assertTrue(minutes >= 9 && minutes <= 10, "token de admin: " + minutes + " min");

    String token = login.get("token").asString();
    mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("token").exists())
        .andExpect(jsonPath("user.role").value("ADMINISTRADOR"));
    mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());

    // Un token del administrador ya vencido no sirve ni para renovar.
    var encoder = context.getBean(org.springframework.security.oauth2.jwt.JwtEncoder.class);
    var accounts =
        context.getBean(com.renttool.application.identity.port.out.AccountRepository.class);
    var adminAccount = accounts.findByEmail(TestAccounts.ADMIN_EMAIL).orElseThrow();
    var now = java.time.Instant.now();
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer("renttool")
            .subject(adminAccount.id().toString())
            .issuedAt(now.minusSeconds(900))
            .expiresAt(now.minusSeconds(60))
            .claim("role", "ADMINISTRADOR")
            .claim("auth_time", now.minusSeconds(900).getEpochSecond())
            .build();
    String expired =
        encoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    mvc.perform(get("/api/v1/admin/providers").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());

    var clientExpiry =
        new ObjectMapper()
            .readTree(
                mvc.perform(
                        post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(
                                "{\"email\":\"adm-client@test.example\",\"password\":\""
                                    + TestAccounts.PASSWORD
                                    + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("expiresAt")
            .asString();
    Assertions.assertTrue(
        java.time.Duration.between(now, java.time.Instant.parse(clientExpiry)).toMinutes() >= 59,
        "los clientes conservan una hora");
  }

  @Test
  @Order(7)
  void administratorNameIsFixed() throws Exception {
    mvc.perform(
            put("/api/v1/me")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + admin))
        .andExpect(jsonPath("name").value("Administrador"));
  }

  @Test
  @Order(99)
  void administratorAddsAnotherAdministratorWithTheSamePermissions() throws Exception {
    String current = TestAccounts.admin(mvc);
    String client = TestAccounts.client(mvc, "admin-candidate-client@test.example");
    String body = "{\"name\":\"Ana Ruiz\",\"email\":\"ana-admin@test.example\","
        + "\"temporaryPassword\":\"temporal-admin-2026\"}";
    var add = (java.util.function.Function<String, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>) token ->
        post("/api/v1/admin/administrators").header("Authorization", "Bearer " + token)
            .contentType("application/json");
    mvc.perform(add.apply(client).content(body)).andExpect(status().isForbidden());
    mvc.perform(add.apply(current).content(body.replace("temporal-admin-2026", "corta-123")))
        .andExpect(status().isBadRequest());
    mvc.perform(add.apply(current).content(body)).andExpect(status().isCreated())
        .andExpect(jsonPath("mustChangePassword").value(true));
    mvc.perform(add.apply(current).content(body)).andExpect(status().isConflict());

    // Entra con la clave temporal, la cambia y ya opera como cualquier administrador.
    String fresh = TestAccounts.login(mvc, "ana-admin@test.example", "temporal-admin-2026");
    mvc.perform(get("/api/v1/admin/clients").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/me/password").header("Authorization", "Bearer " + fresh)
            .contentType("application/json")
            .content("{\"currentPassword\":\"temporal-admin-2026\",\"newPassword\":\"clave-propia-de-ana\"}"))
        .andExpect(status().is2xxSuccessful());
    fresh = TestAccounts.login(mvc, "ana-admin@test.example", "clave-propia-de-ana");
    mvc.perform(get("/api/v1/admin/clients").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/administrators").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.email == 'ana-admin@test.example')].mustChangePassword")
            .value(false));
  }
}
