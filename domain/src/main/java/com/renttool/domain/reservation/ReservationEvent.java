package com.renttool.domain.reservation;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.Instant;

/**
 * Hecho inmutable en la vida de una reserva: qué pasó, cuándo y quién lo hizo. Los eventos solo se
 * agregan; nunca se modifican ni se borran, para que sirvan como registro de auditoría.
 */
public record ReservationEvent(
    Long id,
    Long reservationId,
    ReservationStatus action,
    Long actorId,
    ActorRole actorRole,
    Instant occurredAt,
    String detail) {
  public static final int MAX_DETAIL = 500;

  /** Rol con que actuó el autor. SISTEMA es la tarea programada que vence solicitudes. */
  public enum ActorRole {
    CLIENTE,
    PROVEEDOR,
    ADMINISTRADOR,
    SISTEMA
  }

  public ReservationEvent {
    if (reservationId == null) throw new InvalidInputException("El evento requiere una reserva");
    if (action == null || occurredAt == null || actorRole == null)
      throw new InvalidInputException("El evento requiere acción, fecha y autor");
    if ((actorRole == ActorRole.SISTEMA) != (actorId == null))
      throw new InvalidInputException("Solo los eventos del sistema carecen de autor");
    if (detail != null && detail.isBlank()) detail = null;
    if (detail != null && detail.length() > MAX_DETAIL) detail = detail.substring(0, MAX_DETAIL);
  }

  public static ReservationEvent by(
      Participant actor, Long reservationId, ReservationStatus action, Instant at, String detail) {
    actor.ensureAuthenticated();
    return new ReservationEvent(
        null, reservationId, action, actor.id(), ActorRole.valueOf(actor.role().name()), at, detail);
  }

  public static ReservationEvent bySystem(
      Long reservationId, ReservationStatus action, Instant at, String detail) {
    return new ReservationEvent(
        null, reservationId, action, null, ActorRole.SISTEMA, at, detail);
  }
}
