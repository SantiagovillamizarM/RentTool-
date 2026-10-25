package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Recargo por devolver después de la fecha pactada. Devolver el mismo día de devolución no tiene
 * recargo; cada día siguiente se cobra a la tarifa diaria pactada por cada unidad.
 */
public record LateFee(int days, BigDecimal amount) {
  public static final LateFee NONE = new LateFee(0, BigDecimal.ZERO);

  public LateFee {
    if (days < 0 || amount == null || amount.signum() < 0)
      throw new InvalidInputException("Recargo por retraso inválido");
    amount = amount.setScale(2, RoundingMode.HALF_UP);
  }

  public static LateFee of(
      LocalDate dueDate, LocalDate returnedOn, BigDecimal dailyRate, int quantity) {
    long days = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnedOn));
    if (days == 0) return NONE;
    return new LateFee(
        (int) days,
        dailyRate.multiply(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(quantity)));
  }
}
