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

  public record HistoryResponse(
      @Schema(example = "2026-10-15T14:30:00Z", description = "Null si no quedó registrado")
          Instant at,
      @Schema(example = "ACEPTADA") ReservationStatus action,
      @Schema(example = "PROVEEDOR", description = "CLIENTE, PROVEEDOR, ADMINISTRADOR o SISTEMA")
          ReservationEvent.ActorRole actorRole,
      @Schema(example = "4") Long actorId,
      @Schema(example = "ObraYa") String actorName,
      @Schema(example = "Unidades asignadas: [#3, #4]") String detail,
      @Schema(description = "False para cambios anteriores a que existiera el historial")
          boolean recorded) {
    public static HistoryResponse from(HistoryEntry h) {
      return new HistoryResponse(
          h.at(), h.action(), h.actorRole(), h.actorId(), h.actorName(), h.detail(), h.recorded());
    }
  }

  public record ReservationResponse(
      @Schema(example = "41") Long id,
      @Schema(example = "12") Long toolId,
      @Schema(example = "Taladro percutor 800 W") String toolName,
      @Schema(example = "4") Long providerId,
      @Schema(example = "7") Long clientId,
      @Schema(example = "Laura Gómez") String clientName,
      @Schema(example = "laura@correo.co", description = "Para que el proveedor la contacte")
          String clientEmail,
      @Schema(example = "+57 300 123 4567", description = "Para coordinar el pago y la entrega")
          String clientPhone,
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
      @Schema(example = "Motivo del rechazo, si lo hubo") String note, String rentalStatus,
      @Schema(description = "El cliente ya pagó con la pasarela simulada y el pago no se reembolsó")
          boolean paid) {
    public static ReservationResponse from(ReservationDetails d, String rentalStatus, boolean paid) {
      var r = d.reservation();
      return new ReservationResponse(
          r.id(),
          r.toolId(),
          d.toolName(),
          d.providerId(),
          r.clientId(),
          d.clientName(),
          d.clientEmail(),
          d.clientPhone(),
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
          r.note(), rentalStatus, paid);
    }
  }
}
