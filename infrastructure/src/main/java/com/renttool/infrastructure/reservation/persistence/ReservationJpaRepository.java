package com.renttool.infrastructure.reservation.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ReservationEntity r where r.id = :id")
  Optional<ReservationEntity> findByIdForUpdate(@Param("id") Long id);

  /** Rangos [inicio, fin) que se cruzan: inicio < fin del otro y fin > inicio del otro. */
  @Query(value =
      """
      select ru.unit_id from reservation_units ru
      join reservations r on r.id = ru.reservation_id
      left join rental_operations o on o.reservation_id = r.id
      where r.tool_id = :toolId and r.status in (:statuses)
        and o.returned_at is null
        and ((r.start_date < :end and r.end_date > :start)
          or (o.delivered_at is not null and r.start_date < :end))
      """, nativeQuery = true)
  List<Long> findHeldUnits(
      @Param("toolId") Long toolId,
      @Param("statuses") Collection<String> statuses,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  List<ReservationEntity> findByRequestedAtGreaterThanEqualAndRequestedAtLessThanOrderByRequestedAtAscIdAsc(
      LocalDateTime from, LocalDateTime to);

  List<ReservationEntity>
      findByRequestedAtGreaterThanEqualAndRequestedAtLessThanAndStatusOrderByRequestedAtAscIdAsc(
          LocalDateTime from, LocalDateTime to, String status);

  @Query(value = """
      select r.* from reservations r
      left join rental_operations o on o.reservation_id = r.id
      where r.tool_id in (:toolIds) and r.status in (:statuses) and o.returned_at is null
      """, nativeQuery = true)
  List<ReservationEntity> findByToolIdInAndStatusIn(
      @Param("toolIds") Collection<Long> toolIds, @Param("statuses") Collection<String> statuses);

  Page<ReservationEntity> findByClientId(Long clientId, Pageable pageable);

  Page<ReservationEntity> findByToolIdIn(Collection<Long> toolIds, Pageable pageable);

  @Query("select r.id from ReservationEntity r where r.status = :status and r.respondBy <= :limit")
  List<Long> findIdsByStatusDueBy(
      @Param("status") String status, @Param("limit") LocalDateTime limit);
}
