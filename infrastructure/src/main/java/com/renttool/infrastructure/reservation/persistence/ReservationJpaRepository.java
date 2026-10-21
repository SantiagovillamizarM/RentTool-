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

  /**
   * Rangos [inicio, fin) que se cruzan: inicio < fin del otro y fin > inicio del otro. Una unidad
   * entregada y vencida (ya pasó su devolución sin registrarla) bloquea además todo lo que sigue.
   */
  @Query(value =
      """
      select ru.unit_id from reservation_units ru
      join reservations r on r.id = ru.reservation_id
      left join rental_operations o on o.reservation_id = r.id
      where r.tool_id = :toolId and r.status in (:statuses)
        and o.returned_at is null
        and ((r.start_date < :end and r.end_date > :start)
          or (o.delivered_at is not null and r.end_date <= :today and r.start_date < :end))
      """, nativeQuery = true)
  List<Long> findHeldUnits(
      @Param("toolId") Long toolId,
      @Param("statuses") Collection<String> statuses,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("today") LocalDate today);

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

  @Query(value = "select count(*) from rental_operations where reservation_id = :id", nativeQuery = true)
  long countDeliveries(@Param("id") Long reservationId);

  @Query(value = """
      select r.id from reservations r
      left join rental_operations o on o.reservation_id = r.id
      where r.status = 'ACEPTADA' and r.start_date <= :today and o.reservation_id is null
      order by r.start_date, r.id
      """, nativeQuery = true)
  List<Long> findAcceptedUndeliveredStartedBy(@Param("today") LocalDate today);

  @Query("select r.id from ReservationEntity r where r.status = :status and r.respondBy <= :limit")
  List<Long> findIdsByStatusDueBy(
      @Param("status") String status, @Param("limit") LocalDateTime limit);
}
