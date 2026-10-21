package com.renttool.infrastructure.insights.web;

import com.renttool.application.insights.port.in.ProviderPerformanceUseCase;
import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.Participant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/provider-performance")
@Tag(name = "Estadísticas")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = com.renttool.infrastructure.config.OpenApiConfig.BEARER)
public class ProviderPerformanceController {
  private final ProviderPerformanceUseCase performance;
  public ProviderPerformanceController(ProviderPerformanceUseCase performance) { this.performance = performance; }
  @GetMapping
  @Operation(summary = "Compara las ventas y la respuesta de los proveedores entre dos fechas",
      description = "Solo administrador. Una reserva aceptada cuenta como venta. Incluye alertas para citar a proveedores poco eficientes.")
  public ProviderPerformanceUseCase.Report get(@AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var actor = jwt == null ? Participant.anonymous() : new Participant(Long.valueOf(jwt.getSubject()),
        Participant.Role.valueOf(jwt.getClaimAsString("role")));
    return performance.get(actor, new Range(from, to));
  }
}
