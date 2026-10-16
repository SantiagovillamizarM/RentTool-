package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.in.ReservationReportUseCase;
import com.renttool.application.reservation.port.out.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.ForbiddenOperationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Genera los informes del administrador. Las fechas del rango son días de la zona del negocio;
 * cada informe generado queda en {@link ReportDownloadLog}.
 */
public class ReservationReportService implements ReservationReportUseCase {
  static final String RESERVATIONS = "reservas";
  static final String EVENTS = "historial";

  private final ReservationRepository reservations;
  private final ReservationEventRepository events;
  private final RentalInventory inventory;
  private final ParticipantDirectory directory;
  private final ReportDownloadLog log;
  private final Clock clock;

  public ReservationReportService(
      ReservationRepository reservations,
      ReservationEventRepository events,
      RentalInventory inventory,
      ParticipantDirectory directory,
      ReportDownloadLog log,
      Clock clock) {
    this.reservations = reservations;
    this.events = events;
    this.inventory = inventory;
    this.directory = directory;
    this.log = log;
    this.clock = clock;
  }

  @Override
  public List<ReservationRow> reservations(
      Participant admin, Range range, ReservationStatus status) {
    ensureAdministrator(admin);
    var lookup = new Lookup();
    var found = reservations.findRequestedBetween(start(range), end(range), status);
    var eventsByReservation =
        events.findByReservations(found.stream().map(Reservation::id).toList()).stream()
            .collect(Collectors.groupingBy(ReservationEvent::reservationId));
    var rows =
        found.stream()
            .map(
                r -> {
                  var tool = lookup.tool(r.toolId());
                  var client = lookup.card(r.clientId());
                  var provider = tool.map(t -> lookup.card(t.providerId()));
                  var timeline =
                      Timeline.of(
                          r,
                          eventsByReservation.getOrDefault(r.id(), List.of()),
                          id -> lookup.card(id).name());
                  return new ReservationRow(
                      r.id(),
                      r.requestedAt(),
                      client.name(),
                      client.email(),
                      provider.map(ParticipantDirectory.Card::name).orElse(null),
                      provider.map(ParticipantDirectory.Card::taxId).orElse(null),
                      tool.map(RentableTool::name).orElse("Herramienta " + r.toolId()),
                      lookup.category(r.toolId()),
                      r.period().start(),
                      r.period().end(),
                      r.period().days(),
                      r.quantity(),
                      r.dailyRate(),
                      r.total(),
                      r.status(),
                      timeline.getLast(),
                      r.note());
                })
            .toList();
    log.record(admin.id(), RESERVATIONS, range.from(), range.to(), clock.instant());
    return rows;
  }

  @Override
  public List<EventRow> events(Participant admin, Range range) {
    ensureAdministrator(admin);
    var lookup = new Lookup();
    var rows =
        events.findOccurredBetween(start(range), end(range)).stream()
            .map(
                e -> {
                  var card = e.actorId() == null ? null : lookup.card(e.actorId());
                  return new EventRow(
                      e.occurredAt(),
                      e.reservationId(),
                      lookup.toolOfReservation(e.reservationId()),
                      e.action(),
                      e.actorRole(),
                      card == null ? Timeline.SYSTEM_NAME : card.name(),
                      card == null ? null : card.email(),
                      e.detail());
                })
            .toList();
    log.record(admin.id(), EVENTS, range.from(), range.to(), clock.instant());
    return rows;
  }

  private static void ensureAdministrator(Participant p) {
    if (!p.isAdministrator())
      throw new ForbiddenOperationException("Solo el administrador descarga informes");
  }

  private Instant start(Range range) {
    return dayStart(range.from());
  }

  /** Exclusivo: el inicio del día siguiente a la fecha final. */
  private Instant end(Range range) {
    return dayStart(range.to().plusDays(1));
  }

  private Instant dayStart(LocalDate day) {
    return day.atStartOfDay(clock.getZone()).toInstant();
  }

  /** Evita consultar varias veces la misma persona o herramienta dentro de un informe. */
  private class Lookup {
    private final Map<Long, ParticipantDirectory.Card> cards = new HashMap<>();
    private final Map<Long, Optional<RentableTool>> tools = new HashMap<>();
    private final Map<Long, String> categories = new HashMap<>();
    private final Map<Long, String> toolNamesByReservation = new HashMap<>();

    ParticipantDirectory.Card card(Long id) {
      return cards.computeIfAbsent(id, directory::cardOf);
    }

    Optional<RentableTool> tool(Long id) {
      return tools.computeIfAbsent(id, inventory::find);
    }

    String category(Long toolId) {
      return categories.computeIfAbsent(toolId, id -> inventory.categoryOf(id).orElse(null));
    }

    String toolOfReservation(Long reservationId) {
      return toolNamesByReservation.computeIfAbsent(
          reservationId,
          id ->
              reservations
                  .findById(id)
                  .flatMap(r -> tool(r.toolId()))
                  .map(RentableTool::name)
                  .orElse(null));
    }
  }
}
