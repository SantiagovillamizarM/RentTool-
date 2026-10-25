package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;

/**
 * Agregado Herramienta: una publicación de un proveedor con su tarifa diaria. {@code deposit} es
 * la garantía por unidad que el cliente entrega al recibir el equipo; cero si no se pide.
 */
public record Tool(
    Long id,
    Long providerId,
    Long categoryId,
    String name,
    String description,
    BigDecimal dailyRate,
    boolean active,
    BigDecimal deposit) {
  public Tool {
    if (providerId == null) throw new InvalidInputException("La herramienta requiere proveedor");
    if (categoryId == null) throw new InvalidInputException("Categoría inválida");
    if (name == null || name.isBlank()) throw new InvalidInputException("El nombre es obligatorio");
    if (description == null || description.isBlank())
      throw new InvalidInputException("La descripción es obligatoria");
    if (dailyRate == null || dailyRate.signum() <= 0)
      throw new InvalidInputException("La tarifa debe ser positiva");
    if (deposit == null) deposit = BigDecimal.ZERO;
    if (deposit.signum() < 0)
      throw new InvalidInputException("El depósito de garantía no puede ser negativo");
  }

  public Tool(Long id, Long providerId, Long categoryId, String name, String description,
      BigDecimal dailyRate, boolean active) {
    this(id, providerId, categoryId, name, description, dailyRate, active, BigDecimal.ZERO);
  }

  public static Tool publish(
      Actor actor, Long categoryId, String name, String description, BigDecimal dailyRate) {
    return publish(actor, categoryId, name, description, dailyRate, BigDecimal.ZERO);
  }

  public static Tool publish(Actor actor, Long categoryId, String name, String description,
      BigDecimal dailyRate, BigDecimal deposit) {
    actor.ensureCanManageInventory();
    return new Tool(null, actor.id(), categoryId, name, description, dailyRate, true, deposit);
  }

  /** Cambia los datos editables; el proveedor y el estado no se transfieren al editar. */
  public Tool edit(Long categoryId, String name, String description, BigDecimal dailyRate) {
    return edit(categoryId, name, description, dailyRate, deposit);
  }

  public Tool edit(Long categoryId, String name, String description, BigDecimal dailyRate,
      BigDecimal deposit) {
    return new Tool(id, providerId, categoryId, name, description, dailyRate, active, deposit);
  }

  public Tool retire() {
    return new Tool(id, providerId, categoryId, name, description, dailyRate, false, deposit);
  }

  public void ensureManageableBy(Actor actor) {
    actor.ensureCanManageInventory();
    if (!actor.administrator() && !providerId.equals(actor.id()))
      throw new ForbiddenOperationException("Esta herramienta pertenece a otro proveedor");
  }
}
