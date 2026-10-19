package com.renttool.infrastructure.billing.persistence;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.domain.billing.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
@Repository
class BillingPersistenceAdapter implements PaymentStore {
  private final PaymentJpaRepository payments;
  private final InvoiceJpaRepository invoices;
  private final ObjectMapper json;
  BillingPersistenceAdapter(PaymentJpaRepository payments, InvoiceJpaRepository invoices, ObjectMapper json) {
    this.payments=payments; this.invoices=invoices; this.json=json;
  }
  public List<Payment> history(Long id) { return payments.findByReservationIdOrderByCreatedAtDesc(id).stream().map(this::domain).toList(); }
  public Payment save(Payment p) {
    var e = new PaymentEntity();
    e.reference=p.reference(); e.reservationId=p.reservationId(); e.amount=p.amount();
    e.status=p.status().name(); e.transactionId=p.transactionId(); e.createdAt=p.createdAt(); e.updatedAt=p.updatedAt();
    payments.saveAndFlush(e);
    return p;
  }
  public Optional<Invoice> invoice(Long id) {
    return invoices.findById(id).map(e -> json.readValue(e.payload, Invoice.class));
  }
  public void issue(Invoice i) {
    var e = new InvoiceEntity(); e.reservationId=i.reservationId(); e.number=i.number();
    e.paymentReference=i.paymentReference(); e.payload=json.writeValueAsString(i);
    invoices.saveAndFlush(e);
  }
  private Payment domain(PaymentEntity e) {
    return new Payment(e.reference,e.reservationId,e.amount,Payment.Status.valueOf(e.status),e.transactionId,e.createdAt,e.updatedAt);
  }
}
