package com.renttool.infrastructure.identity.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProviderProfileJpaRepository extends JpaRepository<ProviderProfileEntity, Long> {
  Optional<ProviderProfileEntity> findByNit(String nit);
}
