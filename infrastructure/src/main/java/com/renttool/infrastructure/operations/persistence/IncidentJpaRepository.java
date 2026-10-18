package com.renttool.infrastructure.operations.persistence;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {
  List<IncidentEntity> findByReservationIdOrderByReportedAtAscIdAsc(Long reservationId);
  boolean existsByUnitIdAndResolvedAtIsNullAndTypeIn(Long unitId, Collection<String> types);
  Page<IncidentEntity> findByResolvedAtIsNull(Pageable page);
}
