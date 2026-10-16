package com.renttool.application.reservation.port.in;

import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.ReservationEvent.ActorRole;
import com.renttool.domain.reservation.ReservationStatus;
import com.renttool.domain.shared.exception.InvalidInputException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Informes descargables para el administrador. Cada generación queda registrada porque incluyen
 * datos personales de clientes.
 */
public interface ReservationReportUseCase {
  /** Una fila por reserva solicitada en el rango; {@code status} null incluye todas. */
  List<ReservationRow> reservations(Participant admin, Range range, ReservationStatus status);

  /** Una fila por evento ocurrido en el rango. */
  List<EventRow> events(Participant admin, Range range);

  /** Días completos [from, to] en la zona del negocio, hasta un año. */
  record Range(LocalDate from, LocalDate to) {
    public static final int MAX_DAYS = 366;

    public Range {
      if (from == null || to == null)
        throw new InvalidInputException("Indica la fecha inicial y la final");
      if (to.isBefore(from))
        throw new InvalidInputException("La fecha final no puede ser anterior a la inicial");
      if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS)
        throw new InvalidInputException("El rango admite hasta " + MAX_DAYS + " días");
    }
  }

  record ReservationRow(
      Long id,
      Instant requestedAt,
      String clientName,
      String clientEmail,
      String providerName,
      String providerTaxId,
      String toolName,
      String category,
      LocalDate start,
      LocalDate end,
      long days,
      int quantity,
      BigDecimal dailyRate,
      BigDecimal total,
      ReservationStatus status,
      HistoryEntry lastChange,
      String note) {}

  record EventRow(
      Instant at,
      Long reservationId,
      String toolName,
      ReservationStatus action,
      ActorRole actorRole,
      String actorName,
      String actorEmail,
      String detail) {}
}
