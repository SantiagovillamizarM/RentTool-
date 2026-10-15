package com.renttool.application.reservation.port.out;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.RentalPeriod;
import com.renttool.domain.reservation.Reservation;
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

  /** Ids de las solicitudes pendientes cuyo plazo de respuesta ya pasó. */
  List<Long> findPendingIdsDueBy(Instant now);
}
