package com.renttool.infrastructure.inventory.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ToolJpaRepository
    extends JpaRepository<ToolEntity, Long>, JpaSpecificationExecutor<ToolEntity> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from ToolEntity t where t.id = :id")
  Optional<ToolEntity> findByIdForUpdate(@Param("id") Long id);

  @Query("select t.id from ToolEntity t where t.providerId = :providerId")
  List<Long> findIdsByProviderId(@Param("providerId") Long providerId);
}
