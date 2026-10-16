package com.renttool.application.reservation.port.in;

import com.renttool.domain.reservation.ReservationEvent.ActorRole;
import com.renttool.domain.reservation.ReservationStatus;
import java.time.Instant;

/**
 * Un paso en la vida de una reserva. {@code recorded} es false para cambios hechos antes de que
 * existiera el historial: se conoce el estado, pero no cuándo ni quién; esos campos llegan null.
 */
public record HistoryEntry(
    Instant at,
    ReservationStatus action,
    ActorRole actorRole,
    Long actorId,
    String actorName,
    String detail,
    boolean recorded) {}
