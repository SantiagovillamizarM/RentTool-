package com.renttool.application.inventory.service;

import com.renttool.application.inventory.port.in.ManageToolsUseCase;
import com.renttool.application.inventory.port.out.*;
import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.InvalidInputException;

public class ToolManagementService implements ManageToolsUseCase {
  private final ToolRepository tools;
  private final CategoryRepository categories;
  private final ToolAccess access;

  public ToolManagementService(ToolRepository tools, CategoryRepository categories) {
    this.tools = tools;
    this.categories = categories;
    this.access = new ToolAccess(tools);
  }

  @Override
  public Tool publish(Actor actor, Command c) {
    actor.ensureCanManageInventory();
    ensureCategory(c.categoryId());
    return tools.save(
        Tool.publish(
            actor, c.categoryId(), c.name(), c.description(), c.dailyRate(), c.deposit()));
  }

  @Override
  public Tool edit(Actor actor, Long toolId, Command c) {
    actor.ensureCanManageInventory();
    ensureCategory(c.categoryId());
    var tool = access.manageable(toolId, actor);
    // Sin depósito en la petición se conserva el que ya tenía.
    var deposit = c.deposit() == null ? tool.deposit() : c.deposit();
    return tools.save(
        tool.edit(c.categoryId(), c.name(), c.description(), c.dailyRate(), deposit));
  }

  @Override
  public void retire(Actor actor, Long toolId) {
    tools.save(access.manageable(toolId, actor).retire());
  }

  private void ensureCategory(Long categoryId) {
    if (categoryId == null || !categories.existsById(categoryId))
      throw new InvalidInputException("Categoría inválida");
  }
}
