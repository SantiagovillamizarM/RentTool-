package com.renttool.infrastructure.inventory.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ToolImageJpaRepository extends JpaRepository<ToolImageEntity, Long> {
  List<ToolImageEntity> findByToolIdOrderByPositionAscIdAsc(Long toolId);
}
