package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;

/** Agregado Herramienta: una publicación de un proveedor con su tarifa diaria. */
public record Tool(
    Long id,
    Long providerId,
    Long categoryId,
    String name,
    String description,
    BigDecimal dailyRate,
    boolean active) {
  public Tool {
    if (providerId == null) throw new InvalidInputException("La herramienta requiere proveedor");
    if (categoryId == null) throw new InvalidInputException("Categoría inválida");
    if (name == null || name.isBlank()) throw new InvalidInputException("El nombre es obligatorio");
    if (description == null || description.isBlank())
      throw new InvalidInputException("La descripción es obligatoria");
    if (dailyRate == null || dailyRate.signum() <= 0)
      throw new InvalidInputException("La tarifa debe ser positiva");
  }

  public static Tool publish(
      Actor actor, Long categoryId, String name, String description, BigDecimal dailyRate) {
    actor.ensureCanManageInventory();
    return new Tool(null, actor.id(), categoryId, name, description, dailyRate, true);
  }

  /** Cambia los datos editables; el proveedor y el estado no se transfieren al editar. */
  public Tool edit(Long categoryId, String name, String description, BigDecimal dailyRate) {
    return new Tool(id, providerId, categoryId, name, description, dailyRate, active);
  }

  public Tool retire() {
    return new Tool(id, providerId, categoryId, name, description, dailyRate, false);
  }

  public void ensureManageableBy(Actor actor) {
    actor.ensureCanManageInventory();
    if (!actor.administrator() && !providerId.equals(actor.id()))
      throw new ForbiddenOperationException("Esta herramienta pertenece a otro proveedor");
  }
}
