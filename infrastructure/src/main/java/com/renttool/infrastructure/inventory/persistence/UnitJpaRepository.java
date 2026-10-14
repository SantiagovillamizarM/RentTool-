package com.renttool.infrastructure.inventory.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UnitJpaRepository extends JpaRepository<UnitEntity, Long> {
  List<UnitEntity> findByToolId(Long toolId);
}
