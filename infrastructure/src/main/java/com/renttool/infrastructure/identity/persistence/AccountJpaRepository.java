package com.renttool.infrastructure.identity.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {
  Optional<AccountEntity> findByEmail(String email);

  Page<AccountEntity> findByRoleAndRemovedAtIsNull(String role, Pageable pageable);
}
