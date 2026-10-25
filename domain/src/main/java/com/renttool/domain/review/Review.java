package com.renttool.domain.review;

import com.renttool.domain.shared.exception.InvalidInputException;
import java.time.Instant;

/**
 * Calificación que un cliente da a la herramienta de una reserva suya ya devuelta. Hay como máximo
 * una por reserva; el comentario es opcional.
 */
public record Review(
    Long id,
    Long reservationId,
    Long toolId,
    Long clientId,
    int rating,
    String comment,
    Instant createdAt) {
  public static final int MAX_COMMENT = 500;

  public Review {
    if (reservationId == null || toolId == null || clientId == null || createdAt == null)
      throw new InvalidInputException("La calificación requiere reserva, herramienta y cliente");
    if (rating < 1 || rating > 5)
      throw new InvalidInputException("La calificación debe estar entre 1 y 5 estrellas");
    comment = comment == null || comment.isBlank() ? null : comment.strip();
    if (comment != null && comment.length() > MAX_COMMENT)
      throw new InvalidInputException("El comentario admite hasta " + MAX_COMMENT + " caracteres");
  }

  public static Review create(
      Long reservationId, Long toolId, Long clientId, int rating, String comment, Instant now) {
    return new Review(null, reservationId, toolId, clientId, rating, comment, now);
  }
}
