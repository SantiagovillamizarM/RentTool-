package com.renttool.infrastructure.billing.persistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="payments")
class PaymentEntity {
  @Id @Column(length=50) String reference;
  Long reservationId;
  @Column(precision=14,scale=2) BigDecimal amount;
  String status;
  @Column(unique=true,length=100) String transactionId;
  Instant createdAt;
  Instant updatedAt;
  @Column(length=20) String method;
  @Column(length=60) String detail;
  @Column(length=200) String message;
}
