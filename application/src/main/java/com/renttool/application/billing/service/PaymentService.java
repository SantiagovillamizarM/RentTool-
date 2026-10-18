package com.renttool.application.billing.service;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase;
import com.renttool.application.billing.port.out.BillingBookings;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.domain.billing.Invoice;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Simula el pago y crea una factura de prueba en la misma operación. */
public class PaymentService implements ManagePaymentsUseCase {
  private final BillingBookings bookings;
  private final PaymentStore store;
  private final Clock clock;

  public PaymentService(BillingBookings bookings, PaymentStore store, Clock clock) {
    this.bookings = bookings;
    this.store = store;
    this.clock = clock;
  }

  @Override
  public Payment pay(Participant actor, Long id, boolean approved) {
    actor.ensureClient();
    var booking = bookings.lock(id);
    if (!booking.clientId().equals(actor.id()))
      throw new ForbiddenOperationException("Solo el cliente de la reserva puede pagar");

    // Si se pulsa dos veces, se devuelve el pago existente sin duplicar la factura.
    var existing = store.history(id).stream()
        .filter(payment -> payment.status() == Payment.Status.APPROVED)
        .findFirst();
    if (existing.isPresent()) return existing.get();

    if (!"ACEPTADA".equals(booking.status())
        || booking.start().isBefore(LocalDate.now(clock)))
      throw new ConflictException("Solo puedes pagar una reserva aceptada cuya fecha de inicio no haya pasado");

    var now = clock.instant();
    var payment = new Payment(
        "DEMO-" + UUID.randomUUID(), id, booking.total(),
        approved ? Payment.Status.APPROVED : Payment.Status.DECLINED,
        null, now, now);
    store.save(payment);

    if (approved) {
      store.issue(new Invoice(
          "RT-DEMO-" + id, payment.reference(), id, now,
          booking.sellerName(), booking.sellerTaxId(), booking.sellerAddress(),
          booking.buyerName(), booking.buyerEmail(), booking.toolName(),
          booking.start(), booking.end(), booking.quantity(), booking.dailyRate(), booking.total()));
    }
    return payment;
  }

  @Override
  public Summary get(Participant actor, Long id) {
    var booking = bookings.find(id);
    ensureVisible(actor, booking);
    boolean payable = actor.isClient() && booking.clientId().equals(actor.id())
        && "ACEPTADA".equals(booking.status())
        && !booking.start().isBefore(LocalDate.now(clock))
        && store.invoice(id).isEmpty();
    return new Summary(payable, store.history(id),
        store.invoice(id).map(Invoice::number).orElse(null));
  }

  @Override
  public Invoice invoice(Participant actor, Long id) {
    ensureVisible(actor, bookings.find(id));
    return store.invoice(id)
        .orElseThrow(() -> new NotFoundException("Todavía no hay factura para esta reserva"));
  }

  @Override
  public void ensureCancellationAllowed(Long id) {
    bookings.lock(id);
    if (store.history(id).stream().anyMatch(payment -> payment.status() == Payment.Status.APPROVED))
      throw new ConflictException("Una reserva con pago de demostración aprobado no se puede cancelar");
  }

  private void ensureVisible(Participant actor, BillingBookings.Booking booking) {
    actor.ensureAuthenticated();
    boolean client = actor.isClient() && booking.clientId().equals(actor.id());
    boolean provider = actor.isProvider() && booking.providerId().equals(actor.id());
    if (!actor.isAdministrator() && !client && !provider)
      throw new ForbiddenOperationException("Este pago no te pertenece");
  }
}
