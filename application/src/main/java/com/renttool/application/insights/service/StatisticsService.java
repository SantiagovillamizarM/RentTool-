package com.renttool.application.insights.service;

import com.renttool.application.insights.port.in.ViewStatisticsUseCase;
import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class StatisticsService implements ViewStatisticsUseCase {
  private final ReservationRepository reservations;
  private final ToolRepository tools;
  private final PaymentStore payments;
  private final RentalRecords operations;
  private final Clock clock;
  public StatisticsService(ReservationRepository reservations, ToolRepository tools,
      PaymentStore payments, RentalRecords operations, Clock clock) {
    this.reservations = reservations; this.tools = tools; this.payments = payments;
    this.operations = operations; this.clock = clock;
  }
  public Summary get(Participant actor, Range range) {
    if (!actor.isAdministrator() && !actor.isProvider())
      throw new ForbiddenOperationException("Solo el administrador y los proveedores consultan estadísticas");
    Set<Long> own = actor.isProvider() ? new HashSet<>(tools.findIdsByProviderId(actor.id())) : Set.of();
    var found = reservations.findRequestedBetween(
        range.from().atStartOfDay(clock.getZone()).toInstant(),
        range.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant(), null).stream()
        .filter(r -> actor.isAdministrator() || own.contains(r.toolId())).toList();
    var statuses = new EnumMap<ReservationStatus, Long>(ReservationStatus.class);
    for (var status : ReservationStatus.values()) statuses.put(status, 0L);
    BigDecimal amount = BigDecimal.ZERO;
    long paid = 0, inUse = 0, returned = 0, overdue = 0, incidents = 0;
    for (var reservation : found) {
      statuses.merge(reservation.status(), 1L, Long::sum);
      var approved = payments.history(reservation.id()).stream()
          .filter(p -> p.status() == Payment.Status.APPROVED).findFirst();
      if (approved.isPresent()) { paid++; amount = amount.add(approved.get().amount()); }
      var operation = operations.operation(reservation.id());
      if (operation.isPresent()) {
        if (operation.get().isOut()) {
          inUse++;
          if (!reservation.period().end().isAfter(LocalDate.now(clock))) overdue++;
        } else returned++;
      }
      incidents += operations.incidents(reservation.id()).stream().filter(i -> i.isOpen()).count();
    }
    var grouped = found.stream().collect(Collectors.groupingBy(Reservation::toolId));
    var popular = grouped.entrySet().stream().map(e -> new PopularTool(e.getKey(),
        tools.findById(e.getKey()).map(t -> t.name()).orElse("Herramienta " + e.getKey()),
        e.getValue().size(), e.getValue().stream().mapToLong(Reservation::quantity).sum()))
        .sorted(Comparator.comparingLong(PopularTool::reservations).reversed()
            .thenComparing(PopularTool::toolId)).limit(5).toList();
    return new Summary(range, found.size(), statuses, paid, amount, inUse, returned, overdue, incidents, popular);
  }
}
