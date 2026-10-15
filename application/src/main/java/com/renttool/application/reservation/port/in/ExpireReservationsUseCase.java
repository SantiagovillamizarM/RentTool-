package com.renttool.application.reservation.port.in;

public interface ExpireReservationsUseCase {
  /** Marca como vencidas las solicitudes sin respuesta a tiempo y devuelve cuántas cambió. */
  int expireOverdue();
}
