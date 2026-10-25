package com.renttool.infrastructure.review.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewJpaRepository extends JpaRepository<ReviewEntity, Long> {
  Optional<ReviewEntity> findByReservationId(Long reservationId);

  List<ReviewEntity> findByToolIdOrderByCreatedAtDescIdDesc(Long toolId);
}
