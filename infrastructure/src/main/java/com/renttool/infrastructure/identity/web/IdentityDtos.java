package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.AuthenticatedSession;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;

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
      @Schema(
              example = "CLIENTE",
              description = "Opcional; solo se admite CLIENTE. Los proveedores los crea el administrador")
          Role role,
      @Schema(
              example = "+57 300 123 4567",
              description = "Obligatorio: el proveedor lo usa para coordinar pago y entrega")
          @NotBlank
          @Pattern(regexp = "\\+?[\\d ]{7,20}", message = "solo admite dígitos, espacios y +, mínimo 7")
          String phone) {}

  public record LoginRequest(
      @Schema(example = "laura@ejemplo.com") @NotBlank @Email String email,
      @Schema(example = "una-clave-segura-123") @NotBlank String password) {}

  public record ProfileRequest(
      @Schema(example = "Laura Gómez Ruiz") @NotBlank @Size(max = 120) String name,
      @Schema(example = "+57 300 123 4567", description = "Opcional; solo para clientes")
          @Pattern(regexp = "\\+?[\\d ]{7,20}", message = "solo admite dígitos, espacios y +, mínimo 7")
          String phone) {}

  public record PasswordChangeRequest(
      @Schema(example = "temporal-2026") @NotBlank String currentPassword,
      @Schema(example = "mi-clave-nueva-456", description = "Entre 10 y 64 caracteres")
          @NotBlank
          @Size(min = 10, max = 64)
          String newPassword) {}

  public record UserResponse(
      @Schema(example = "7") Long id,
      @Schema(example = "Laura Gómez") String name,
      @Schema(example = "laura@ejemplo.com") String email,
      @Schema(example = "CLIENTE") Role role,
      @Schema(
              description =
                  "La contraseña es temporal: hasta cambiarla solo se admiten GET /me y PUT"
                      + " /me/password")
          boolean mustChangePassword,
      @Schema(description = "Logo del proveedor; null para otros roles o si no tiene")
          String logoUrl,
      @Schema(example = "+57 300 123 4567", description = "Teléfono del cliente; puede faltar")
          String phone) {
    public static UserResponse from(Account a, String logoUrl) {
      return new UserResponse(
          a.id(), a.name(), a.email(), a.role(), a.mustChangePassword(), logoUrl, a.phone());
    }
  }

  public record SessionResponse(
      @Schema(
              example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6IkNMSUVOVEUifQ.firma",
              description = "JWT para el header Authorization: Bearer <token>")
          String token,
      @Schema(
              example = "2026-10-15T15:10:00Z",
              description =
                  "Vencimiento del token: 10 minutos para el administrador (renovable con"
                      + " /auth/refresh mientras haya actividad), una hora para los demás")
          Instant expiresAt,
      UserResponse user) {
    public static SessionResponse from(AuthenticatedSession s, String logoUrl) {
      return new SessionResponse(
          s.token(), s.expiresAt(), UserResponse.from(s.account(), logoUrl));
    }
  }
}
