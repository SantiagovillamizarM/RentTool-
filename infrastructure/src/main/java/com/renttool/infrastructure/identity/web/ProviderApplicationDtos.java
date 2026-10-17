package com.renttool.infrastructure.identity.web;

import com.renttool.application.identity.port.in.ReviewProviderApplicationsUseCase.ApplicationDetails;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.ProviderApplication;
import com.renttool.domain.identity.ProviderApplication.Status;
import com.renttool.infrastructure.identity.web.ProviderDtos.ProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Contratos HTTP de las solicitudes para ser proveedor. */
public final class ProviderApplicationDtos {
  private ProviderApplicationDtos() {}

  public record RejectionRequest(
      @Schema(example = "El NIT no coincide con la razón social registrada en el RUT")
          @NotBlank
          @Size(max = 500)
          String reason) {}

  public record ApplicationResponse(
      @Schema(example = "3") Long id,
      @Schema(example = "PENDIENTE") Status status,
      @Schema(example = "ObraYa", description = "Nombre comercial, o la razón social si no hay")
          String displayName,
      ProfileResponse profile,
      Instant submittedAt,
      Instant decidedAt,
      @Schema(description = "Solo en RECHAZADA") String rejectionReason,
      @Schema(description = "La respuesta aún no se le ha mostrado al solicitante")
          boolean needsNotice) {
    static ApplicationResponse from(ProviderApplication a) {
      return new ApplicationResponse(
          a.id(),
          a.status(),
          a.profile().displayName(),
          ProfileResponse.from(a.profile()),
          a.submittedAt(),
          a.decidedAt(),
          a.rejectionReason(),
          a.needsNotice());
    }
  }

  public record ApplicantResponse(
      @Schema(example = "7") Long id,
      @Schema(example = "Ana Ruiz") String name,
      @Schema(example = "ana@obraya.co") String email) {}

  public record ReviewResponse(ApplicationResponse application, ApplicantResponse applicant) {
    static ReviewResponse from(ApplicationDetails d) {
      var a = d.applicant();
      return new ReviewResponse(
          ApplicationResponse.from(d.application()),
          a == null ? null : new ApplicantResponse(a.id(), a.name(), a.email()));
    }
  }

  public record ReviewPage(
      List<ReviewResponse> content, long totalElements, int totalPages, int page) {
    static ReviewPage from(PageResult<ApplicationDetails> p) {
      return new ReviewPage(
          p.content().stream().map(ReviewResponse::from).toList(),
          p.totalElements(),
          p.totalPages(),
          p.page());
    }
  }
}
