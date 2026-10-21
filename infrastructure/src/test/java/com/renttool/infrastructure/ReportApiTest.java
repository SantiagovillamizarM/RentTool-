package com.renttool.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ReportApiTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  MockMvc mvc;
  static String admin, provider, client;
  static long reservation;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  String bearer(String token) {
    return "Bearer " + token;
  }

  long id(ResultActions result) throws Exception {
    return JSON.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  @Order(1)
  void everyChangeIsRecordedWithAuthorAndTime() throws Exception {
    admin = TestAccounts.admin(mvc);
    provider = TestAccounts.provider(mvc, "rep-provider@test.example");
    // Nombre malicioso: en una hoja de cálculo se ejecutaría como fórmula.
    client =
        TestAccounts.field(
            mvc.perform(
                    post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"=HYPERLINK(\\\"http://x\\\")\",\"email\":\"rep-client@test.example\","
                                + "\"password\":\"correct-test-password\",\"phone\":\"3001234567\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "token");
    long tool =
        id(
            mvc.perform(
                    post("/api/v1/tools")
                        .header("Authorization", bearer(provider))
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Pulidora; industrial\",\"categoryId\":1,"
                                + "\"description\":\"Prueba\",\"dailyRate\":12500.50}"))
                .andExpect(status().isCreated()));
    mvc.perform(
            post("/api/v1/tools/" + tool + "/units")
                .header("Authorization", bearer(provider))
                .contentType("application/json")
                .content("{\"code\":\"PUL-1\",\"status\":\"OPERATIVA\"}"))
        .andExpect(status().isCreated());
    reservation =
        id(
            mvc.perform(
                    post("/api/v1/reservations")
                        .header("Authorization", bearer(client))
                        .contentType("application/json")
                        .content(
                            "{\"toolId\":"
                                + tool
                                + ",\"start\":\""
                                + TODAY.plusDays(5)
                                + "\",\"end\":\""
                                + TODAY.plusDays(7)
                                + "\",\"quantity\":1}"))
                .andExpect(status().isCreated()));
    TestAccounts.pay(mvc, client, reservation);
    mvc.perform(
            post("/api/v1/reservations/" + reservation + "/accept")
                .header("Authorization", bearer(provider)))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/v1/reservations/" + reservation).header("Authorization", bearer(admin)))
        .andExpect(status().isNoContent());

    mvc.perform(
            get("/api/v1/reservations/" + reservation + "/history")
                .header("Authorization", bearer(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].action").value("SOLICITADA"))
        .andExpect(jsonPath("$[0].actorRole").value("CLIENTE"))
        .andExpect(jsonPath("$[1].action").value("ACEPTADA"))
        .andExpect(jsonPath("$[1].actorRole").value("PROVEEDOR"))
        .andExpect(jsonPath("$[2].action").value("CANCELADA"))
        .andExpect(jsonPath("$[2].actorRole").value("ADMINISTRADOR"))
        .andExpect(jsonPath("$[2].actorName").value("Administrador"))
        .andExpect(jsonPath("$[2].at").exists())
        .andExpect(jsonPath("$[2].recorded").value(true));
    String stranger = TestAccounts.client(mvc, "rep-stranger@test.example");
    mvc.perform(
            get("/api/v1/reservations/" + reservation + "/history")
                .header("Authorization", bearer(stranger)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void administratorDownloadsReservationsAsExcelReadyCsv() throws Exception {
    int before = jdbc.queryForObject("select count(*) from report_downloads", Integer.class);
    var response =
        mvc.perform(
                get("/api/v1/admin/reports/reservations.csv")
                    .param("from", TODAY.toString())
                    .param("to", TODAY.toString())
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString(
                            "renttool-reservas-" + TODAY + "_a_" + TODAY + ".csv")))
            .andReturn()
            .getResponse();
    byte[] bytes = response.getContentAsByteArray();
    String csv = new String(bytes, StandardCharsets.UTF_8);
    assertTrue(csv.startsWith("﻿ID reserva;Fecha de solicitud;Cliente;Correo del cliente;"));
    String row =
        csv.lines().filter(l -> l.startsWith(reservation + ";")).findFirst().orElseThrow();
    assertTrue(row.contains("rep-client@test.example"), row);
    assertTrue(row.contains(";\"'=HYPERLINK(\"\"http://x\"\")\";"), "fórmula neutralizada: " + row);
    assertTrue(row.contains(";\"Pulidora; industrial\";"), "texto con separador entre comillas");
    assertTrue(row.contains(";12500,50;25001,00;"), "coma decimal: " + row);
    assertTrue(row.contains(";Cancelada;Cancelada;"), row);
    assertTrue(row.contains(";Administrador;Administrador;"), "quién y con qué rol: " + row);
    assertTrue(row.contains("-") && row.contains(":"), "fecha y hora del cambio");
    assertEquals(before + 1, jdbc.queryForObject("select count(*) from report_downloads", Integer.class));

    String filtered =
        mvc.perform(
                get("/api/v1/admin/reports/reservations.csv")
                    .param("from", TODAY.toString())
                    .param("to", TODAY.toString())
                    .param("status", "ACEPTADA")
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertFalse(filtered.contains("\r\n" + reservation + ";"), "el filtro excluye la cancelada");
  }

  @Test
  @Order(3)
  void eventLogListsEveryAction() throws Exception {
    String csv =
        mvc.perform(
                get("/api/v1/admin/reports/reservation-events.csv")
                    .param("from", TODAY.toString())
                    .param("to", TODAY.toString())
                    .header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    var rows = csv.lines().filter(l -> l.contains(";" + reservation + ";")).toList();
    assertEquals(3, rows.size(), csv);
    assertTrue(rows.get(0).contains(";Solicitada;"));
    assertTrue(rows.get(1).contains(";Aceptada;") && rows.get(1).contains(";Proveedor;"));
    assertTrue(rows.get(2).contains(";Cancelada;Administrador;Administrador;admin@test.example;"));
  }

  @Test
  @Order(4)
  void onlyAdministratorsAndValidRanges() throws Exception {
    mvc.perform(
            get("/api/v1/admin/reports/reservations.csv")
                .param("from", TODAY.toString())
                .param("to", TODAY.toString())
                .header("Authorization", bearer(client)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/admin/reports/reservations.csv")
                .param("from", TODAY.toString())
                .param("to", TODAY.minusDays(1).toString())
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/admin/reports/reservation-events.csv")
                .param("from", TODAY.minusDays(400).toString())
                .param("to", TODAY.toString())
                .header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/admin/reports/reservations.csv").header("Authorization", bearer(admin)))
        .andExpect(status().isBadRequest());
  }
}
