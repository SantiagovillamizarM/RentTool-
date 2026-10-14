package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.AuthenticatedSession;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import jakarta.validation.constraints.*;

/** Contratos HTTP de identidad: validan formato y nunca exponen el hash de la contraseña. */
public final class IdentityDtos {
  private IdentityDtos() {}

  public record RegistrationRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 190) String email,
      @NotBlank @Size(min = 10, max = 64) String password,
      @NotNull Role role) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record ProfileRequest(@NotBlank @Size(max = 120) String name) {}

  public record UserResponse(Long id, String name, String email, Role role) {
    public static UserResponse from(Account a) {
      return new UserResponse(a.id(), a.name(), a.email(), a.role());
    }
  }

  public record SessionResponse(String token, UserResponse user) {
    public static SessionResponse from(AuthenticatedSession s) {
      return new SessionResponse(s.token(), UserResponse.from(s.account()));
    }
  }
}
