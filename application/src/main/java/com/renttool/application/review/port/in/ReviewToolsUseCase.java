package com.renttool.application.review.port.in;

import com.renttool.domain.reservation.Participant;
import java.time.Instant;
import java.util.List;

/** Calificaciones de herramientas: las escribe el cliente tras devolver y las ve cualquiera. */
public interface ReviewToolsUseCase {
  /** El cliente califica su reserva una sola vez, después de que se registró la devolución. */
  ReviewView rate(Participant client, Long reservationId, RateCommand command);

  /** Calificación de una reserva y si quien consulta todavía puede calificarla. */
  ReservationReview ofReservation(Participant participant, Long reservationId);

  /** Promedio y calificaciones de una herramienta, la más reciente primero. Es público. */
  ToolReviews ofTool(Long toolId);

  record RateCommand(int rating, String comment) {}

  record ReviewView(
      Long id,
      Long reservationId,
      Long toolId,
      String clientName,
      int rating,
      String comment,
      Instant createdAt) {}

  record ReservationReview(boolean canReview, ReviewView review) {}

  /** @param average promedio con un decimal; 0 si no hay calificaciones */
  record ToolReviews(double average, int count, List<ReviewView> reviews) {}
}
