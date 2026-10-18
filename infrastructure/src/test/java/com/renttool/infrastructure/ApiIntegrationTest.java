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
class ApiIntegrationTest {
  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String provider, client, other;
  static long tool;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @Order(1)
  void registrationAndSecurity() throws Exception {
    provider = TestAccounts.provider(mvc, "provider@test.example");
    client = TestAccounts.client(mvc, "client@test.example");
    other = TestAccounts.provider(mvc, "other@test.example");
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + client))
        .andExpect(status().isOk())
        .andExpect(jsonPath("role").value("CLIENTE"))
        .andExpect(jsonPath("passwordHash").doesNotExist());
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    "{\"name\":\"Admin\",\"email\":\"admin@test.example\",\"password\":\"correct-test-password\",\"role\":\"ADMINISTRADOR\",\"phone\":\"3001234567\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    "{\"name\":\"Intruso\",\"email\":\"self@test.example\",\"password\":\"correct-test-password\",\"role\":\"PROVEEDOR\",\"phone\":\"3001234567\"}"))
        .andExpect(status().isForbidden());
  }

  String payload =
      "{\"name\":\"Taladro profesional\",\"categoryId\":1,\"description\":\"Equipo de"
          + " prueba\",\"dailyRate\":25000}";

  @Test
  @Order(2)
  void catalogOwnershipAndLifecycle() throws Exception {
    mvc.perform(
            post("/api/v1/tools")
                .header("Authorization", "Bearer " + client)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isForbidden());
    var r =
        mvc.perform(
                post("/api/v1/tools")
                    .header("Authorization", "Bearer " + provider)
                    .contentType("application/json")
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn();
    tool = new ObjectMapper().readTree(r.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(
            put("/api/v1/tools/" + tool)
                .header("Authorization", "Bearer " + other)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/tools/" + tool + "/units")
                .header("Authorization", "Bearer " + provider)
                .contentType("application/json")
                .content("{\"code\":\"TAL-001\",\"status\":\"OPERATIVA\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/tools?q=Taladro"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1))
        .andExpect(jsonPath("content[0].operationalUnits").value(1));
    mvc.perform(delete("/api/v1/tools/" + tool).header("Authorization", "Bearer " + provider))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/tools/" + tool)).andExpect(status().isNotFound());
  }

  @Test
  @Order(3)
  void badCredentialsAndValidation() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"client@test.example\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/tools?size=999")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Eléctricas"))
        .andExpect(jsonPath("$[1].name").value("Iluminación"))
        .andExpect(jsonPath("$[2].name").value("Manuales"));
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(forwardedUrl("/admin.html"));
    mvc.perform(get("/proveedor"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/proveedor.html"));
  }

  @Test
  @Order(4)
  void imageValidationAndUnitOwnership() throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/tools")
                    .header("Authorization", "Bearer " + provider)
                    .contentType("application/json")
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn();
    long id =
        new ObjectMapper().readTree(created.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(get("/api/v1/tools/" + id + "/units")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/tools/" + id + "/images")
                .header("Authorization", "Bearer " + provider)
                .contentType("application/json")
                .content("{\"location\":\"https://example.com/test.png\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/tools/" + id + "/images")
                .header("Authorization", "Bearer " + provider)
                .contentType("application/json")
                .content("{\"location\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());
    var fake =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "fake.png", "image/png", "not an image".getBytes());
    mvc.perform(
            multipart("/api/v1/tools/" + id + "/images/upload")
                .file(fake)
                .header("Authorization", "Bearer " + provider))
        .andExpect(status().isBadRequest());
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        output);
    var image =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "image.png", "image/png", output.toByteArray());
    mvc.perform(
            multipart("/api/v1/tools/" + id + "/images/upload")
                .file(image)
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
    var response =
        mvc.perform(
                multipart("/api/v1/tools/" + id + "/images/upload")
                    .file(image)
                    .header("Authorization", "Bearer " + provider))
            .andExpect(status().isCreated())
            .andReturn();
    String location =
        new ObjectMapper()
            .readTree(response.getResponse().getContentAsString())
            .get("location")
            .asString();
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
  }

  @Test
  @Order(5)
  void swaggerDocumentsTheApiWithoutLogin() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("info.title").value("RentTool API"))
        .andExpect(jsonPath("components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("paths['/api/v1/tools'].post.security[0].bearerAuth").exists())
        .andExpect(jsonPath("paths['/api/v1/auth/login'].post.security").doesNotExist());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  @Order(6)
  void disabledAccountCannotUseExistingToken() throws Exception {
    var repository =
        context.getBean(com.renttool.application.identity.port.out.AccountRepository.class);
    var user = repository.findByEmail("client@test.example").orElseThrow();
    repository.save(user.deactivate());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + client))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(7)
  void catalogSortsBeforePaginationWithFilters() throws Exception {
    var accounts = context.getBean(com.renttool.application.identity.port.out.AccountRepository.class);
    var tools = context.getBean(com.renttool.application.inventory.port.out.ToolRepository.class);
    Long owner = accounts.findByEmail("provider@test.example").orElseThrow().id();
    var ids = new java.util.ArrayList<Long>();
    for (int price : new int[] {30, 10, 20, 10}) {
      ids.add(tools.save(new com.renttool.domain.inventory.Tool(
          null, owner, 1L, "Sort QA " + price, "Sorting test",
          java.math.BigDecimal.valueOf(price), true)).id());
    }
    tools.save(new com.renttool.domain.inventory.Tool(
        null, owner, 2L, "Sort QA other", "Excluded category",
        java.math.BigDecimal.ONE, true));
    tools.save(new com.renttool.domain.inventory.Tool(
        null, owner, 1L, "Sort QA retired", "Excluded retired",
        java.math.BigDecimal.ONE, false));
    mvc.perform(get("/api/v1/tools").param("q", "Sort QA").param("categoryId", "1")
            .param("sort", "PRICE_ASC").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(4))
        .andExpect(jsonPath("content[0].id").value(ids.get(3).intValue()))
        .andExpect(jsonPath("content[1].id").value(ids.get(1).intValue()));
    mvc.perform(get("/api/v1/tools").param("q", "Sort QA").param("categoryId", "1")
            .param("sort", "PRICE_ASC").param("size", "2").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].id").value(ids.get(2).intValue()))
        .andExpect(jsonPath("content[1].id").value(ids.get(0).intValue()));
    mvc.perform(get("/api/v1/tools").param("q", "Sort QA").param("categoryId", "1")
            .param("sort", "PRICE_DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].id").value(ids.get(0).intValue()));
    mvc.perform(get("/api/v1/tools").param("q", "Sort QA").param("categoryId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content[0].id").value(ids.get(3).intValue()));
    mvc.perform(get("/api/v1/tools").param("sort", "INVALID"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(8)
  void validationReturnsSpanishMessagesByFieldWithoutRejectedValues() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"secret\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("fieldErrors.name").value("Nombre: es obligatorio."))
        .andExpect(jsonPath("fieldErrors.email").value(
            "Correo electrónico: escribe una dirección válida, por ejemplo nombre@ejemplo.com."))
        .andExpect(jsonPath("fieldErrors.password").value(
            "Contraseña: debe tener entre 10 y 64 caracteres."))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    mvc.perform(post("/api/v1/tools").header("Authorization", "Bearer " + provider)
            .contentType("application/json")
            .content("{\"categoryId\":1,\"name\":\"Taladro\",\"description\":\"Equipo\",\"dailyRate\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("fieldErrors.dailyRate").value("Tarifa diaria: debe ser como mínimo 0.01."));
    mvc.perform(get("/api/v1/tools").param("sort", "INVALID"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("detail").value(org.hamcrest.Matchers.containsString("PRICE_ASC")));
  }
}
