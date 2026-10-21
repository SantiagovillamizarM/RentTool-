package com.renttool.application;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase.Checkout;
import com.renttool.application.billing.port.out.*;
import com.renttool.application.billing.service.PaymentService;
import com.renttool.domain.billing.*;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
  final Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
  final Participant client = new Participant(2L, Participant.Role.CLIENTE);
  final Memory store = new Memory();
  final Bookings bookings = new Bookings();
  final PaymentService service = new PaymentService(bookings, store, clock);

  static class Bookings implements BillingBookings {
    String status = "SOLICITADA";
    public Booking find(Long id) {
      return new Booking(id, 2L, 3L, status, LocalDate.of(2026,10,20), LocalDate.of(2026,10,22),
          1, new BigDecimal("25"), new BigDecimal("50"), "Taladro", "Cliente",
          "c@test.example", "Proveedor", "123-4", "Calle 1");
    }
    public Booking lock(Long id) { return find(id); }
    int holds;
    public void holdUnits(Long id) { holds++; }
  }

  static class Memory implements PaymentStore {
    Map<String, Payment> payments = new LinkedHashMap<>();
    Map<Long, Invoice> invoices = new HashMap<>();
    int issued;
    public List<Payment> history(Long id) {
      return payments.values().stream().filter(payment -> payment.reservationId().equals(id)).toList();
    }
    public Payment save(Payment payment) { payments.put(payment.reference(), payment); return payment; }
    public Optional<Invoice> invoice(Long id) { return Optional.ofNullable(invoices.get(id)); }
    public void issue(Invoice invoice) { invoices.put(invoice.reservationId(), invoice); issued++; }
    public void replace(Invoice invoice) { invoices.put(invoice.reservationId(), invoice); }
  }

  static Checkout card(String number) {
    return new Checkout(Checkout.Method.TARJETA, number, "ANA RUIZ", 12, 2030, "123", null);
  }
  static final Checkout APPROVED = card("4242 4242 4242 4242");
  static final Checkout DECLINED = card("4000 0000 0000 0002");

  @Test void approvedPaymentUsesReservationTotalAndIssuesOneInvoice() {
    var payment = service.pay(client, 1L, APPROVED);
    assertEquals(0, new BigDecimal("50").compareTo(payment.amount()));
    assertEquals(Payment.Status.APPROVED, payment.status());
    assertEquals("Visa •••• 4242", payment.detail());
    assertEquals(payment.reference(), service.pay(client, 1L, APPROVED).reference());
    assertEquals(1, store.issued);
    assertEquals(1, bookings.holds, "al aprobarse, la solicitud aparta sus unidades una sola vez");
    assertEquals("Taladro", service.invoice(client, 1L).toolName());
    assertEquals("Visa •••• 4242", service.invoice(client, 1L).paymentDetail());
    assertDoesNotThrow(() -> service.ensurePaid(1L));
  }

  @Test void declinedPaymentAllowsAnotherAttempt() {
    var rejected = service.pay(client, 1L, DECLINED);
    assertEquals(Payment.Status.DECLINED, rejected.status());
    assertEquals("Fondos insuficientes", rejected.message());
    assertTrue(store.invoice(1L).isEmpty());
    assertThrows(ConflictException.class, () -> service.ensurePaid(1L));
    var approved = service.pay(client, 1L, new Checkout(Checkout.Method.NEQUI, null, null, 0, 0, null, "3001234567"));
    assertNotEquals(rejected.reference(), approved.reference());
    assertEquals(2, store.history(1L).size());
    assertEquals(1, store.issued);
  }

  @Test void refundVoidsTheApprovedPaymentAndTheInvoice() {
    service.pay(client, 1L, APPROVED);
    service.refundIfPaid(1L, "El proveedor rechazó la solicitud");
    assertEquals(Payment.Status.VOIDED, store.history(1L).get(0).status());
    assertNotNull(store.invoice(1L).orElseThrow().voidedAt());
    assertThrows(ConflictException.class, () -> service.ensurePaid(1L));
    assertDoesNotThrow(() -> service.refundIfPaid(2L, "Sin pago: no hace nada"));
  }

  @Test void otherClientsCannotPayOrReadTheInvoice() {
    var other = new Participant(9L, Participant.Role.CLIENTE);
    assertThrows(ForbiddenOperationException.class, () -> service.pay(other, 1L, APPROVED));
    assertThrows(ForbiddenOperationException.class, () -> service.get(other, 1L));
    assertThrows(ForbiddenOperationException.class, () -> service.invoice(other, 1L));
  }

  @Test void closedReservationsCannotBePaid() {
    bookings.status = "CANCELADA";
    assertThrows(ConflictException.class, () -> service.pay(client, 1L, APPROVED));
    bookings.status = "RECHAZADA";
    assertThrows(ConflictException.class, () -> service.pay(client, 1L, APPROVED));
    assertTrue(store.payments.isEmpty());
  }
}
