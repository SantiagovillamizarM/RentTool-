package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.*;
import com.renttool.infrastructure.identity.web.IdentityDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Autenticación y perfil", description = "Registro, inicio de sesión y perfil propio")
public class AuthController {
  private final RegisterAccountUseCase register;
  private final LoginUseCase login;
  private final GetProfileUseCase getProfile;
  private final UpdateProfileUseCase updateProfile;

  public AuthController(
      RegisterAccountUseCase register,
      LoginUseCase login,
      GetProfileUseCase getProfile,
      UpdateProfileUseCase updateProfile) {
    this.register = register;
    this.login = login;
    this.getProfile = getProfile;
    this.updateProfile = updateProfile;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar una cuenta",
      description = "Crea una cuenta CLIENTE o PROVEEDOR y devuelve un token listo para usar.")
  @ApiResponse(responseCode = "201", description = "Cuenta creada; incluye el token JWT")
  @ApiResponse(responseCode = "400", description = "Datos inválidos")
  @ApiResponse(responseCode = "403", description = "Se intentó registrar un ADMINISTRADOR")
  @ApiResponse(responseCode = "409", description = "El correo ya está registrado")
  public SessionResponse register(@Valid @RequestBody RegistrationRequest r) {
    return SessionResponse.from(
        register.register(
            new RegisterAccountUseCase.Command(r.name(), r.email(), r.password(), r.role())));
  }

  @PostMapping("/auth/login")
  @Operation(
      summary = "Iniciar sesión",
      description = "Devuelve un token JWT válido por una hora.")
  @ApiResponse(responseCode = "200", description = "Credenciales correctas")
  @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
  public SessionResponse login(@Valid @RequestBody LoginRequest l) {
    return SessionResponse.from(login.login(l.email(), l.password()));
  }

  @GetMapping("/me")
  @Operation(summary = "Ver mi perfil", security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Perfil de la cuenta del token")
  @ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(getProfile.getActive(Long.valueOf(jwt.getSubject())));
  }

  @PutMapping("/me")
  @Operation(summary = "Cambiar mi nombre", security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Perfil actualizado")
  @ApiResponse(responseCode = "400", description = "Nombre vacío o demasiado largo")
  @ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
  public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest p) {
    return UserResponse.from(updateProfile.rename(Long.valueOf(jwt.getSubject()), p.name()));
  }
}
