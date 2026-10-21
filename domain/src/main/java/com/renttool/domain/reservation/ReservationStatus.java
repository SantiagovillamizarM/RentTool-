package com.renttool.domain.reservation;

import java.util.Set;

/**
 * Ciclo de vida de una reserva. Una solicitud aparta unidades en cuanto el cliente la paga, y las
 * conserva al aceptarse; una solicitud sin pagar no aparta nada.
 */
public enum ReservationStatus {
  SOLICITADA,
  ACEPTADA,
  RECHAZADA,
  CANCELADA,
  VENCIDA;

  /**
   * Estados cuyas unidades asignadas no pueden entregarse a otra reserva en las mismas fechas. Una
   * SOLICITADA solo tiene unidades asignadas si ya se pagó.
   */
  public static final Set<ReservationStatus> HOLDING_UNITS = Set.of(SOLICITADA, ACEPTADA);
}
