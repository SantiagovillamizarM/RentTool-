package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.ForbiddenOperationException;

/**
 * Quién ejecuta una operación de inventario. Inventario no depende del módulo de identidad: el
 * adaptador de entrada traduce la credencial a este valor.
 */
public record Actor(Long id, boolean provider, boolean administrator) {
  public static Actor anonymous() {
    return new Actor(null, false, false);
  }

  public boolean canManageInventory() {
    return id != null && (provider || administrator);
  }

  public void ensureCanManageInventory() {
    if (!canManageInventory()) throw new ForbiddenOperationException("Se requiere rol proveedor");
  }
}
