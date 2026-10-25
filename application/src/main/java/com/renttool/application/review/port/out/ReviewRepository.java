package com.renttool.application.review.port.out;

import com.renttool.domain.review.Review;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository {
  Review save(Review review);

  Optional<Review> findByReservationId(Long reservationId);

  /** Calificaciones de la herramienta, la más reciente primero. */
  List<Review> findByToolId(Long toolId);
}
