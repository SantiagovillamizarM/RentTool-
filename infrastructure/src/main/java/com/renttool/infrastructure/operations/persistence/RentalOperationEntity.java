package com.renttool.infrastructure.operations.persistence;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "rental_operations")
class RentalOperationEntity {
  @Id Long reservationId;
  Instant deliveredAt;
  Long deliveredBy;
  @Column(length = 500) String deliveryNotes;
  Instant returnedAt;
  Long returnedBy;
  @Column(length = 500) String returnNotes;
}
