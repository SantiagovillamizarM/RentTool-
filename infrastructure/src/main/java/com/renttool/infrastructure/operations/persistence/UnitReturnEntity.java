package com.renttool.infrastructure.operations.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "rental_returns") @IdClass(UnitReturnId.class)
class UnitReturnEntity {
  @Id Long reservationId;
  @Id Long unitId;
  @Column(name = "return_condition", length = 20) String condition;
  @Column(length = 500) String notes;
  @Column(precision = 14, scale = 2) BigDecimal damageCharge;
}
