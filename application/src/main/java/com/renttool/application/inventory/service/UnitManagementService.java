package com.renttool.application.inventory.service;

import com.renttool.application.inventory.port.in.ManageUnitsUseCase;
import com.renttool.application.inventory.port.out.*;
import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.NotFoundException;
import java.util.List;

public class UnitManagementService implements ManageUnitsUseCase {
  private final UnitRepository units;
  private final ToolAccess access;

  public UnitManagementService(ToolRepository tools, UnitRepository units) {
    this.units = units;
    this.access = new ToolAccess(tools);
  }

  @Override
  public List<Unit> list(Actor actor, Long toolId) {
    access.manageable(toolId, actor);
    return units.findByToolId(toolId);
  }

  @Override
  public Unit add(Actor actor, Long toolId, Command c) {
    access.manageable(toolId, actor);
    return units.save(Unit.register(toolId, c.code(), c.status()));
  }

  @Override
  public Unit change(Actor actor, Long toolId, Long unitId, Command c) {
    access.manageable(toolId, actor);
    return units.save(unitOf(toolId, unitId).change(c.code(), c.status()));
  }

  @Override
  public void retire(Actor actor, Long toolId, Long unitId) {
    access.manageable(toolId, actor);
    units.save(unitOf(toolId, unitId).retire());
  }

  private Unit unitOf(Long toolId, Long unitId) {
    return units
        .findById(unitId)
        .filter(u -> u.belongsTo(toolId))
        .orElseThrow(() -> new NotFoundException("Unidad no encontrada"));
  }
}
