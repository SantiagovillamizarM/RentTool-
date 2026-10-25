package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estado de cada ejemplar al cerrar una devolución. {@code charge} es lo que se cobra al cliente
 * por una unidad dañada o perdida; una unidad en buen estado no genera cobro.
 */
public record UnitReturn(
    Long reservationId, Long unitId, Condition condition, String notes, BigDecimal charge) {
  public enum Condition { BUENO, DANADO, PERDIDO }

  public static final BigDecimal MAX_CHARGE = new BigDecimal("1000000000");

  public UnitReturn {
    if (reservationId == null || unitId == null || condition == null)
      throw new InvalidInputException("Indica cada unidad y su estado de devolución");
    notes = notes == null ? null : notes.strip();
    if (notes != null && notes.length() > 500)
      throw new InvalidInputException("Las observaciones de la unidad admiten hasta 500 caracteres");
    if (condition != Condition.BUENO && (notes == null || notes.isBlank()))
      throw new InvalidInputException("Describe el daño o la pérdida de la unidad");
    charge = charge == null ? BigDecimal.ZERO : charge.setScale(2, RoundingMode.HALF_UP);
    if (charge.signum() < 0 || charge.compareTo(MAX_CHARGE) > 0)
      throw new InvalidInputException("El cobro por daño debe estar entre 0 y 1.000.000.000");
    if (condition == Condition.BUENO && charge.signum() > 0)
      throw new InvalidInputException("Solo se cobra una unidad dañada o perdida");
  }

  public UnitReturn(Long reservationId, Long unitId, Condition condition, String notes) {
    this(reservationId, unitId, condition, notes, BigDecimal.ZERO);
  }
}
