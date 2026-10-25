package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.ManageAdministratorsUseCase;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/administrators")
@Tag(
    name = "Administradores",
    description = "Solo ADMINISTRADOR. Alta de otros administradores con los mismos permisos")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "Quien llama no es ADMINISTRADOR")
public class AdministratorController {
  private final ManageAdministratorsUseCase administrators;

  public AdministratorController(ManageAdministratorsUseCase administrators) {
    this.administrators = administrators;
  }

  public record RegisterAdministratorRequest(
      @Schema(example = "Ana Ruiz") @NotBlank @Size(max = 120) String name,
      @Schema(example = "ana@renttool.co") @NotBlank @Email @Size(max = 190) String email,
      @Schema(example = "temporal-admin-2026", description = "Entre 12 y 64 caracteres")
          @NotBlank
          @Size(min = 12, max = 64)
          String temporaryPassword) {}

  public record AdministratorResponse(
      Long id, String name, String email, boolean active, boolean mustChangePassword) {
    static AdministratorResponse from(Account a) {
      return new AdministratorResponse(
          a.id(), a.name(), a.email(), a.active(), a.mustChangePassword());
    }
  }

  public record AdministratorPage(
      List<AdministratorResponse> content, long totalElements, int totalPages, int page) {
    static AdministratorPage from(PageResult<Account> p) {
      return new AdministratorPage(
          p.content().stream().map(AdministratorResponse::from).toList(),
          p.totalElements(),
          p.totalPages(),
          p.page());
    }
  }

  @GetMapping
  @Operation(summary = "Listar administradores")
  @ApiResponse(responseCode = "200", description = "Página de administradores, el más reciente primero")
  public AdministratorPage list(
      @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Tamaño de página, máximo " + PageQuery.MAX_SIZE)
          @RequestParam(defaultValue = "20")
          int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return AdministratorPage.from(administrators.list(adminId(jwt), new PageQuery(page, size)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Agregar un administrador",
      description =
          "Crea una cuenta ADMINISTRADOR con contraseña temporal. Hasta cambiarla, solo puede"
              + " consultar su perfil y cambiar la contraseña.")
  @ApiResponse(responseCode = "201", description = "Administrador creado")
  @ApiResponse(responseCode = "400", description = "Datos inválidos o contraseña muy corta")
  @ApiResponse(responseCode = "409", description = "El correo ya está registrado")
  public AdministratorResponse register(
      @Valid @RequestBody RegisterAdministratorRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return AdministratorResponse.from(
        administrators.register(
            adminId(jwt),
            new ManageAdministratorsUseCase.RegisterCommand(
                r.name(), r.email(), r.temporaryPassword())));
  }

  private static Long adminId(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }
}
