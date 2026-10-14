package com.renttool.application.inventory.port.in;

import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.Tool;
import java.math.BigDecimal;

public interface ManageToolsUseCase {
  Tool publish(Actor actor, Command command);

  Tool edit(Actor actor, Long toolId, Command command);

  void retire(Actor actor, Long toolId);

  record Command(Long categoryId, String name, String description, BigDecimal dailyRate) {}
}
