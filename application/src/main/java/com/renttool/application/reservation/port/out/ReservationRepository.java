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

  /** Como {@link #unitsHeldDuring}, pero sin contar la reserva indicada. */
  Set<Long> unitsHeldByOthers(Long toolId, RentalPeriod period, Long exceptReservationId);

  /** En estas tres consultas {@code status} null incluye todos los estados. */
  PageResult<Reservation> findByClient(Long clientId, ReservationStatus status, PageQuery page);

  PageResult<Reservation> findByTools(
      Collection<Long> toolIds, ReservationStatus status, PageQuery page);

  PageResult<Reservation> findAll(ReservationStatus status, PageQuery page);

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

  /** Si ya se registró la devolución del equipo de la reserva. */
  boolean hasReturn(Long reservationId);

  /** Ids de las reservas aceptadas cuya fecha de inicio ya llegó y que aún no tienen entrega. */
  List<Long> findAcceptedUndeliveredStartedBy(java.time.LocalDate today);
}
