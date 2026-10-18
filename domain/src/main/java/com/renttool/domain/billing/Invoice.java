package com.renttool.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Factura de pruebas: copia histórica inmutable, sin validez fiscal. */
public record Invoice(String number, String paymentReference, Long reservationId, Instant issuedAt,
    String sellerName, String sellerTaxId, String sellerAddress, String buyerName, String buyerEmail,
    String toolName, LocalDate start, LocalDate end, int quantity, BigDecimal dailyRate,
    BigDecimal total) {}
