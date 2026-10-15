package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.in.*;
import com.renttool.application.reservation.port.out.*;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.time.*;
import java.util.LinkedHashSet;
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

  private final ReservationRepository reservations;
  private final RentalInventory inventory;
  private final ParticipantDirectory directory;
  private final UnitAllocator allocator;
  private final Clock clock;

  public ReservationService(
      ReservationRepository reservations,
      RentalInventory inventory,
      ParticipantDirectory directory,
      Clock clock) {
    this.reservations = reservations;
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
    return details(reservations.save(reservation.accept(units)), tool);
  }

  @Override
  public ReservationDetails reject(Participant provider, Long reservationId, String reason) {
    var reservation = lockedForChange(reservationId);
    var tool = tool(reservation.toolId());
    reservation.ensureDecidableBy(provider, tool.providerId());
    return details(reservations.save(reservation.reject(reason)), tool);
  }

  @Override
  public void cancel(Participant participant, Long reservationId) {
    var reservation = lockedForChange(reservationId);
    reservation.ensureCancellableBy(participant);
    reservations.save(reservation.cancel(today()));
  }

  @Override
  public int expireOverdue() {
    var now = clock.instant();
    int expired = 0;
    for (var id : reservations.findPendingIdsDueBy(now)) {
      // Pudo aceptarse entre la consulta y el bloqueo: se vuelve a comprobar ya bloqueada.
      var reservation = lockedForChange(id);
      if (reservation.isPending() && !reservation.respondBy().isAfter(now)) {
        reservations.save(reservation.expire());
        expired++;
      }
    }
    return expired;
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
