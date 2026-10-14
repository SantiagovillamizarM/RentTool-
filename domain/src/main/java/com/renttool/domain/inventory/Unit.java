package com.renttool.domain.inventory;

import com.renttool.domain.shared.exception.InvalidInputException;

/** Ejemplar físico de una herramienta, identificado por un código único dentro de ella. */
public record Unit(Long id, Long toolId, String code, UnitStatus status) {
  public Unit {
    if (toolId == null) throw new InvalidInputException("La unidad requiere una herramienta");
    if (code == null || code.isBlank()) throw new InvalidInputException("El código es obligatorio");
    if (status == null) throw new InvalidInputException("El estado es obligatorio");
  }

  public static Unit register(Long toolId, String code, UnitStatus status) {
    return new Unit(null, toolId, code, status);
  }

  public Unit change(String newCode, UnitStatus newStatus) {
    return new Unit(id, toolId, newCode, newStatus);
  }

  public Unit retire() {
    return new Unit(id, toolId, code, UnitStatus.RETIRADA);
  }

  public boolean belongsTo(Long tool) {
    return toolId.equals(tool);
  }

  public boolean isOperational() {
    return status == UnitStatus.OPERATIVA;
  }
}
