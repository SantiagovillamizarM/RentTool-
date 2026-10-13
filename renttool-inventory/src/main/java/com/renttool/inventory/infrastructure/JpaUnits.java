package com.renttool.inventory.infrastructure;

public interface JpaUnits
    extends org.springframework.data.jpa.repository.JpaRepository<UnitEntity, Long> {
  java.util.List<UnitEntity> findByToolId(Long toolId);
}
