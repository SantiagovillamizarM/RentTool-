package com.renttool.infrastructure.identity.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ProviderApplicationJpaRepository extends JpaRepository<ProviderApplicationEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ProviderApplicationEntity a where a.id = :id")
  Optional<ProviderApplicationEntity> findByIdForUpdate(@Param("id") Long id);

  Optional<ProviderApplicationEntity> findFirstByAccountIdOrderBySubmittedAtDescIdDesc(
      Long accountId);

  boolean existsByNitAndStatus(String nit, String status);

  Page<ProviderApplicationEntity> findByStatus(String status, Pageable pageable);
}
