package com.renttool.infrastructure.review.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tool_reviews")
class ReviewEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long reservationId;
  Long toolId;
  Long clientId;
  int rating;

  @Column(name = "review_comment", length = 500)
  String comment;

  Instant createdAt;
}
