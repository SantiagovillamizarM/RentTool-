package com.renttool.application.reservation.port.in;

import com.renttool.domain.reservation.RentalPeriod;
import java.math.BigDecimal;

public interface CheckAvailabilityUseCase {
  /** Consulta informativa y pública: no bloquea unidades. La aceptación vuelve a comprobar. */
  Availability check(Long toolId, RentalPeriod period, int quantity);

  record Availability(
      Long toolId,
      RentalPeriod period,
      int quantity,
      long availableUnits,
      BigDecimal dailyRate,
      BigDecimal total) {
    public boolean enough() {
      return availableUnits >= quantity;
    }
  }
}
