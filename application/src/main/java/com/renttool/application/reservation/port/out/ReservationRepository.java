package com.renttool.application.reservation.port.out;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.RentalPeriod;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.*;

public interface ReservationRepository {
  Optional<Reservation> findById(Long id);

  /**
   * Carga la reserva bloqueándola hasta el fin de la transacción. Toda operación que cambia su
   * estado la lee así primero, para que dos cambios simultáneos no se pisen.
   */
  Optional<Reservation> findByIdForUpdate(Long id);

  Reservation save(Reservation reservation);

  /** Unidades de la herramienta ocupadas por reservas que se cruzan con el periodo. */
  Set<Long> unitsHeldDuring(Long toolId, RentalPeriod period);

  PageResult<Reservation> findByClient(Long clientId, PageQuery page);

  PageResult<Reservation> findByTools(Collection<Long> toolIds, PageQuery page);

  PageResult<Reservation> findAll(PageQuery page);

  /**
   * Reservas solicitadas en [from, to), de la más antigua a la más reciente. {@code status} null
   * incluye todas.
   */
  List<Reservation> findRequestedBetween(Instant from, Instant to, ReservationStatus status);

  /** Reservas SOLICITADA o ACEPTADA de esas herramientas, sin importar sus fechas. */
  List<Reservation> findOpenByTools(Collection<Long> toolIds);

  /** Ids de las solicitudes pendientes cuyo plazo de respuesta ya pasó. */
  List<Long> findPendingIdsDueBy(Instant now);

  /** Si el equipo de la reserva llegó a entregarse (esté o no devuelto). */
  boolean hasDelivery(Long reservationId);

  /** Ids de las reservas aceptadas cuya fecha de inicio ya llegó y que aún no tienen entrega. */
  List<Long> findAcceptedUndeliveredStartedBy(java.time.LocalDate today);
}
