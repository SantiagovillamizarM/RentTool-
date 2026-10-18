package com.renttool.infrastructure.billing.persistence;
import jakarta.persistence.*;
@Entity @Table(name="invoices")
class InvoiceEntity {
  @Id Long reservationId;
  @Column(unique=true,length=50) String number;
  @Column(unique=true,length=50) String paymentReference;
  @Column(columnDefinition="TEXT") String payload;
}
