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
  @Query(
      """
      select u from ReservationEntity r join r.unitIds u
      where r.toolId = :toolId and r.status in :statuses
        and r.startDate < :end and r.endDate > :start
      """)
  List<Long> findHeldUnits(
      @Param("toolId") Long toolId,
      @Param("statuses") Collection<String> statuses,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  Page<ReservationEntity> findByClientId(Long clientId, Pageable pageable);

  Page<ReservationEntity> findByToolIdIn(Collection<Long> toolIds, Pageable pageable);

  @Query("select r.id from ReservationEntity r where r.status = :status and r.respondBy <= :limit")
  List<Long> findIdsByStatusDueBy(
      @Param("status") String status, @Param("limit") LocalDateTime limit);
}
