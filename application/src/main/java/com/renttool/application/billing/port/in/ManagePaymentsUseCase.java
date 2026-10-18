package com.renttool.application.billing.port.in;

import com.renttool.domain.billing.Invoice;
import com.renttool.domain.billing.Payment;
import com.renttool.domain.reservation.Participant;
import java.util.List;

/** Pagos de demostración para el proyecto escolar. No mueve dinero. */
public interface ManagePaymentsUseCase {
  Payment pay(Participant actor, Long reservationId, boolean approved);
  Summary get(Participant actor, Long reservationId);
  Invoice invoice(Participant actor, Long reservationId);
  void ensureCancellationAllowed(Long reservationId);

  record Summary(boolean payable, List<Payment> payments, String invoiceNumber) {}
}
