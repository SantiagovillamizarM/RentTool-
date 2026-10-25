package com.renttool.domain.reservation;

import java.math.BigDecimal;

/**
 * Lo que reservas necesita saber de una herramienta del inventario. {@code deposit} es la garantía
 * por unidad.
 */
public record RentableTool(
    Long id, Long providerId, String name, BigDecimal dailyRate, boolean active,
    BigDecimal deposit) {
  public RentableTool {
    if (deposit == null) deposit = BigDecimal.ZERO;
  }

  public RentableTool(Long id, Long providerId, String name, BigDecimal dailyRate, boolean active) {
    this(id, providerId, name, dailyRate, active, BigDecimal.ZERO);
  }
}
