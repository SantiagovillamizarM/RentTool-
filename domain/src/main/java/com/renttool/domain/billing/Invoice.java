package com.renttool.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Factura de pruebas: copia histórica inmutable, sin validez fiscal. Si la reserva no sigue
 * adelante, la factura no se borra: queda anulada con la fecha y el motivo.
 *
 * @param paymentDetail método de pago enmascarado; null en facturas anteriores a la pasarela
 */
public record Invoice(String number, String paymentReference, Long reservationId, Instant issuedAt,
    String sellerName, String sellerTaxId, String sellerAddress, String buyerName, String buyerEmail,
    String toolName, LocalDate start, LocalDate end, int quantity, BigDecimal dailyRate,
    BigDecimal total, String paymentDetail, Instant voidedAt, String voidReason) {

  public Invoice voided(Instant at, String reason) {
    return new Invoice(number, paymentReference, reservationId, issuedAt, sellerName, sellerTaxId,
        sellerAddress, buyerName, buyerEmail, toolName, start, end, quantity, dailyRate, total,
        paymentDetail, at, reason);
  }
}
