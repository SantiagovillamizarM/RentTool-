package com.renttool.infrastructure.inventory.persistence;

import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.domain.inventory.Unit;
import com.renttool.domain.inventory.UnitStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UnitPersistenceAdapter implements UnitRepository {
  private final UnitJpaRepository jpa;

  UnitPersistenceAdapter(UnitJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Unit> findById(Long id) {
    return jpa.findById(id).map(UnitPersistenceAdapter::toDomain);
  }

  @Override
  public List<Unit> findByToolId(Long toolId) {
    return jpa.findByToolId(toolId).stream().map(UnitPersistenceAdapter::toDomain).toList();
  }

  @Override
  public Unit save(Unit unit) {
    var e = new UnitEntity();
    e.id = unit.id();
    e.toolId = unit.toolId();
    e.code = unit.code();
    e.status = unit.status().name();
    return toDomain(jpa.save(e));
  }

  private static Unit toDomain(UnitEntity e) {
    return new Unit(e.id, e.toolId, e.code, UnitStatus.valueOf(e.status));
  }
}
