package com.renttool.application.insights.service;

import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.reservation.ReservationStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Definición única de venta, compartida por el panel del proveedor y la comparación del
 * administrador: una reserva aceptada con pago aprobado. Un pago reembolsado deja de estar aprobado,
 * así que no cuenta; una reserva aceptada sin pago (datos anteriores al pago al solicitar) tampoco.
 */
final class Sales {
  private Sales() {}

  /** Importe vendido de la reserva; cero si no es una venta. */
  static BigDecimal of(Reservation reservation, List<Payment> history) {
    return reservation.status() == ReservationStatus.ACEPTADA ? approved(history) : BigDecimal.ZERO;
  }

  /** Dinero de una solicitud ya pagada que el proveedor todavía no acepta; cero en otro caso. */
  static BigDecimal awaitingAcceptance(Reservation reservation, List<Payment> history) {
    return reservation.status() == ReservationStatus.SOLICITADA ? approved(history) : BigDecimal.ZERO;
  }

  private static BigDecimal approved(List<Payment> history) {
    return history.stream()
        .filter(payment -> payment.status() == Payment.Status.APPROVED)
        .map(Payment::amount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }
}
