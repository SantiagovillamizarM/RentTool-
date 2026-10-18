package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.*;
import com.renttool.domain.identity.Account;
import com.renttool.domain.shared.exception.AuthenticationFailedException;
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
  private final ChangePasswordUseCase changePassword;
  private final RefreshSessionUseCase refresh;

  public AuthController(
      RegisterAccountUseCase register,
      LoginUseCase login,
      GetProfileUseCase getProfile,
      UpdateProfileUseCase updateProfile,
      ChangePasswordUseCase changePassword,
      RefreshSessionUseCase refresh) {
    this.refresh = refresh;
    this.register = register;
    this.login = login;
    this.getProfile = getProfile;
    this.updateProfile = updateProfile;
    this.changePassword = changePassword;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar una cuenta de cliente",
      description =
          "Crea una cuenta CLIENTE y devuelve un token listo para usar. Los proveedores los da"
              + " de alta el administrador en /api/v1/admin/providers.")
  @ApiResponse(responseCode = "201", description = "Cuenta creada; incluye el token JWT")
  @ApiResponse(responseCode = "400", description = "Datos inválidos")
  @ApiResponse(responseCode = "403", description = "Se pidió un rol distinto de CLIENTE")
  @ApiResponse(responseCode = "409", description = "El correo ya está registrado")
  public SessionResponse register(@Valid @RequestBody RegistrationRequest r) {
    return session(
        register.register(
            new RegisterAccountUseCase.Command(
                r.name(), r.email(), r.password(), r.role(), r.phone())));
  }

  @PostMapping("/auth/login")
  @Operation(
      summary = "Iniciar sesión",
      description = "Devuelve un token JWT válido por una hora.")
  @ApiResponse(responseCode = "200", description = "Credenciales correctas")
  @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos")
  public SessionResponse login(@Valid @RequestBody LoginRequest l) {
    return session(login.login(l.email(), l.password()));
  }

  @PostMapping("/auth/refresh")
  @Operation(
      summary = "Renovar el token",
      description =
          "Devuelve un token nuevo para la misma sesión. El panel del administrador lo llama"
              + " mientras hay actividad: sin actividad, su token caduca a los 10 minutos. Ninguna"
              + " sesión se renueva más de 8 horas después de iniciar sesión.",
      security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Token renovado")
  @ApiResponse(
      responseCode = "401",
      description = "Sin token, token vencido, cuenta inactiva o sesión de más de 8 horas")
  public SessionResponse refresh(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) throw new AuthenticationFailedException("Inicia sesión para continuar");
    var authTime = jwt.getClaimAsInstant("auth_time");
    return session(
        refresh.refresh(
            Long.valueOf(jwt.getSubject()), authTime != null ? authTime : jwt.getIssuedAt()));
  }

  @GetMapping("/me")
  @Operation(summary = "Ver mi perfil", security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Perfil de la cuenta del token")
  @ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return user(getProfile.getActive(Long.valueOf(jwt.getSubject())));
  }

  @PutMapping("/me")
  @Operation(
      summary = "Actualizar mi perfil",
      description =
          "Nombre y, para clientes, teléfono de contacto. El nombre del ADMINISTRADOR es fijo y el"
              + " del PROVEEDOR se cambia en sus datos de empresa (PUT /me/provider-profile).",
      security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Perfil actualizado")
  @ApiResponse(responseCode = "400", description = "Nombre vacío o demasiado largo")
  @ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
  @ApiResponse(responseCode = "403", description = "Quien llama es el ADMINISTRADOR")
  public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest p) {
    Long id = Long.valueOf(jwt.getSubject());
    var account = updateProfile.rename(id, p.name());
    if (p.phone() != null && !p.phone().isBlank()) account = updateProfile.changePhone(id, p.phone());
    return user(account);
  }

  @PutMapping("/me/password")
  @Operation(
      summary = "Cambiar mi contraseña",
      description =
          "Exige la contraseña actual. Reemplaza la temporal asignada por el administrador y"
              + " desbloquea el resto de la API. El token vigente sigue siendo válido.",
      security = @SecurityRequirement(name = BEARER))
  @ApiResponse(responseCode = "200", description = "Contraseña cambiada")
  @ApiResponse(
      responseCode = "400",
      description = "Contraseña actual incorrecta, o nueva demasiado corta o igual a la actual")
  @ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
  public UserResponse changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest p) {
    return user(
        changePassword.changePassword(
            Long.valueOf(jwt.getSubject()), p.currentPassword(), p.newPassword()));
  }

  private UserResponse user(Account account) {
    return UserResponse.from(account, logoOf(account));
  }

  private SessionResponse session(AuthenticatedSession s) {
    return SessionResponse.from(s, logoOf(s.account()));
  }

  private String logoOf(Account account) {
    return account.isProvider() ? getProfile.logoOf(account.id()).orElse(null) : null;
  }
}
