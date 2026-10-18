package com.renttool.domain.operations;

import com.renttool.domain.shared.exception.*;
import java.time.Instant;

/** Incidencia conservada en el historial, incluso después de resolverse. */
public record Incident(Long id, Long reservationId, Long unitId, Type type, String description,
    Long reportedBy, Instant reportedAt, Long resolvedBy, Instant resolvedAt, String resolution) {
  public enum Type { DANO, PERDIDA, OTRO }

  public Incident {
    if (reservationId == null || type == null || reportedBy == null || reportedAt == null)
      throw new InvalidInputException("Datos de incidencia incompletos");
    description = required(description, "Describe la incidencia");
    if (type != Type.OTRO && unitId == null)
      throw new InvalidInputException("Un daño o pérdida debe identificar una unidad");
    if (resolvedAt != null) {
      resolution = required(resolution, "Describe cómo se resolvió la incidencia");
      if (resolvedBy == null || resolvedAt.isBefore(reportedAt))
        throw new InvalidInputException("Datos de resolución inválidos");
    }
  }

  public boolean isOpen() { return resolvedAt == null; }
  public boolean blocksUnit() { return isOpen() && unitId != null && type != Type.OTRO; }

  public Incident resolve(Long actorId, Instant at, String notes) {
    if (!isOpen()) throw new ConflictException("La incidencia ya está resuelta");
    return new Incident(id, reservationId, unitId, type, description, reportedBy, reportedAt,
        actorId, at, notes);
  }

  private static String required(String value, String message) {
    if (value == null || value.isBlank()) throw new InvalidInputException(message);
    if (value.length() > 1000) throw new InvalidInputException("El texto admite hasta 1000 caracteres");
    return value.strip();
  }
}
