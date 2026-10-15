package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.AuthenticatedSession;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/** Contratos HTTP de identidad: validan formato y nunca exponen el hash de la contraseña. */
public final class IdentityDtos {
  private IdentityDtos() {}

  public record RegistrationRequest(
      @Schema(example = "Laura Gómez") @NotBlank @Size(max = 120) String name,
      @Schema(example = "laura@ejemplo.com") @NotBlank @Email @Size(max = 190) String email,
      @Schema(example = "una-clave-segura-123", description = "Entre 10 y 64 caracteres")
          @NotBlank
          @Size(min = 10, max = 64)
          String password,
      @Schema(example = "CLIENTE", description = "CLIENTE o PROVEEDOR") @NotNull Role role) {}

  public record LoginRequest(
      @Schema(example = "laura@ejemplo.com") @NotBlank @Email String email,
      @Schema(example = "una-clave-segura-123") @NotBlank String password) {}

  public record ProfileRequest(
      @Schema(example = "Laura Gómez Ruiz") @NotBlank @Size(max = 120) String name) {}

  public record UserResponse(
      @Schema(example = "7") Long id,
      @Schema(example = "Laura Gómez") String name,
      @Schema(example = "laura@ejemplo.com") String email,
      @Schema(example = "CLIENTE") Role role) {
    public static UserResponse from(Account a) {
      return new UserResponse(a.id(), a.name(), a.email(), a.role());
    }
  }

  public record SessionResponse(
      @Schema(
              example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6IkNMSUVOVEUifQ.firma",
              description = "JWT para el header Authorization: Bearer <token>")
          String token,
      UserResponse user) {
    public static SessionResponse from(AuthenticatedSession s) {
      return new SessionResponse(s.token(), UserResponse.from(s.account()));
    }
  }
}
