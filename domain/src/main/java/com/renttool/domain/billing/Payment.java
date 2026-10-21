package com.renttool.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de un pago de demostración. Solo guarda el método y un detalle enmascarado (por ejemplo
 * «Visa •••• 4242»); nunca el número completo de la tarjeta ni el CVV.
 *
 * @param message motivo del rechazo o del reembolso; null si no aplica
 */
public record Payment(
    String reference,
    Long reservationId,
    BigDecimal amount,
    Status status,
    String transactionId,
    Instant createdAt,
    Instant updatedAt,
    String method,
    String detail,
    String message) {

  // Se mantienen los estados anteriores para poder leer bases de datos ya creadas.
  // La simulación registra APPROVED o DECLINED, y VOIDED cuando se reembolsa un pago aprobado.
  public enum Status { PENDING, APPROVED, DECLINED, ERROR, VOIDED, REVIEW }

  public Payment {
    if (reference == null || reservationId == null || amount == null || amount.signum() <= 0)
      throw new IllegalArgumentException("Datos de pago inválidos");
    if (status == null) throw new IllegalArgumentException("El estado del pago es obligatorio");
  }

  /** Reembolso simulado: la reserva no siguió adelante y el dinero vuelve al cliente. */
  public Payment refund(Instant at, String reason) {
    if (status != Status.APPROVED) throw new IllegalStateException("Solo se reembolsa un pago aprobado");
    return new Payment(reference, reservationId, amount, Status.VOIDED, transactionId, createdAt, at,
        method, detail, reason);
  }
}
