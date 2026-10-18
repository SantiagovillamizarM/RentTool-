package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.InvalidInputException;

/** Estado de cada ejemplar al cerrar una devolución. */
public record UnitReturn(Long reservationId, Long unitId, Condition condition, String notes) {
  public enum Condition { BUENO, DANADO, PERDIDO }

  public UnitReturn {
    if (reservationId == null || unitId == null || condition == null)
      throw new InvalidInputException("Indica cada unidad y su estado de devolución");
    notes = notes == null ? null : notes.strip();
    if (notes != null && notes.length() > 500)
      throw new InvalidInputException("Las observaciones de la unidad admiten hasta 500 caracteres");
    if (condition != Condition.BUENO && (notes == null || notes.isBlank()))
      throw new InvalidInputException("Describe el daño o la pérdida de la unidad");
  }
}
