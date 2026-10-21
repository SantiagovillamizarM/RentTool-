package com.renttool.application.insights.service;

import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.application.insights.port.in.ProviderPerformanceUseCase;
import com.renttool.application.inventory.port.in.CatalogOrder;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.inventory.port.out.ToolRepository.SearchCriteria;
import com.renttool.application.reservation.port.in.ReservationReportUseCase.Range;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.shared.PageQuery;
import com.renttool.domain.identity.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProviderPerformanceService implements ProviderPerformanceUseCase {
  /** Ventas por debajo de esta fracción del promedio generan una alerta. */
  static final BigDecimal LOW_SALES = new BigDecimal("0.5");
  /** Fracción de solicitudes vencidas sin respuesta a partir de la cual se alerta. */
  static final double HIGH_EXPIRED = 0.25;
  /** Aceptación mínima esperada, evaluada solo con suficientes solicitudes respondidas. */
  static final double LOW_ACCEPTANCE = 0.5;
  static final int MIN_ANSWERED = 3;

  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;
  private final ToolRepository tools;
  private final ReservationRepository reservations;
  private final PaymentStore payments;
  private final Clock clock;
  public ProviderPerformanceService(AccountRepository accounts, ProviderProfileRepository profiles,
      ToolRepository tools, ReservationRepository reservations, PaymentStore payments, Clock clock) {
    this.accounts = accounts; this.profiles = profiles; this.tools = tools;
    this.reservations = reservations; this.payments = payments; this.clock = clock;
  }

  public Report get(Participant admin, Range range) {
    if (!admin.isAdministrator())
      throw new ForbiddenOperationException("Solo el administrador compara proveedores");
    var providers = accounts.findByRole(Role.PROVEEDOR, new PageQuery(0, PageQuery.MAX_SIZE)).content();
    var profileById = profiles.findAllByAccountId(providers.stream().map(Account::id).toList()).stream()
        .collect(Collectors.toMap(ProviderProfile::accountId, Function.identity()));
    var ownerOf = new HashMap<Long, Long>();
    for (var p : providers) for (var toolId : tools.findIdsByProviderId(p.id())) ownerOf.put(toolId, p.id());
    var byProvider = reservations.findRequestedBetween(
        range.from().atStartOfDay(clock.getZone()).toInstant(),
        range.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant(), null).stream()
        .filter(r -> ownerOf.containsKey(r.toolId()))
        .collect(Collectors.groupingBy(r -> ownerOf.get(r.toolId())));

    var rows = providers.stream().map(p -> row(p, profileById.get(p.id()),
        byProvider.getOrDefault(p.id(), List.of()))).toList();
    // Los promedios y las alertas solo consideran proveedores activos: los inactivos no operan.
    var active = rows.stream().filter(ProviderRow::active).toList();
    var totalSales = rows.stream().map(ProviderRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
    var activeSales = active.stream().map(ProviderRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
    long activeRequests = active.stream().mapToLong(ProviderRow::requests).sum();
    var averages = new Averages(
        active.isEmpty() ? BigDecimal.ZERO : activeSales.divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP),
        active.isEmpty() ? 0 : (double) activeRequests / active.size(),
        ratio(active.stream().mapToLong(ProviderRow::accepted).sum(), active.stream().mapToLong(ProviderPerformanceService::answered).sum()),
        ratio(active.stream().mapToLong(ProviderRow::expired).sum(), activeRequests));
    var withAlerts = rows.stream().map(r -> r.active() ? withAlerts(r, averages) : r)
        .sorted(Comparator.comparing(ProviderRow::sales).reversed().thenComparing(ProviderRow::name))
        .toList();
    long attention = withAlerts.stream().filter(r -> !r.alerts().isEmpty()).count();
    return new Report(range, totalSales, rows.stream().mapToLong(ProviderRow::requests).sum(),
        averages, attention, withAlerts);
  }

  private ProviderRow row(Account p, ProviderProfile profile, List<Reservation> found) {
    var count = new EnumMap<ReservationStatus, Long>(ReservationStatus.class);
    for (var status : ReservationStatus.values()) count.put(status, 0L);
    var sales = BigDecimal.ZERO;
    long units = 0, sold = 0;
    for (var r : found) {
      count.merge(r.status(), 1L, Long::sum);
      var sale = Sales.of(r, payments.history(r.id()));
      if (sale.signum() > 0) { sales = sales.add(sale); units += r.quantity(); sold++; }
    }
    long accepted = count.get(ReservationStatus.ACEPTADA);
    long activeTools = tools.searchActive(new SearchCriteria(null, null, p.id(), CatalogOrder.RECENT),
        new PageQuery(0, 1)).totalElements();
    var row = new ProviderRow(p.id(), p.name(), p.email(),
        profile != null ? profile.phone() : p.phone(), profile != null ? profile.city() : null,
        p.active(), activeTools, found.size(), accepted, count.get(ReservationStatus.RECHAZADA),
        count.get(ReservationStatus.VENCIDA), count.get(ReservationStatus.CANCELADA),
        count.get(ReservationStatus.SOLICITADA), units, sales,
        sold == 0 ? BigDecimal.ZERO : sales.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP),
        null, ratio(count.get(ReservationStatus.VENCIDA), found.size()), List.of());
    return copy(row, ratio(accepted, answered(row)), row.alerts());
  }

  private static ProviderRow withAlerts(ProviderRow r, Averages avg) {
    var alerts = new ArrayList<String>();
    if (r.activeTools() == 0) alerts.add("No tiene herramientas publicadas");
    else if (r.requests() == 0) alerts.add("No recibió solicitudes en el período");
    if (avg.salesPerProvider().signum() > 0
        && r.sales().compareTo(avg.salesPerProvider().multiply(LOW_SALES)) < 0)
      alerts.add("Ventas por debajo de la mitad del promedio");
    if (r.expired() > 0 && r.expiredRate() >= HIGH_EXPIRED)
      alerts.add("Dejó vencer " + r.expired() + " de " + r.requests() + " solicitudes sin responder");
    if (answered(r) >= MIN_ANSWERED && r.acceptanceRate() < LOW_ACCEPTANCE)
      alerts.add("Acepta menos de la mitad de las solicitudes");
    return copy(r, r.acceptanceRate(), List.copyOf(alerts));
  }

  /** Solicitudes que el proveedor aceptó, rechazó o dejó vencer: las que dependían de él. */
  private static long answered(ProviderRow r) { return r.accepted() + r.rejected() + r.expired(); }

  private static Double ratio(long part, long whole) { return whole == 0 ? null : (double) part / whole; }

  private static ProviderRow copy(ProviderRow r, Double acceptanceRate, List<String> alerts) {
    return new ProviderRow(r.id(), r.name(), r.email(), r.phone(), r.city(), r.active(),
        r.activeTools(), r.requests(), r.accepted(), r.rejected(), r.expired(), r.cancelled(),
        r.pending(), r.unitsRented(), r.sales(), r.averageTicket(), acceptanceRate,
        r.expiredRate(), alerts);
  }
}
