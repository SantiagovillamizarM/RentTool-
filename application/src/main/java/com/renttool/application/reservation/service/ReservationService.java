package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.in.*;
import com.renttool.application.reservation.port.out.*;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.time.*;
import java.text.NumberFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Casos de uso de reservas. No abre transacciones: el adaptador que lo publica debe ejecutar cada
 * operación dentro de una con aislamiento READ COMMITTED. Los cambios de estado bloquean primero la
 * reserva y aceptar bloquea además la herramienta ({@link RentalInventory#lockForAllocation}); al
 * obtener el bloqueo, las lecturas siguientes deben ver lo que confirmó quien lo tenía antes.
 */
public class ReservationService
    implements ManageReservationsUseCase, ExpireReservationsUseCase, HoldUnitsUseCase {
  /** Plazo para pagar y para que el proveedor responda, acotado además al día de inicio. */
  static final Duration RESPONSE_WINDOW = Duration.ofHours(24);
  /**
   * Una reserva que empieza hoy necesita al menos este margen antes de medianoche; si no, el plazo
   * para pagar y aceptar sería de pocos minutos.
   */
  static final Duration MIN_SAME_DAY_WINDOW = Duration.ofHours(2);
  public static final String UNDELIVERED = "No se pudo entregar el equipo";

  /** Importes como se leen en Colombia: 150.000 o 12.500,5. NumberFormat no es seguro entre hilos. */
  private static final ThreadLocal<NumberFormat> PESOS =
      ThreadLocal.withInitial(
          () -> {
            var f = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
            f.setMaximumFractionDigits(2);
            return f;
          });

  private final ReservationRepository reservations;
  private final ReservationEventRepository events;
  private final RentalInventory inventory;
  private final ParticipantDirectory directory;
  private final UnitAllocator allocator;
  private final Clock clock;

  public ReservationService(
      ReservationRepository reservations,
      ReservationEventRepository events,
      RentalInventory inventory,
      ParticipantDirectory directory,
      Clock clock) {
    this.reservations = reservations;
    this.events = events;
    this.inventory = inventory;
    this.directory = directory;
    this.allocator = new UnitAllocator(reservations, inventory);
    this.clock = clock;
  }

  @Override
  public ReservationDetails request(Participant client, RequestCommand c) {
    client.ensureClient();
    // Sin teléfono el proveedor no tiene cómo coordinar el pago ni la entrega.
    if (directory.cardOf(client.id()).phone() == null)
      throw new InvalidInputException(
          "Agrega tu número de teléfono en tu perfil para que el proveedor pueda contactarte");
    var period = new RentalPeriod(c.start(), c.end());
    if (period.startsBefore(today()))
      throw new InvalidInputException("No se puede reservar en fechas pasadas");
    var tool = tool(c.toolId());
    if (allocator.freeUnits(tool.id(), period).size() < c.quantity())
      throw new ConflictException("No hay unidades suficientes para esas fechas");
    var now = clock.instant();
    var endOfStartDay = period.start().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    if (now.plus(MIN_SAME_DAY_WINDOW).isAfter(endOfStartDay))
      throw new InvalidInputException(
          "Para empezar hoy, solicita antes de las 10:00 p. m.; si no, elige desde mañana");
    var respondBy = min(now.plus(RESPONSE_WINDOW), endOfStartDay);
    var saved =
        reservations.save(
            Reservation.request(client, tool, period, c.quantity(), now, respondBy));
    record(
        client,
        saved,
        c.quantity()
            + (c.quantity() == 1 ? " unidad" : " unidades")
            + " · total $"
            + PESOS.get().format(saved.total()));
    return details(saved, tool);
  }

  @Override
  public ReservationDetails get(Participant participant, Long reservationId) {
    var reservation = existing(reservationId);
    var tool = tool(reservation.toolId());
    reservation.ensureVisibleTo(participant, tool.providerId());
    return details(reservation, tool);
  }

  @Override
  public PageResult<ReservationDetails> list(Participant participant, PageQuery page) {
    participant.ensureAuthenticated();
    PageResult<Reservation> result;
    if (participant.isAdministrator()) result = reservations.findAll(page);
    else if (participant.isProvider()) {
      var toolIds = inventory.toolIdsOf(participant.id());
      result =
          toolIds.isEmpty()
              ? new PageResult<>(List.of(), 0, 0, page.page())
              : reservations.findByTools(toolIds, page);
    } else result = reservations.findByClient(participant.id(), page);
    return result.map(r -> details(r, tool(r.toolId())));
  }

  @Override
  public ReservationDetails accept(Participant provider, Long reservationId) {
    var reservation = lockedForChange(reservationId);
    var tool =
        inventory
            .lockForAllocation(reservation.toolId())
            .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
    reservation.ensureDecidableBy(provider, tool.providerId());
    if (!reservation.isPending())
      throw new ConflictException("Solo se puede aceptar una solicitud pendiente");
    if (reservation.isDue(clock.instant()))
      throw new ConflictException("El plazo para responder esta solicitud ya venció");
    if (reservation.period().startsBefore(today()))
      throw new ConflictException("La fecha de inicio ya pasó; rechaza la solicitud");
    // Con la herramienta bloqueada, nadie más puede asignar sus unidades hasta el commit. Una
    // solicitud pagada ya apartó sus unidades; se conservan si siguen operativas.
    var units = unitsFor(reservation, tool.id());
    var accepted = reservations.save(reservation.accept(units));
    record(
        provider,
        accepted,
        "Unidades asignadas: "
            + accepted.unitIds().stream()
                .sorted()
                .map(id -> "#" + id)
                .collect(Collectors.joining(", ")));
    return details(accepted, tool);
  }

  @Override
  public ReservationDetails reject(Participant provider, Long reservationId, String reason) {
    var reservation = lockedForChange(reservationId);
    var tool = tool(reservation.toolId());
    reservation.ensureDecidableBy(provider, tool.providerId());
    var rejected = reservations.save(reservation.reject(reason));
    record(provider, rejected, rejected.note());
    return details(rejected, tool);
  }

  /**
   * Aparta unidades al pagar una solicitud. Lo invoca el pago, dentro de su misma transacción: si
   * no quedan unidades, la excepción revierte también el cobro.
   */
  @Override
  public void holdUnits(Long reservationId) {
    var reservation = lockedForChange(reservationId);
    if (!reservation.isPending() || reservation.holdsUnits()) return;
    if (reservation.isDue(clock.instant()))
      throw new ConflictException("El plazo para pagar esta solicitud ya venció");
    var tool =
        inventory
            .lockForAllocation(reservation.toolId())
            .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
    reservations.save(reservation.hold(unitsFor(reservation, tool.id())));
  }

  /** Unidades para la reserva: las que ya apartó si siguen operativas, o las primeras libres. */
  private LinkedHashSet<Long> unitsFor(Reservation reservation, Long toolId) {
    var operational = inventory.operationalUnitIds(toolId);
    if (reservation.holdsUnits() && operational.containsAll(reservation.unitIds()))
      return new LinkedHashSet<>(reservation.unitIds());
    var own = reservation.unitIds();
    var candidates = new LinkedHashSet<Long>(own.stream().filter(operational::contains).toList());
    candidates.addAll(allocator.freeUnits(toolId, reservation.period()));
    return new LinkedHashSet<>(candidates.stream().limit(reservation.quantity()).toList());
  }

  @Override
  public void cancel(Participant participant, Long reservationId) {
    var reservation = lockedForChange(reservationId);
    var tool = tool(reservation.toolId());
    // Una reserva aceptada que empezó sin poder entregarse la cancela el proveedor o el admin.
    boolean stuck = reservation.status() == ReservationStatus.ACEPTADA
        && !today().isBefore(reservation.period().start())
        && !reservations.hasDelivery(reservationId);
    if (stuck) {
      if (!participant.isAdministrator()
          && !(participant.isProvider() && tool.providerId().equals(participant.id())))
        throw new ForbiddenOperationException(
            "El alquiler ya comenzó; el proveedor o el administrador pueden cancelarlo si no se entregó");
      var cancelled = reservations.save(reservation.cancelUndelivered(UNDELIVERED));
      record(participant, cancelled, UNDELIVERED);
      return;
    }
    reservation.ensureCancellableBy(participant);
    var cancelled = reservations.save(reservation.cancel(today()));
    record(
        participant,
        cancelled,
        reservation.status() == ReservationStatus.ACEPTADA
            ? "Se liberaron las unidades asignadas"
            : null);
  }

  @Override
  public List<HistoryEntry> history(Participant participant, Long reservationId) {
    var reservation = existing(reservationId);
    reservation.ensureVisibleTo(participant, tool(reservation.toolId()).providerId());
    return Timeline.of(reservation, events.findByReservation(reservationId), directory::nameOf);
  }

  @Override
  public int expireOverdue() {
    return expireOverdueIds().size();
  }

  /** Vence la solicitud ya mismo si su plazo pasó, sin esperar a la revisión periódica. */
  @Override
  public boolean expireIfDue(Long reservationId) {
    var reservation = lockedForChange(reservationId);
    if (!reservation.isDue(clock.instant())) return false;
    var saved = reservations.save(reservation.expire());
    events.append(ReservationEvent.bySystem(
        saved.id(), saved.status(), clock.instant(), "Sin respuesta del proveedor dentro del plazo"));
    return true;
  }

  /**
   * Cancela las reservas aceptadas que llegaron a su fecha de devolución sin entrega registrada,
   * para que el pago no quede retenido. Devuelve sus ids, para reembolsarlas.
   */
  @Override
  public int cancelUndeliveredEnded() {
    return cancelUndeliveredEndedIds().size();
  }

  public List<Long> cancelUndeliveredEndedIds() {
    var cancelled = new java.util.ArrayList<Long>();
    for (var id : reservations.findAcceptedUndeliveredStartedBy(today())) {
      var reservation = lockedForChange(id);
      if (reservation.status() != ReservationStatus.ACEPTADA
          || reservation.period().end().isAfter(today()) || reservations.hasDelivery(id)) continue;
      var saved = reservations.save(reservation.cancelUndelivered(UNDELIVERED));
      events.append(ReservationEvent.bySystem(saved.id(), saved.status(), clock.instant(),
          "Terminó el periodo sin entrega registrada"));
      cancelled.add(saved.id());
    }
    return cancelled;
  }

  /** Igual que {@link #expireOverdue}, pero devuelve qué reservas vencieron (para reembolsarlas). */
  public List<Long> expireOverdueIds() {
    var now = clock.instant();
    var expired = new java.util.ArrayList<Long>();
    for (var id : reservations.findPendingIdsDueBy(now)) {
      // Pudo aceptarse entre la consulta y el bloqueo: se vuelve a comprobar ya bloqueada.
      var reservation = lockedForChange(id);
      if (reservation.isPending() && !reservation.respondBy().isAfter(now)) {
        var saved = reservations.save(reservation.expire());
        events.append(
            ReservationEvent.bySystem(
                saved.id(), saved.status(), now, "Sin respuesta del proveedor dentro del plazo"));
        expired.add(saved.id());
      }
    }
    return expired;
  }

  /** Cada cambio de estado deja constancia de quién lo hizo y cuándo, en la misma transacción. */
  private void record(Participant actor, Reservation r, String detail) {
    var at = r.status() == ReservationStatus.SOLICITADA ? r.requestedAt() : clock.instant();
    events.append(ReservationEvent.by(actor, r.id(), r.status(), at, detail));
  }

  private Reservation existing(Long id) {
    return reservations
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
  }

  private Reservation lockedForChange(Long id) {
    return reservations
        .findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
  }

  private RentableTool tool(Long toolId) {
    return inventory
        .find(toolId)
        .orElseThrow(() -> new NotFoundException("Herramienta no encontrada"));
  }

  private ReservationDetails details(Reservation r, RentableTool tool) {
    var client = directory.cardOf(r.clientId());
    return new ReservationDetails(
        r, tool.name(), tool.providerId(), client.name(), client.email(), client.phone());
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }
}
