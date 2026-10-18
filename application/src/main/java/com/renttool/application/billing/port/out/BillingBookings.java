package com.renttool.application.billing.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Traducción de reservas e identidad al lenguaje necesario para facturar. */
public interface BillingBookings {
  Booking find(Long id);
  Booking lock(Long id);
  record Booking(Long id, Long clientId, Long providerId, String status,
      LocalDate start, LocalDate end, int quantity, BigDecimal dailyRate, BigDecimal total,
      String toolName, String buyerName, String buyerEmail, String sellerName,
      String sellerTaxId, String sellerAddress) {}
}
