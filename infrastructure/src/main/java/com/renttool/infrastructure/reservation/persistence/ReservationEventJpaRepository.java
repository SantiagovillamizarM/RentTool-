package com.renttool.infrastructure.reservation.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReservationEventJpaRepository extends JpaRepository<ReservationEventEntity, Long> {
  List<ReservationEventEntity> findByReservationIdOrderByOccurredAtAscIdAsc(Long reservationId);

  List<ReservationEventEntity> findByReservationIdInOrderByOccurredAtAscIdAsc(
      Collection<Long> reservationIds);

  List<ReservationEventEntity>
      findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
          LocalDateTime from, LocalDateTime to);
}
