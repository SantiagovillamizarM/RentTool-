package com.renttool.infrastructure.insights.web;

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
  public StatisticsController(ViewStatisticsUseCase statistics) { this.statistics = statistics; }
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
