package com.renttool.infrastructure.reservation.web;

import com.renttool.application.reservation.port.in.*;
import com.renttool.domain.reservation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos HTTP de reservas. Validan formato; las reglas de negocio están en el dominio. */
public final class ReservationDtos {
  private ReservationDtos() {}

  public record ReservationRequest(
      @Schema(example = "12") @NotNull Long toolId,
      @Schema(example = "2026-10-20", description = "Día de entrega") @NotNull LocalDate start,
      @Schema(example = "2026-10-23", description = "Día de devolución; no se cobra") @NotNull
          LocalDate end,
      @Schema(example = "1") @Min(1) @Max(Reservation.MAX_QUANTITY) int quantity) {
    public ManageReservationsUseCase.RequestCommand toCommand() {
      return new ManageReservationsUseCase.RequestCommand(toolId, start, end, quantity);
    }
  }

  public record RejectRequest(
      @Schema(example = "Las unidades están comprometidas en otra obra esa semana.")
          @Size(max = Reservation.MAX_NOTE)
          String reason) {}

  public record AvailabilityResponse(
      @Schema(example = "12") Long toolId,
      @Schema(example = "2026-10-20") LocalDate start,
      @Schema(example = "2026-10-23") LocalDate end,
      @Schema(example = "3") long days,
      @Schema(example = "1") int quantity,
      @Schema(example = "4", description = "Unidades libres en esas fechas") long availableUnits,
      @Schema(example = "true", description = "Si alcanzan para la cantidad pedida")
          boolean available,
      @Schema(example = "25000.00") BigDecimal dailyRate,
      @Schema(example = "75000.00", description = "Tarifa diaria × días × cantidad")
          BigDecimal total) {
    public static AvailabilityResponse from(CheckAvailabilityUseCase.Availability a) {
      return new AvailabilityResponse(
          a.toolId(),
          a.period().start(),
          a.period().end(),
          a.period().days(),
          a.quantity(),
          a.availableUnits(),
          a.enough(),
          a.dailyRate(),
          a.total());
    }
  }

  public record ReservationResponse(
      @Schema(example = "41") Long id,
      @Schema(example = "12") Long toolId,
      @Schema(example = "Taladro percutor 800 W") String toolName,
      @Schema(example = "4") Long providerId,
      @Schema(example = "7") Long clientId,
      @Schema(example = "Laura Gómez") String clientName,
      @Schema(example = "2026-10-20") LocalDate start,
      @Schema(example = "2026-10-23") LocalDate end,
      @Schema(example = "3") long days,
      @Schema(example = "1") int quantity,
      @Schema(example = "25000.00", description = "Tarifa pactada al solicitar")
          BigDecimal dailyRate,
      @Schema(example = "75000.00") BigDecimal total,
      @Schema(example = "SOLICITADA") ReservationStatus status,
      @Schema(description = "Unidades asignadas al aceptar") List<Long> unitIds,
      @Schema(example = "2026-10-15T14:05:00Z") Instant requestedAt,
      @Schema(
              example = "2026-10-16T14:05:00Z",
              description = "Plazo del proveedor para responder; solo en SOLICITADA")
          Instant respondBy,
      @Schema(example = "Motivo del rechazo, si lo hubo") String note) {
    public static ReservationResponse from(ReservationDetails d) {
      var r = d.reservation();
      return new ReservationResponse(
          r.id(),
          r.toolId(),
          d.toolName(),
          d.providerId(),
          r.clientId(),
          d.clientName(),
          r.period().start(),
          r.period().end(),
          r.period().days(),
          r.quantity(),
          r.dailyRate(),
          r.total(),
          r.status(),
          r.unitIds().stream().sorted().toList(),
          r.requestedAt(),
          r.respondBy(),
          r.note());
    }
  }
}
