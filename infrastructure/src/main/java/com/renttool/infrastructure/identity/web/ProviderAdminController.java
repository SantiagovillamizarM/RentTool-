package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.ManageProvidersUseCase;
import com.renttool.application.offboarding.port.in.RemoveProviderUseCase;
import com.renttool.application.shared.PageQuery;
import com.renttool.infrastructure.identity.web.ProviderDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/providers")
@Tag(
    name = "Administración de proveedores",
    description = "Solo ADMINISTRADOR. Alta de proveedores con sus datos legales y control de acceso")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "Quien llama no es ADMINISTRADOR")
public class ProviderAdminController {
  private final ManageProvidersUseCase providers;
  private final RemoveProviderUseCase removal;

  public ProviderAdminController(ManageProvidersUseCase providers, RemoveProviderUseCase removal) {
    this.providers = providers;
    this.removal = removal;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar un proveedor",
      description =
          "Crea la cuenta PROVEEDOR con una contraseña temporal y sus datos legales. Hasta que"
              + " el proveedor la cambie, solo puede consultar su perfil y cambiar la contraseña.")
  @ApiResponse(responseCode = "201", description = "Proveedor creado")
  @ApiResponse(responseCode = "400", description = "Datos inválidos o NIT con dígito incorrecto")
  @ApiResponse(responseCode = "409", description = "El correo o el NIT ya están registrados")
  public ProviderResponse register(
      @Valid @RequestBody RegisterProviderRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(providers.register(adminId(jwt), r.toCommand()));
  }

  @GetMapping
  @Operation(
      summary = "Listar proveedores",
      description = "Incluye los inactivos y los antiguos sin datos legales (profile null).")
  @ApiResponse(responseCode = "200", description = "Página de proveedores, el más reciente primero")
  public ProviderPage list(
      @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Tamaño de página, máximo " + PageQuery.MAX_SIZE)
          @RequestParam(defaultValue = "20")
          int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderPage.from(providers.list(adminId(jwt), new PageQuery(page, size)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Ver un proveedor")
  @ApiResponse(responseCode = "200", description = "Proveedor")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  public ProviderResponse get(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(providers.get(adminId(jwt), id));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Actualizar datos legales",
      description = "Reemplaza los datos del proveedor; también completa los de cuentas antiguas.")
  @ApiResponse(responseCode = "200", description = "Proveedor actualizado")
  @ApiResponse(responseCode = "400", description = "Datos inválidos")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  @ApiResponse(responseCode = "409", description = "El NIT pertenece a otro proveedor")
  public ProviderResponse update(
      @PathVariable Long id,
      @Valid @RequestBody ProfileRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(providers.update(adminId(jwt), id, r.toCommand()));
  }

  @PutMapping("/{id}/status")
  @Operation(
      summary = "Activar o desactivar",
      description = "Un proveedor inactivo no puede entrar y sus tokens vigentes dejan de servir.")
  @ApiResponse(responseCode = "200", description = "Estado actualizado")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  public ProviderResponse status(
      @PathVariable Long id,
      @Valid @RequestBody StatusRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(providers.setActive(adminId(jwt), id, r.active()));
  }

  @PostMapping(value = "/{id}/logo", consumes = "multipart/form-data")
  @Operation(
      summary = "Subir o reemplazar el logo",
      description =
          "Opcional. Archivo PNG o JPEG real de hasta 2 MB en el campo `file`. Sin logo, la"
              + " interfaz muestra uno predeterminado.")
  @ApiResponse(responseCode = "200", description = "Logo guardado")
  @ApiResponse(
      responseCode = "400",
      description = "No es una imagen válida, supera 2 MB o el proveedor no tiene datos legales")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  public ProviderResponse uploadLogo(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return ProviderResponse.from(providers.setLogo(adminId(jwt), id, file.getBytes()));
  }

  @DeleteMapping("/{id}/logo")
  @Operation(summary = "Quitar el logo", description = "Vuelve al logo predeterminado.")
  @ApiResponse(responseCode = "200", description = "Logo quitado")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  public ProviderResponse removeLogo(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(providers.removeLogo(adminId(jwt), id));
  }

  @GetMapping("/{id}/removal")
  @Operation(
      summary = "Vista previa de la eliminación",
      description =
          "Cuenta lo que pasaría al eliminar el proveedor, sin cambiar nada: herramientas que"
              + " salen del catálogo, reservas que se cancelan y alquileres en curso.")
  @ApiResponse(responseCode = "200", description = "Resumen")
  @ApiResponse(responseCode = "404", description = "No existe, no es proveedor o ya fue eliminado")
  public RemovalResponse removalPreview(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return RemovalResponse.from(removal.preview(adminId(jwt), id));
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Eliminar un proveedor",
      description =
          "Irreversible. Cierra su cuenta para siempre, retira todas sus herramientas del"
              + " catálogo y cancela sus solicitudes y reservas que aún no empiezan, registrando"
              + " el motivo en el historial. Los alquileres ya iniciados siguen hasta su"
              + " devolución. Sus datos se conservan para el historial y los informes.")
  @ApiResponse(responseCode = "200", description = "Proveedor eliminado; resumen de lo hecho")
  @ApiResponse(responseCode = "404", description = "No existe, no es proveedor o ya fue eliminado")
  public RemovalResponse remove(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return RemovalResponse.from(removal.remove(adminId(jwt), id));
  }

  @PutMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Asignar contraseña temporal",
      description = "Para un proveedor que olvidó la suya. Deberá cambiarla al entrar.")
  @ApiResponse(responseCode = "204", description = "Contraseña temporal asignada")
  @ApiResponse(responseCode = "404", description = "No existe o no es proveedor")
  public void resetPassword(
      @PathVariable Long id,
      @Valid @RequestBody PasswordResetRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    providers.resetPassword(adminId(jwt), id, r.temporaryPassword());
  }

  private static Long adminId(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }
}
