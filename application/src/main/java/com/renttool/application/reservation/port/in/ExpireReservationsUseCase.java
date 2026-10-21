package com.renttool.application.reservation.port.in;

public interface ExpireReservationsUseCase {
  /** Marca como vencidas las solicitudes sin respuesta a tiempo y devuelve cuántas cambió. */
  int expireOverdue();

  /** Vence ya mismo esta solicitud si su plazo pasó (y reembolsa su pago); dice si la venció. */
  boolean expireIfDue(Long reservationId);

  /**
   * Cancela y reembolsa las reservas aceptadas que terminaron su periodo sin entrega registrada.
   * Devuelve cuántas canceló.
   */
  int cancelUndeliveredEnded();
}
