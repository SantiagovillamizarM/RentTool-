package com.renttool.domain.reservation;

import java.math.BigDecimal;

/** Lo que reservas necesita saber de una herramienta del inventario. */
public record RentableTool(
    Long id, Long providerId, String name, BigDecimal dailyRate, boolean active) {}
