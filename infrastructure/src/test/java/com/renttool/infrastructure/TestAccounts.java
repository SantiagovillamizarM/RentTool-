package com.renttool.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.renttool.domain.identity.Nit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Crea cuentas para las pruebas de API. Los proveedores ya no se registran solos: el administrador
 * provisionado por {@link #ADMIN_PROPERTIES} los da de alta y cada uno cambia su clave temporal.
 */
final class TestAccounts {
  static final String ADMIN_EMAIL = "admin@test.example";
  static final String ADMIN_PASSWORD = "admin-test-password";
  static final String ADMIN_EMAIL_PROPERTY = "renttool.admin-email=" + ADMIN_EMAIL;
  static final String ADMIN_PASSWORD_PROPERTY = "renttool.admin-password=" + ADMIN_PASSWORD;
  static final String PASSWORD = "correct-test-password";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicLong NIT = new AtomicLong(900_000_000);

  private TestAccounts() {}

  static String login(MockMvc mvc, String email, String password) throws Exception {
    return field(
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "token");
  }

  static String admin(MockMvc mvc) throws Exception {
    return login(mvc, ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  static String client(MockMvc mvc, String email) throws Exception {
    return field(
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"Cliente de prueba\",\"email\":\""
                            + email
                            + "\",\"password\":\""
                            + PASSWORD
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "token");
  }

  /** JSON de alta con un NIT nuevo y válido en cada llamada. */
  static String providerPayload(String email, String temporaryPassword) {
    String nit = String.valueOf(NIT.incrementAndGet());
    return "{\"email\":\""
        + email
        + "\",\"temporaryPassword\":\""
        + temporaryPassword
        + "\",\"profile\":{\"personType\":\"JURIDICA\",\"nit\":\""
        + nit
        + "\",\"verificationDigit\":"
        + Nit.verificationDigitOf(nit)
        + ",\"legalName\":\"Proveedor "
        + nit
        + " SAS\",\"legalRepresentative\":\"Ana Ruiz\",\"representativeDocument\":\"CC 1020"
        + "\",\"phone\":\"3001234567\",\"address\":\"Calle 1 # 2-3\",\"city\":\"Bogotá\"}}";
  }

  /** Alta por el administrador, cambio de la clave temporal y token final del proveedor. */
  static String provider(MockMvc mvc, String email) throws Exception {
    mvc.perform(
            post("/api/v1/admin/providers")
                .header("Authorization", "Bearer " + admin(mvc))
                .contentType("application/json")
                .content(providerPayload(email, "temporal-password")))
        .andExpect(status().isCreated());
    String token = login(mvc, email, "temporal-password");
    mvc.perform(
            put("/api/v1/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    "{\"currentPassword\":\"temporal-password\",\"newPassword\":\""
                        + PASSWORD
                        + "\"}"))
        .andExpect(status().isOk());
    return token;
  }

  static String field(String json, String name) throws Exception {
    return JSON.readTree(json).get(name).asString();
  }
}
