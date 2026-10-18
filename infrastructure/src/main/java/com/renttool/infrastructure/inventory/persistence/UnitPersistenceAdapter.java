package com.renttool.infrastructure.inventory.persistence;

import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.domain.inventory.Unit;
import com.renttool.domain.inventory.UnitStatus;
import com.renttool.domain.shared.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class UnitPersistenceAdapter implements UnitRepository {
  private final UnitJpaRepository jpa;
  private final ToolRepository tools;
  private final RentalRecords records;

  UnitPersistenceAdapter(UnitJpaRepository jpa, ToolRepository tools, RentalRecords records) {
    this.jpa = jpa;
    this.tools = tools;
    this.records = records;
  }

  public Optional<Unit> findById(Long id) {
    return jpa.findById(id).map(UnitPersistenceAdapter::toDomain);
  }
  public List<Unit> findByToolId(Long toolId) {
    return jpa.findByToolId(toolId).stream().map(UnitPersistenceAdapter::toDomain).toList();
  }

  @Transactional
  public Unit save(Unit unit) {
    // El mismo bloqueo coordina aceptación, entrega, devolución y cambios manuales.
    tools.findByIdForUpdate(unit.toolId()).orElseThrow();
    if (unit.id() != null && unit.status() == UnitStatus.OPERATIVA && records.hasBlockingIncident(unit.id()))
      throw new ConflictException("Resuelve primero las incidencias de daño o pérdida de esta unidad");
    var entity = new UnitEntity();
    entity.id = unit.id(); entity.toolId = unit.toolId();
    entity.code = unit.code(); entity.status = unit.status().name();
    return toDomain(jpa.saveAndFlush(entity));
  }
  private static Unit toDomain(UnitEntity entity) {
    return new Unit(entity.id, entity.toolId, entity.code, UnitStatus.valueOf(entity.status));
  }
}
