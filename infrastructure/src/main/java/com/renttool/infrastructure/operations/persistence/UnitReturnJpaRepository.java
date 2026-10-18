package com.renttool.infrastructure.operations.persistence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UnitReturnJpaRepository extends JpaRepository<UnitReturnEntity, UnitReturnId> {
  List<UnitReturnEntity> findByReservationIdOrderByUnitId(Long reservationId);
}
