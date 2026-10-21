package com.renttool.application.billing.service;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase;
import com.renttool.application.billing.port.out.BillingBookings;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.domain.billing.Invoice;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.billing.SimulatedGateway;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.shared.exception.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Simula el pago con la pasarela de prueba y crea la factura en la misma operación. El cliente
 * paga al solicitar; el proveedor solo acepta reservas pagadas y, si la reserva no sigue adelante,
 * el pago se reembolsa y la factura queda anulada.
 */
public class PaymentService implements ManagePaymentsUseCase {
  /** Pendiente o, en reservas anteriores a este flujo, aceptada sin pago registrado. */
  private static final Set<String> PAYABLE = Set.of("SOLICITADA", "ACEPTADA");

  private final BillingBookings bookings;
  private final PaymentStore store;
  private final Clock clock;

  public PaymentService(BillingBookings bookings, PaymentStore store, Clock clock) {
    this.bookings = bookings;
    this.store = store;
    this.clock = clock;
  }

  @Override
  public Payment pay(Participant actor, Long id, Checkout checkout) {
    actor.ensureClient();
    var booking = bookings.lock(id);
    if (!booking.clientId().equals(actor.id()))
      throw new ForbiddenOperationException("Solo el cliente de la reserva puede pagar");

    // Si se pulsa dos veces, se devuelve el pago existente sin duplicar la factura.
    var existing = approved(id);
    if (existing.isPresent()) return existing.get();

    if (!PAYABLE.contains(booking.status()) || booking.start().isBefore(LocalDate.now(clock)))
      throw new ConflictException("Solo puedes pagar una reserva pendiente o aceptada cuya fecha de inicio no haya pasado");
    if (checkout == null || checkout.method() == null)
      throw new InvalidInputException("Elige un método de pago");

    var result = switch (checkout.method()) {
      case TARJETA -> SimulatedGateway.card(checkout.number(), checkout.holder(),
          checkout.expMonth(), checkout.expYear(), checkout.cvv(), LocalDate.now(clock));
      case NEQUI -> SimulatedGateway.nequi(checkout.phone());
    };
    // Al aprobarse, la solicitud aparta sus unidades. Si ya no quedan, la excepción revierte todo y
    // no se registra ningún cobro.
    if (result.approved() && "SOLICITADA".equals(booking.status())) bookings.holdUnits(id);
    var now = clock.instant();
    var payment = new Payment(
        "PAY-" + UUID.randomUUID(), id, booking.total(),
        result.approved() ? Payment.Status.APPROVED : Payment.Status.DECLINED,
        null, now, now, result.method().name(), result.detail(), result.message());
    store.save(payment);

    if (result.approved()) {
      store.issue(new Invoice(
          "RT-" + id, payment.reference(), id, now,
          booking.sellerName(), booking.sellerTaxId(), booking.sellerAddress(),
          booking.buyerName(), booking.buyerEmail(), booking.toolName(),
          booking.start(), booking.end(), booking.quantity(), booking.dailyRate(), booking.total(),
          result.detail(), null, null));
    }
    return payment;
  }

  @Override
  public Summary get(Participant actor, Long id) {
    var booking = bookings.find(id);
    ensureVisible(actor, booking);
    boolean payable = actor.isClient() && booking.clientId().equals(actor.id())
        && PAYABLE.contains(booking.status())
        && !booking.start().isBefore(LocalDate.now(clock))
        && approved(id).isEmpty();
    return new Summary(payable, booking.total(), store.history(id),
        store.invoice(id).map(Invoice::number).orElse(null));
  }

  @Override
  public Invoice invoice(Participant actor, Long id) {
    ensureVisible(actor, bookings.find(id));
    return store.invoice(id)
        .orElseThrow(() -> new NotFoundException("Todavía no hay factura para esta reserva"));
  }

  @Override
  public void ensurePaid(Long id) {
    bookings.lock(id);
    if (approved(id).isEmpty())
      throw new ConflictException("El cliente todavía no ha pagado esta reserva; no se puede aceptar");
  }

  @Override
  public void refundIfPaid(Long id, String reason) {
    bookings.lock(id);
    var payment = approved(id);
    if (payment.isEmpty()) return;
    var now = clock.instant();
    store.save(payment.get().refund(now, reason));
    store.invoice(id).ifPresent(invoice -> store.replace(invoice.voided(now, reason)));
  }

  private Optional<Payment> approved(Long id) {
    return store.history(id).stream()
        .filter(payment -> payment.status() == Payment.Status.APPROVED)
        .findFirst();
  }

  private void ensureVisible(Participant actor, BillingBookings.Booking booking) {
    actor.ensureAuthenticated();
    boolean client = actor.isClient() && booking.clientId().equals(actor.id());
    boolean provider = actor.isProvider() && booking.providerId().equals(actor.id());
    if (!actor.isAdministrator() && !client && !provider)
      throw new ForbiddenOperationException("Este pago no te pertenece");
  }
}
