package com.renttool.application.insights.service;

import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.insights.port.in.RevenueReportUseCase;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.domain.operations.LateFee;
import com.renttool.domain.operations.RentalOperation;
import com.renttool.domain.operations.UnitReturn;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Ingreso de una venta: el total de la reserva (con sus extensiones) más los recargos y cobros
 * registrados al devolver. Usa la misma definición de venta que las estadísticas.
 */
public class RevenueService implements RevenueReportUseCase {
  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CO"));

  private final ReservationRepository reservations;
  private final ToolRepository tools;
  private final PaymentStore payments;
  private final RentalRecords operations;
  private final Clock clock;

  public RevenueService(ReservationRepository reservations, ToolRepository tools,
      PaymentStore payments, RentalRecords operations, Clock clock) {
    this.reservations = reservations;
    this.tools = tools;
    this.payments = payments;
    this.operations = operations;
    this.clock = clock;
  }

  @Override
  public Report get(Participant actor, Range range, GroupBy groupBy) {
    if (!actor.isProvider() && !actor.isAdministrator())
      throw new ForbiddenOperationException("Solo proveedores y administradores ven los ingresos");
    var by = groupBy == null ? GroupBy.TOOL : groupBy;
    Set<Long> own = actor.isProvider() ? new HashSet<>(tools.findIdsByProviderId(actor.id())) : null;
    var zone = clock.getZone();
    var found = reservations.findRequestedBetween(
        range.from().atStartOfDay(zone).toInstant(),
        range.to().plusDays(1).atStartOfDay(zone).toInstant(), null);

    var groups = new LinkedHashMap<String, Totals>();
    var names = new HashMap<Long, String>();
    for (var reservation : found) {
      if (own != null && !own.contains(reservation.toolId())) continue;
      if (Sales.of(reservation, payments.history(reservation.id())).signum() == 0) continue;
      String key = by == GroupBy.TOOL
          ? String.valueOf(reservation.toolId())
          : YearMonth.from(reservation.requestedAt().atZone(zone)).toString();
      String label = by == GroupBy.TOOL
          ? names.computeIfAbsent(reservation.toolId(), id ->
              tools.findById(id).map(t -> t.name()).orElse("Herramienta " + id))
          : capitalize(YearMonth.parse(key).format(MONTH));
      groups.computeIfAbsent(key, k -> new Totals(label)).add(reservation, extras(reservation));
    }

    var all = new Totals("Total");
    groups.values().forEach(all::merge);
    var rows = new ArrayList<Row>();
    for (var entry : groups.entrySet())
      rows.add(entry.getValue().row(entry.getKey(), all.income()));
    rows.sort(by == GroupBy.TOOL
        ? Comparator.comparing(Row::income).reversed().thenComparing(Row::label)
        : Comparator.comparing(Row::key));
    return new Report(range, by, all.reservations, all.rental, all.extras, all.income(), rows);
  }

  /** Recargo por retraso más cobros por daño o pérdida; cero si aún no se devuelve. */
  private BigDecimal extras(Reservation reservation) {
    var late = operations.operation(reservation.id())
        .map(RentalOperation::lateFee).orElse(LateFee.NONE).amount();
    return operations.returns(reservation.id()).stream()
        .map(UnitReturn::charge).reduce(late, BigDecimal::add);
  }

  private static String capitalize(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private static final class Totals {
    final String label;
    long reservations, unitDays;
    BigDecimal rental = BigDecimal.ZERO, extras = BigDecimal.ZERO;

    Totals(String label) { this.label = label; }

    void add(Reservation reservation, BigDecimal extra) {
      reservations++;
      unitDays += reservation.period().days() * reservation.quantity();
      rental = rental.add(reservation.total());
      extras = extras.add(extra);
    }

    void merge(Totals other) {
      reservations += other.reservations;
      unitDays += other.unitDays;
      rental = rental.add(other.rental);
      extras = extras.add(other.extras);
    }

    BigDecimal income() { return rental.add(extras); }

    Row row(String key, BigDecimal grandTotal) {
      var income = income();
      double share = grandTotal.signum() == 0 ? 0
          : income.multiply(BigDecimal.valueOf(100))
              .divide(grandTotal, 1, RoundingMode.HALF_UP).doubleValue();
      return new Row(key, label, reservations, unitDays, rental, extras, income,
          divide(income, reservations), divide(income, unitDays), share);
    }

    private static BigDecimal divide(BigDecimal amount, long by) {
      return by == 0 ? BigDecimal.ZERO
          : amount.divide(BigDecimal.valueOf(by), 2, RoundingMode.HALF_UP);
    }
  }
}
