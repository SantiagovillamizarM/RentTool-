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
public class ReservationService implements ManageReservationsUseCase, ExpireReservationsUseCase {
  /** Plazo del proveedor para responder, acotado además al día de inicio del alquiler. */
  static final Duration RESPONSE_WINDOW = Duration.ofHours(24);

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
    var period = new RentalPeriod(c.start(), c.end());
    if (period.startsBefore(today()))
      throw new InvalidInputException("No se puede reservar en fechas pasadas");
    var tool = tool(c.toolId());
    if (allocator.freeUnits(tool.id(), period).size() < c.quantity())
      throw new ConflictException("No hay unidades suficientes para esas fechas");
    var now = clock.instant();
    var endOfStartDay = period.start().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
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
    if (reservation.period().startsBefore(today()))
      throw new ConflictException("La fecha de inicio ya pasó; rechaza la solicitud");
    // Con la herramienta bloqueada, nadie más puede asignar sus unidades hasta el commit.
    var units =
        new LinkedHashSet<>(
            allocator.freeUnits(tool.id(), reservation.period()).stream()
                .limit(reservation.quantity())
                .toList());
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

  @Override
  public void cancel(Participant participant, Long reservationId) {
    var reservation = lockedForChange(reservationId);
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
    var now = clock.instant();
    int expired = 0;
    for (var id : reservations.findPendingIdsDueBy(now)) {
      // Pudo aceptarse entre la consulta y el bloqueo: se vuelve a comprobar ya bloqueada.
      var reservation = lockedForChange(id);
      if (reservation.isPending() && !reservation.respondBy().isAfter(now)) {
        var saved = reservations.save(reservation.expire());
        events.append(
            ReservationEvent.bySystem(
                saved.id(), saved.status(), now, "Sin respuesta del proveedor dentro del plazo"));
        expired++;
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
    return new ReservationDetails(
        r, tool.name(), tool.providerId(), directory.nameOf(r.clientId()));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }
}
