package com.renttool.application.inventory.port.in;

import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.Unit;
import com.renttool.domain.inventory.UnitStatus;
import java.util.List;

public interface ManageUnitsUseCase {
  List<Unit> list(Actor actor, Long toolId);

  Unit add(Actor actor, Long toolId, Command command);

  Unit change(Actor actor, Long toolId, Long unitId, Command command);

  void retire(Actor actor, Long toolId, Long unitId);

  record Command(String code, UnitStatus status) {}
}
