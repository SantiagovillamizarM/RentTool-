package com.renttool.application.reservation.port.out;

import com.renttool.domain.reservation.ReservationEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Registro de auditoría de reservas: solo se agregan eventos, en orden cronológico. */
public interface ReservationEventRepository {
  ReservationEvent append(ReservationEvent event);

  List<ReservationEvent> findByReservation(Long reservationId);

  List<ReservationEvent> findByReservations(Collection<Long> reservationIds);

  /** Eventos ocurridos en [from, to), del más antiguo al más reciente. */
  List<ReservationEvent> findOccurredBetween(Instant from, Instant to);
}
