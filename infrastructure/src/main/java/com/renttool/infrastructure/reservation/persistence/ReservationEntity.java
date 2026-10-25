package com.renttool.infrastructure.reservation.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Los instantes se guardan como fecha y hora UTC, sin depender de la zona del servidor. */
@Entity
@Table(name = "reservations")
class ReservationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "client_id")
  Long clientId;

  @Column(name = "tool_id")
  Long toolId;

  @Column(name = "start_date")
  LocalDate startDate;

  @Column(name = "end_date")
  LocalDate endDate;

  int quantity;

  @Column(name = "daily_rate", precision = 14, scale = 2)
  BigDecimal dailyRate;

  @Column(precision = 14, scale = 2)
  BigDecimal total;

  String status;

  @Column(name = "requested_at")
  LocalDateTime requestedAt;

  @Column(name = "respond_by")
  LocalDateTime respondBy;

  @Column(length = 500)
  String note;

  @Column(precision = 14, scale = 2)
  BigDecimal deposit;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "reservation_units", joinColumns = @JoinColumn(name = "reservation_id"))
  @Column(name = "unit_id")
  Set<Long> unitIds = new HashSet<>();
}
