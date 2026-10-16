package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.*;
import com.renttool.application.identity.port.in.ProviderDetails;
import com.renttool.application.offboarding.port.in.RemoveProviderUseCase;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.PersonType;
import com.renttool.domain.identity.ProviderProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Contratos HTTP del alta de proveedores por el administrador. */
public final class ProviderDtos {
  private ProviderDtos() {}

  public record ProfileRequest(
      @Schema(example = "JURIDICA", description = "NATURAL o JURIDICA") @NotNull
          PersonType personType,
      @Schema(example = "900123456", description = "Sin puntos ni dígito de verificación")
          @NotBlank
          @Pattern(regexp = "\\d{5,15}", message = "debe tener entre 5 y 15 dígitos")
          String nit,
      @Schema(example = "8", description = "Dígito de verificación DIAN") @NotNull @Min(0) @Max(9)
          Integer verificationDigit,
      @Schema(example = "Obras y Equipos SAS", description = "Razón social o nombre completo")
          @NotBlank
          @Size(max = 200)
          String legalName,
      @Schema(example = "ObraYa", description = "Nombre comercial, opcional") @Size(max = 160)
          String tradeName,
      @Schema(example = "Carlos Pérez", description = "Obligatorio para persona jurídica")
          @Size(max = 160)
          String legalRepresentative,
      @Schema(example = "CC 79123456", description = "Obligatorio para persona jurídica")
          @Size(max = 40)
          String representativeDocument,
      @Schema(example = "+57 601 555 1234") @NotBlank @Size(max = 20) String phone,
      @Schema(example = "Calle 80 # 10-20") @NotBlank @Size(max = 200) String address,
      @Schema(example = "Bogotá") @NotBlank @Size(max = 100) String city) {
    ProfileCommand toCommand() {
      return new ProfileCommand(
          personType,
          nit,
          verificationDigit,
          legalName,
          tradeName,
          legalRepresentative,
          representativeDocument,
          phone,
          address,
          city);
    }
  }

  public record RegisterProviderRequest(
      @Schema(example = "ventas@obraya.co", description = "Correo con que el proveedor entra")
          @NotBlank
          @Email
          @Size(max = 190)
          String email,
      @Schema(
              example = "temporal-2026",
              description = "Entre 10 y 64 caracteres. El proveedor la cambia al entrar")
          @NotBlank
          @Size(min = 10, max = 64)
          String temporaryPassword,
      @NotNull @Valid ProfileRequest profile) {
    RegisterCommand toCommand() {
      return new RegisterCommand(email, temporaryPassword, profile.toCommand());
    }
  }

  public record StatusRequest(@Schema(example = "false") @NotNull Boolean active) {}

  public record PasswordResetRequest(
      @Schema(example = "temporal-2027") @NotBlank @Size(min = 10, max = 64)
          String temporaryPassword) {}

  public record ProfileResponse(
      PersonType personType,
      @Schema(example = "900123456") String nit,
      @Schema(example = "8") int verificationDigit,
      String legalName,
      String tradeName,
      String legalRepresentative,
      String representativeDocument,
      String phone,
      String address,
      String city) {
    static ProfileResponse from(ProviderProfile p) {
      return p == null
          ? null
          : new ProfileResponse(
              p.personType(),
              p.nit().number(),
              p.nit().verificationDigit(),
              p.legalName(),
              p.tradeName(),
              p.legalRepresentative(),
              p.representativeDocument(),
              p.phone(),
              p.address(),
              p.city());
    }
  }

  public record ProviderResponse(
      @Schema(example = "12") Long id,
      @Schema(example = "ObraYa") String name,
      @Schema(example = "ventas@obraya.co") String email,
      boolean active,
      @Schema(description = "Aún no ha reemplazado la contraseña temporal")
          boolean mustChangePassword,
      @Schema(description = "Null si la cuenta se creó antes del alta por administrador")
          ProfileResponse profile,
      @Schema(
              example = "/uploads/3f2c.png",
              description = "Null si no tiene logo; la interfaz muestra uno predeterminado")
          String logoUrl) {
    static ProviderResponse from(ProviderDetails d) {
      var a = d.account();
      return new ProviderResponse(
          a.id(),
          a.name(),
          a.email(),
          a.active(),
          a.mustChangePassword(),
          ProfileResponse.from(d.profile()),
          d.profile() == null ? null : d.profile().logoLocation());
    }
  }

  public record RemovalResponse(
      @Schema(example = "12") Long providerId,
      @Schema(example = "ObraYa") String providerName,
      @Schema(example = "800197268-4") String taxId,
      @Schema(description = "Herramientas que salen del catálogo público") int activeTools,
      @Schema(description = "Solicitudes sin responder que se cancelan") int pendingRequests,
      @Schema(description = "Reservas aceptadas que aún no empiezan y se cancelan")
          int upcomingRentals,
      @Schema(description = "Alquileres ya iniciados: siguen hasta la devolución")
          int rentalsInProgress) {
    static RemovalResponse from(RemoveProviderUseCase.Summary s) {
      return new RemovalResponse(
          s.providerId(),
          s.providerName(),
          s.taxId(),
          s.activeTools(),
          s.pendingRequests(),
          s.upcomingRentals(),
          s.rentalsInProgress());
    }
  }

  public record ProviderPage(
      List<ProviderResponse> content, long totalElements, int totalPages, int page) {
    static ProviderPage from(PageResult<ProviderDetails> p) {
      return new ProviderPage(
          p.content().stream().map(ProviderResponse::from).toList(),
          p.totalElements(),
          p.totalPages(),
          p.page());
    }
  }
}
