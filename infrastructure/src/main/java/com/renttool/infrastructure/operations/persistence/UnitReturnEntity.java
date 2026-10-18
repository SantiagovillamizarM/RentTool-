package com.renttool.infrastructure.operations.persistence;
import jakarta.persistence.*;

@Entity @Table(name = "rental_returns") @IdClass(UnitReturnId.class)
class UnitReturnEntity {
  @Id Long reservationId;
  @Id Long unitId;
  @Column(name = "return_condition", length = 20) String condition;
  @Column(length = 500) String notes;
}
