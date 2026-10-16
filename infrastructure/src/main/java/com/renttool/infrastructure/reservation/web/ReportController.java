package com.renttool.infrastructure.reservation.web;

import static com.renttool.infrastructure.config.OpenApiConfig.BEARER;

import com.renttool.application.reservation.port.in.ReservationReportUseCase;
import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.domain.reservation.ReservationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(
    name = "Informes",
    description =
        "Solo ADMINISTRADOR. CSV para Excel (UTF-8, punto y coma, coma decimal). Cada descarga"
            + " queda registrada porque incluye datos personales")
@SecurityRequirement(name = BEARER)
@ApiResponse(responseCode = "400", description = "Rango inválido o mayor a 366 días")
@ApiResponse(responseCode = "401", description = "Sin token, token vencido o cuenta inactiva")
@ApiResponse(responseCode = "403", description = "Quien llama no es ADMINISTRADOR")
public class ReportController {
  private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String UNRECORDED = "Sin registro";

  private final ReservationReportUseCase reports;
  private final ZoneId zone;

  public ReportController(
      ReservationReportUseCase reports,
      @Value("${renttool.time-zone:America/Bogota}") String zone) {
    this.reports = reports;
    this.zone = ZoneId.of(zone);
  }

  @GetMapping(value = "/reservations.csv", produces = "text/csv")
  @Operation(
      summary = "Informe de reservas",
      description =
          "Una fila por reserva solicitada entre from y to (días completos, America/Bogota), con"
              + " cliente y correo, proveedor y NIT, herramienta, fechas, importes, estado y quién"
              + " hizo el último cambio y cuándo.")
  @ApiResponse(responseCode = "200", description = "Archivo CSV")
  public ResponseEntity<byte[]> reservations(
      @Parameter(description = "Fecha inicial, AAAA-MM-DD", example = "2026-09-15")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Fecha final incluida, AAAA-MM-DD", example = "2026-10-15")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(description = "Estado actual; vacío incluye todos")
          @RequestParam(required = false)
          ReservationStatus status,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    var range = new Range(from, to);
    var csv =
        new Csv()
            .row(
                Arrays.asList(
                    "ID reserva",
                    "Fecha de solicitud",
                    "Cliente",
                    "Correo del cliente",
                    "Proveedor",
                    "NIT del proveedor",
                    "Herramienta",
                    "Categoría",
                    "Inicio",
                    "Devolución",
                    "Días",
                    "Unidades",
                    "Tarifa diaria (COP)",
                    "Total (COP)",
                    "Estado actual",
                    "Último cambio",
                    "Fecha del último cambio",
                    "Hecho por",
                    "Rol de quien lo hizo",
                    "Motivo o detalle"));
    for (var r : reports.reservations(ParticipantResolver.from(jwt), range, status)) {
      var last = r.lastChange();
      csv.row(
          Arrays.asList(
              r.id(),
              time(r.requestedAt()),
              r.clientName(),
              r.clientEmail(),
              r.providerName(),
              r.providerTaxId(),
              r.toolName(),
              r.category(),
              r.start(),
              r.end(),
              r.days(),
              r.quantity(),
              r.dailyRate(),
              r.total(),
              Labels.status(r.status()),
              Labels.status(last.action()),
              last.recorded() ? time(last.at()) : UNRECORDED,
              last.recorded() ? last.actorName() : UNRECORDED,
              last.recorded() ? Labels.role(last.actorRole()) : null,
              r.note() != null ? r.note() : last.detail()));
    }
    return file(csv, "reservas", range);
  }

  @GetMapping(value = "/reservation-events.csv", produces = "text/csv")
  @Operation(
      summary = "Historial de eventos",
      description =
          "Una fila por evento ocurrido entre from y to: solicitudes, aceptaciones, rechazos,"
              + " cancelaciones y vencimientos, con quién lo hizo y cuándo. Para auditoría.")
  @ApiResponse(responseCode = "200", description = "Archivo CSV")
  public ResponseEntity<byte[]> events(
      @Parameter(description = "Fecha inicial, AAAA-MM-DD", example = "2026-09-15")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Fecha final incluida, AAAA-MM-DD", example = "2026-10-15")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    var range = new Range(from, to);
    var csv =
        new Csv()
            .row(
                Arrays.asList(
                    "Fecha y hora",
                    "ID reserva",
                    "Herramienta",
                    "Acción",
                    "Hecho por",
                    "Rol",
                    "Correo",
                    "Detalle"));
    for (var e : reports.events(ParticipantResolver.from(jwt), range))
      csv.row(
          Arrays.asList(
              time(e.at()),
              e.reservationId(),
              e.toolName(),
              Labels.status(e.action()),
              e.actorName(),
              Labels.role(e.actorRole()),
              e.actorEmail(),
              e.detail()));
    return file(csv, "historial-reservas", range);
  }

  private static ResponseEntity<byte[]> file(Csv csv, String name, Range range) {
    var filename = "renttool-" + name + "-" + range.from() + "_a_" + range.to() + ".csv";
    return ResponseEntity.ok()
        .contentType(CSV)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(csv.bytes());
  }

  private String time(Instant instant) {
    return instant == null ? null : DATE_TIME.format(instant.atZone(zone));
  }
}
