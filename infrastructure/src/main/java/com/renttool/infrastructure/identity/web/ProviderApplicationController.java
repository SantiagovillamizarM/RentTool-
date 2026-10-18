package com.renttool.infrastructure.identity.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.identity.port.in.ApplyAsProviderUseCase;
import com.renttool.application.identity.port.in.GetProfileUseCase;
import com.renttool.application.identity.port.in.ManageOwnCompanyUseCase;
import com.renttool.application.identity.port.in.ReviewProviderApplicationsUseCase;
import com.renttool.application.shared.PageQuery;
import com.renttool.domain.identity.ProviderApplication.Status;
import com.renttool.domain.shared.exception.NotFoundException;
import com.renttool.infrastructure.identity.web.ProviderApplicationDtos.*;
import com.renttool.infrastructure.identity.web.ProviderDtos.ProfileRequest;
import com.renttool.infrastructure.identity.web.ProviderDtos.CompanyRequest;
import com.renttool.infrastructure.identity.web.ProviderDtos.ProfileResponse;
import com.renttool.infrastructure.identity.web.ProviderDtos.ProviderResponse;
import com.renttool.infrastructure.identity.web.ProviderDtos.PublicProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Solicitudes de proveedor",
    description =
        "Un cliente pide ser proveedor con sus datos legales; el ADMINISTRADOR la acepta o la"
            + " rechaza con un motivo")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
public class ProviderApplicationController {
  private final ApplyAsProviderUseCase apply;
  private final ReviewProviderApplicationsUseCase review;
  private final GetProfileUseCase profiles;
  private final ManageOwnCompanyUseCase company;

  public ProviderApplicationController(
      ApplyAsProviderUseCase apply,
      ReviewProviderApplicationsUseCase review,
      GetProfileUseCase profiles,
      ManageOwnCompanyUseCase company) {
    this.apply = apply;
    this.review = review;
    this.profiles = profiles;
    this.company = company;
  }

  @PostMapping("/me/provider-application")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Solicitar ser proveedor",
      description =
          "Solo CLIENTE. Envía los datos legales; queda PENDIENTE hasta que el administrador la"
              + " revise. Al aceptarse, la cuenta pasa a PROVEEDOR.")
  @ApiResponse(responseCode = "201", description = "Solicitud enviada")
  @ApiResponse(responseCode = "400", description = "Datos inválidos o NIT con dígito incorrecto")
  @ApiResponse(responseCode = "403", description = "La cuenta no es de cliente")
  @ApiResponse(
      responseCode = "409",
      description = "Ya tiene una solicitud en revisión o el NIT ya está en uso")
  public ApplicationResponse submit(
      @Valid @RequestBody ProfileRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ApplicationResponse.from(apply.submit(accountId(jwt), r.toCommand()));
  }

  @GetMapping("/me/provider-application")
  @Operation(
      summary = "Ver mi solicitud",
      description =
          "La más reciente. needsNotice indica que aún no se le mostró la respuesta. 204 si"
              + " nunca envió una.")
  @ApiResponse(responseCode = "200", description = "Solicitud")
  @ApiResponse(responseCode = "204", description = "No tiene solicitudes")
  public ResponseEntity<ApplicationResponse> current(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return apply
        .current(accountId(jwt))
        .map(ApplicationResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/me/provider-application/acknowledge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Marcar la respuesta como vista",
      description = "Para no volver a mostrar el aviso de aceptación o rechazo.")
  @ApiResponse(responseCode = "204", description = "Hecho")
  public void acknowledge(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    apply.acknowledge(accountId(jwt));
  }

  @GetMapping("/me/provider-profile")
  @Operation(
      summary = "Ver mis datos de proveedor",
      description =
          "Datos de la empresa. El NIT y el tipo de persona solo los cambia el administrador.")
  @ApiResponse(responseCode = "200", description = "Datos legales")
  @ApiResponse(responseCode = "404", description = "La cuenta no tiene datos de proveedor")
  public ProfileResponse providerProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return profiles
        .providerProfileOf(accountId(jwt))
        .map(ProfileResponse::from)
        .orElseThrow(() -> new NotFoundException("No hay datos de proveedor registrados"));
  }

  @PutMapping("/me/provider-profile")
  @Operation(
      summary = "Actualizar los datos de mi empresa",
      description =
          "Solo PROVEEDOR. Nombre, representante, contacto y dirección; el NIT y el tipo de persona"
              + " se conservan. La cuenta se muestra con el nombre comercial, o la razón social si no"
              + " tiene.")
  @ApiResponse(responseCode = "200", description = "Datos actualizados")
  @ApiResponse(responseCode = "400", description = "Datos inválidos")
  @ApiResponse(responseCode = "403", description = "La cuenta no es de proveedor")
  public ProviderResponse updateCompany(
      @Valid @RequestBody CompanyRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(company.updateOwn(accountId(jwt), r.toCommand()));
  }

  @PostMapping(value = "/me/provider-profile/logo", consumes = "multipart/form-data")
  @Operation(
      summary = "Subir o reemplazar mi logo",
      description = "Solo PROVEEDOR. Archivo PNG o JPEG real de hasta 2 MB en el campo file.")
  @ApiResponse(responseCode = "200", description = "Logo guardado")
  @ApiResponse(responseCode = "400", description = "No es una imagen válida o supera 2 MB")
  @ApiResponse(responseCode = "403", description = "La cuenta no es de proveedor")
  public ProviderResponse uploadLogo(
      @RequestParam("file") MultipartFile file,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt)
      throws IOException {
    return ProviderResponse.from(company.setOwnLogo(accountId(jwt), file.getBytes()));
  }

  @DeleteMapping("/me/provider-profile/logo")
  @Operation(summary = "Quitar mi logo", description = "Vuelve al logo predeterminado.")
  @ApiResponse(responseCode = "200", description = "Logo quitado")
  @ApiResponse(responseCode = "403", description = "La cuenta no es de proveedor")
  public ProviderResponse removeLogo(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ProviderResponse.from(company.removeOwnLogo(accountId(jwt)));
  }

  @GetMapping("/providers")
  @SecurityRequirements
  @Operation(
      summary = "Listar proveedores",
      description = "Público. Proveedores activos por nombre, para filtrar el catálogo.")
  @ApiResponse(responseCode = "200", description = "Proveedores")
  public List<PublicProviderResponse> publicProviders() {
    return company.publicProviders().stream().map(PublicProviderResponse::from).toList();
  }

  @GetMapping("/admin/provider-applications")
  @Operation(
      summary = "Listar solicitudes",
      description = "Solo ADMINISTRADOR. Por defecto las PENDIENTE, de la más antigua a la más nueva.")
  @ApiResponse(responseCode = "200", description = "Página de solicitudes")
  @ApiResponse(responseCode = "403", description = "Quien llama no es ADMINISTRADOR")
  public ReviewPage list(
      @Parameter(description = "PENDIENTE, ACEPTADA o RECHAZADA; vacío incluye todas")
          @RequestParam(required = false, defaultValue = "PENDIENTE")
          Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ReviewPage.from(review.list(accountId(jwt), status, new PageQuery(page, size)));
  }

  @PostMapping("/admin/provider-applications/{id}/accept")
  @Operation(
      summary = "Aceptar una solicitud",
      description =
          "Solo ADMINISTRADOR. La cuenta del solicitante pasa a PROVEEDOR con sus datos legales y"
              + " se muestra con su nombre comercial.")
  @ApiResponse(responseCode = "200", description = "Aceptada")
  @ApiResponse(responseCode = "404", description = "La solicitud no existe")
  @ApiResponse(
      responseCode = "409",
      description = "Ya fue resuelta, la cuenta ya no está disponible o el NIT ya está en uso")
  public ReviewResponse accept(
      @PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ReviewResponse.from(review.accept(accountId(jwt), id));
  }

  @PostMapping("/admin/provider-applications/{id}/reject")
  @Operation(
      summary = "Rechazar una solicitud",
      description = "Solo ADMINISTRADOR. El motivo se le muestra al solicitante.")
  @ApiResponse(responseCode = "200", description = "Rechazada")
  @ApiResponse(responseCode = "400", description = "Falta el motivo")
  @ApiResponse(responseCode = "404", description = "La solicitud no existe")
  @ApiResponse(responseCode = "409", description = "Ya fue resuelta")
  public ReviewResponse reject(
      @PathVariable Long id,
      @Valid @RequestBody RejectionRequest r,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return ReviewResponse.from(review.reject(accountId(jwt), id, r.reason()));
  }

  private static Long accountId(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }
}
