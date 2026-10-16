package com.renttool.infrastructure.reservation.persistence;

import com.renttool.application.reservation.port.out.ReservationEventRepository;
import com.renttool.domain.reservation.ReservationEvent;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Solo inserta y consulta: el historial nunca se actualiza ni se borra. */
@Repository
class ReservationEventPersistenceAdapter implements ReservationEventRepository {
  private final ReservationEventJpaRepository jpa;

  ReservationEventPersistenceAdapter(ReservationEventJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ReservationEvent append(ReservationEvent event) {
    if (event.id() != null) throw new IllegalStateException("Los eventos no se modifican");
    return toDomain(jpa.save(toEntity(event)));
  }

  @Override
  public List<ReservationEvent> findByReservation(Long reservationId) {
    return jpa.findByReservationIdOrderByOccurredAtAscIdAsc(reservationId).stream()
        .map(ReservationEventPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<ReservationEvent> findByReservations(Collection<Long> reservationIds) {
    if (reservationIds.isEmpty()) return List.of();
    return jpa.findByReservationIdInOrderByOccurredAtAscIdAsc(reservationIds).stream()
        .map(ReservationEventPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<ReservationEvent> findOccurredBetween(Instant from, Instant to) {
    return jpa
        .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
            utc(from), utc(to))
        .stream()
        .map(ReservationEventPersistenceAdapter::toDomain)
        .toList();
  }

  private static LocalDateTime utc(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static ReservationEvent toDomain(ReservationEventEntity e) {
    return new ReservationEvent(
        e.id,
        e.reservationId,
        ReservationStatus.valueOf(e.action),
        e.actorId,
        ReservationEvent.ActorRole.valueOf(e.actorRole),
        e.occurredAt.toInstant(ZoneOffset.UTC),
        e.detail);
  }

  private static ReservationEventEntity toEntity(ReservationEvent r) {
    var e = new ReservationEventEntity();
    e.reservationId = r.reservationId();
    e.action = r.action().name();
    e.actorId = r.actorId();
    e.actorRole = r.actorRole().name();
    e.occurredAt = utc(r.occurredAt());
    e.detail = r.detail();
    return e;
  }
}
