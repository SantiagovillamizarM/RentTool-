package com.renttool.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro de un pago de demostración. No contiene información bancaria. */
public record Payment(
    String reference,
    Long reservationId,
    BigDecimal amount,
    Status status,
    String transactionId,
    Instant createdAt,
    Instant updatedAt) {

  // Se mantienen los estados anteriores para poder leer bases de datos ya creadas.
  // El formulario escolar solo registra APPROVED o DECLINED.
  public enum Status { PENDING, APPROVED, DECLINED, ERROR, VOIDED, REVIEW }

  public Payment {
    if (reference == null || reservationId == null || amount == null || amount.signum() <= 0)
      throw new IllegalArgumentException("Datos de pago inválidos");
    if (status == null) throw new IllegalArgumentException("El estado del pago es obligatorio");
  }
}
