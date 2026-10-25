package com.renttool.infrastructure.review.persistence;

import com.renttool.application.review.port.out.ReviewRepository;
import com.renttool.domain.review.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ReviewPersistenceAdapter implements ReviewRepository {
  private final ReviewJpaRepository jpa;

  ReviewPersistenceAdapter(ReviewJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Review save(Review review) {
    var entity = new ReviewEntity();
    entity.id = review.id();
    entity.reservationId = review.reservationId();
    entity.toolId = review.toolId();
    entity.clientId = review.clientId();
    entity.rating = review.rating();
    entity.comment = review.comment();
    entity.createdAt = review.createdAt();
    return toDomain(jpa.saveAndFlush(entity));
  }

  @Override
  public Optional<Review> findByReservationId(Long reservationId) {
    return jpa.findByReservationId(reservationId).map(ReviewPersistenceAdapter::toDomain);
  }

  @Override
  public List<Review> findByToolId(Long toolId) {
    return jpa.findByToolIdOrderByCreatedAtDescIdDesc(toolId).stream()
        .map(ReviewPersistenceAdapter::toDomain)
        .toList();
  }

  private static Review toDomain(ReviewEntity e) {
    return new Review(
        e.id, e.reservationId, e.toolId, e.clientId, e.rating, e.comment, e.createdAt);
  }
}
