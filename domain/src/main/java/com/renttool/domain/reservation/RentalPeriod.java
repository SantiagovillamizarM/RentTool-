package com.renttool.domain.reservation;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Rango de alquiler por días con la forma [inicio, fin): el día fin es la devolución y no se cobra.
 * Un alquiler del 10 al 12 dura dos días y otra persona puede recoger la herramienta el 12.
 */
public record RentalPeriod(LocalDate start, LocalDate end) {
  public static final int MAX_DAYS = 90;

  public RentalPeriod {
    if (start == null || end == null)
      throw new InvalidInputException("Las fechas de inicio y devolución son obligatorias");
    if (!end.isAfter(start))
      throw new InvalidInputException("La devolución debe ser posterior al inicio");
    if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS)
      throw new InvalidInputException("Una reserva no puede superar " + MAX_DAYS + " días");
  }

  public long days() {
    return ChronoUnit.DAYS.between(start, end);
  }

  public boolean overlaps(RentalPeriod other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }

  public boolean startsBefore(LocalDate day) {
    return start.isBefore(day);
  }
}
