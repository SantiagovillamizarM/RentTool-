package com.renttool.infrastructure.insights.web;

import com.renttool.application.insights.port.in.RevenueReportUseCase;
import com.renttool.application.insights.port.in.ViewStatisticsUseCase;
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
@RequestMapping("/api/v1/statistics")
@Tag(name = "Estadísticas")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = com.renttool.infrastructure.config.OpenApiConfig.BEARER)
public class StatisticsController {
  private final ViewStatisticsUseCase statistics;
  private final RevenueReportUseCase revenue;
  public StatisticsController(ViewStatisticsUseCase statistics, RevenueReportUseCase revenue) {
    this.statistics = statistics;
    this.revenue = revenue;
  }
  @GetMapping("/revenue")
  @Operation(summary = "Ingresos y rentabilidad por herramienta o por mes",
      description = "PROVEEDOR: sus herramientas. ADMINISTRADOR: toda la plataforma. Cuentan las"
          + " reservas solicitadas en el rango que son ventas (aceptadas con pago aprobado).")
  public RevenueReportUseCase.Report revenue(@AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "TOOL") RevenueReportUseCase.GroupBy groupBy) {
    return revenue.get(actor(jwt), new Range(from, to), groupBy);
  }
  private static Participant actor(Jwt jwt) {
    return jwt == null ? Participant.anonymous() : new Participant(Long.valueOf(jwt.getSubject()),
        Participant.Role.valueOf(jwt.getClaimAsString("role")));
  }
  @GetMapping
  @Operation(summary = "Resumen de reservas solicitadas entre dos fechas",
      description = "Solo proveedores: cada uno ve únicamente sus herramientas, incluso retiradas.")
  public ViewStatisticsUseCase.Summary get(@AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var actor = jwt == null ? Participant.anonymous() : new Participant(Long.valueOf(jwt.getSubject()),
        Participant.Role.valueOf(jwt.getClaimAsString("role")));
    return statistics.get(actor, new Range(from, to));
  }
}
