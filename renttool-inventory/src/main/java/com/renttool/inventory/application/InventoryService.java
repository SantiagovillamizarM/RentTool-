package com.renttool.inventory.application;

import com.renttool.inventory.domain.Tool;
import com.renttool.shared.BusinessException;

public class InventoryService {
  private final ToolRepository repository;

  public InventoryService(ToolRepository r) {
    repository = r;
  }

  public Tool get(Long id) {
    return repository
        .find(id)
        .orElseThrow(() -> new BusinessException(404, "Herramienta no encontrada"));
  }

  public Tool owned(Long id, Long actor, boolean admin) {
    var t = get(id);
    if (!admin && !t.providerId().equals(actor))
      throw new BusinessException(403, "Esta herramienta pertenece a otro proveedor");
    return t;
  }

  public Tool save(Tool t, Long actor, boolean admin) {
    if (t.id() != null) {
      var old = owned(t.id(), actor, admin);
      t =
          new Tool(
              t.id(),
              old.providerId(),
              t.categoryId(),
              t.name(),
              t.description(),
              t.dailyRate(),
              old.active());
    }
    return repository.save(t);
  }

  public void retire(Long id, Long actor, boolean admin) {
    var t = owned(id, actor, admin);
    repository.save(
        new Tool(
            t.id(),
            t.providerId(),
            t.categoryId(),
            t.name(),
            t.description(),
            t.dailyRate(),
            false));
  }
}
