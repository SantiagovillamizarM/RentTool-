package com.renttool.infrastructure;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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
class ToolEditingApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired org.springframework.web.context.WebApplicationContext context;
  MockMvc mvc;
  static String admin, provider, other;
  static long tool, extraImage;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  static MockMultipartFile png(int size) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", out);
    return new MockMultipartFile("file", "foto.png", "image/png", out.toByteArray());
  }

  String location(MvcResult r) throws Exception {
    return JSON.readTree(r.getResponse().getContentAsString()).get("location").asString();
  }

  @Test
  @Order(1)
  void administratorEditsNamePriceAndCategory() throws Exception {
    admin = TestAccounts.admin(mvc);
    provider = TestAccounts.provider(mvc, "edit-provider@test.example");
    other = TestAccounts.provider(mvc, "edit-other@test.example");
    tool =
        JSON.readTree(
                mvc.perform(
                        post("/api/v1/tools")
                            .header("Authorization", "Bearer " + provider)
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"Pulidora vieja\",\"categoryId\":1,"
                                    + "\"description\":\"Prueba\",\"dailyRate\":20000}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    // Sin imagen: la interfaz muestra la predeterminada.
    mvc.perform(get("/api/v1/tools/" + tool)).andExpect(jsonPath("images.length()").value(0));

    String edited =
        "{\"name\":\"Pulidora industrial\",\"categoryId\":2,\"description\":\"Renovada\","
            + "\"dailyRate\":27500}";
    mvc.perform(
            put("/api/v1/tools/" + tool)
                .header("Authorization", "Bearer " + other)
                .contentType("application/json")
                .content(edited))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/tools/" + tool)
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(edited))
        .andExpect(status().isOk())
        .andExpect(jsonPath("providerId").exists());
    mvc.perform(get("/api/v1/tools/" + tool))
        .andExpect(jsonPath("name").value("Pulidora industrial"))
        .andExpect(jsonPath("dailyRate").value(27500.0))
        .andExpect(jsonPath("categoryId").value(2));
  }

  @Test
  @Order(2)
  void changingTheImageReplacesOnlyTheMainOne() throws Exception {
    String first =
        location(
            mvc.perform(
                    multipart("/api/v1/tools/" + tool + "/images/upload")
                        .file(png(3))
                        .header("Authorization", "Bearer " + provider))
                .andExpect(status().isCreated())
                .andReturn());
    var extra =
        mvc.perform(
                multipart("/api/v1/tools/" + tool + "/images/upload")
                    .file(png(4))
                    .header("Authorization", "Bearer " + provider))
            .andExpect(status().isCreated())
            .andReturn();
    extraImage = JSON.readTree(extra.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(get("/api/v1/tools/" + tool))
        .andExpect(jsonPath("images[0].location").value(first))
        .andExpect(jsonPath("images[1].id").value(extraImage));

    mvc.perform(
            multipart("/api/v1/tools/" + tool + "/images/main")
                .file(png(5))
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
    String replaced =
        location(
            mvc.perform(
                    multipart("/api/v1/tools/" + tool + "/images/main")
                        .file(png(5))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    mvc.perform(get("/api/v1/tools/" + tool))
        .andExpect(jsonPath("images.length()").value(2))
        .andExpect(jsonPath("images[0].location").value(replaced))
        .andExpect(jsonPath("images[1].id").value(extraImage));

    var fake = new MockMultipartFile("file", "x.png", "image/png", "no".getBytes());
    mvc.perform(
            multipart("/api/v1/tools/" + tool + "/images/main")
                .file(fake)
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(3)
  void withoutImagesTheToolFallsBackToTheDefault() throws Exception {
    var images =
        JSON.readTree(
                mvc.perform(get("/api/v1/tools/" + tool))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("images");
    for (var image : images)
      mvc.perform(
              delete("/api/v1/tools/" + tool + "/images/" + image.get("id").asLong())
                  .header("Authorization", "Bearer " + admin))
          .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/tools/" + tool)).andExpect(jsonPath("images.length()").value(0));
  }
}
