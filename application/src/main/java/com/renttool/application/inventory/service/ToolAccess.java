package com.renttool.application.inventory.service;

import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.domain.inventory.Actor;
import com.renttool.domain.inventory.Tool;
import com.renttool.domain.shared.exception.NotFoundException;

/** Carga herramientas aplicando la regla de propiedad compartida por los casos de uso. */
class ToolAccess {
  private final ToolRepository tools;

  ToolAccess(ToolRepository tools) {
    this.tools = tools;
  }

  Tool existing(Long toolId) {
    return tools
        .findById(toolId)
        .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
  }

  Tool manageable(Long toolId, Actor actor) {
    actor.ensureCanManageInventory();
    var tool = existing(toolId);
    tool.ensureManageableBy(actor);
    return tool;
  }
}
