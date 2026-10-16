package com.renttool.application.reservation.service;

import com.renttool.application.reservation.port.in.HistoryEntry;
import com.renttool.domain.reservation.*;
import com.renttool.domain.reservation.ReservationEvent.ActorRole;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Arma la línea de tiempo de una reserva a partir de sus eventos registrados. */
final class Timeline {
  static final String SYSTEM_NAME = "Sistema";

  private Timeline() {}

  /**
   * Si el último evento no explica el estado actual, la reserva cambió antes de que existiera el
   * historial: se añade ese paso marcado como no registrado en lugar de inventar fecha o autor.
   */
  static List<HistoryEntry> of(
      Reservation r, List<ReservationEvent> events, Function<Long, String> names) {
    var entries = new ArrayList<HistoryEntry>();
    if (events.isEmpty())
      entries.add(
          new HistoryEntry(
              r.requestedAt(),
              ReservationStatus.SOLICITADA,
              ActorRole.CLIENTE,
              r.clientId(),
              names.apply(r.clientId()),
              null,
              true));
    for (var e : events)
      entries.add(
          new HistoryEntry(
              e.occurredAt(),
              e.action(),
              e.actorRole(),
              e.actorId(),
              e.actorId() == null ? SYSTEM_NAME : names.apply(e.actorId()),
              e.detail(),
              true));
    if (entries.getLast().action() != r.status())
      entries.add(new HistoryEntry(null, r.status(), null, null, null, r.note(), false));
    return entries;
  }
}
