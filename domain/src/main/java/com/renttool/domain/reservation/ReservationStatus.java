package com.renttool.domain.reservation;

import java.util.Set;

/**
 * Ciclo de vida de una reserva. Solo las aceptadas ocupan unidades; una solicitud todavía no
 * bloquea inventario y puede chocar con otra al momento de aceptarla.
 */
public enum ReservationStatus {
  SOLICITADA,
  ACEPTADA,
  RECHAZADA,
  CANCELADA,
  VENCIDA;

  /** Estados cuyas unidades asignadas no pueden entregarse a otra reserva en las mismas fechas. */
  public static final Set<ReservationStatus> HOLDING_UNITS = Set.of(ACEPTADA);
}
