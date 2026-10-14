package com.renttool.application.inventory.port.out;

import com.renttool.domain.inventory.Unit;
import java.util.List;
import java.util.Optional;

public interface UnitRepository {
  Optional<Unit> findById(Long id);

  List<Unit> findByToolId(Long toolId);

  Unit save(Unit unit);
}
