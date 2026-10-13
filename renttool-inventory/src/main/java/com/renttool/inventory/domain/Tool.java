package com.renttool.inventory.domain;

import java.math.BigDecimal;

public record Tool(
    Long id,
    Long providerId,
    Long categoryId,
    String name,
    String description,
    BigDecimal dailyRate,
    boolean active) {
  public Tool {
    if (dailyRate == null || dailyRate.signum() <= 0)
      throw new IllegalArgumentException("La tarifa debe ser positiva");
  }
}
