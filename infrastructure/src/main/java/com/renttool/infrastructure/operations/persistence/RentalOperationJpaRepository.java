package com.renttool.infrastructure.operations.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface RentalOperationJpaRepository extends JpaRepository<RentalOperationEntity, Long> {
  @Query(value = """
      select distinct ru.unit_id from rental_operations o
      join reservations r on r.id = o.reservation_id
      join reservation_units ru on ru.reservation_id = r.id
      where r.tool_id = :toolId and o.returned_at is null
      """, nativeQuery = true)
  List<Long> outUnitIds(@Param("toolId") Long toolId);
}
