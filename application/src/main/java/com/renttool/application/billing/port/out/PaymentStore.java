package com.renttool.application.billing.port.out;

import com.renttool.domain.billing.*;
import java.util.List;
import java.util.Optional;

public interface PaymentStore {
  Optional<Payment> find(String reference);
  List<Payment> history(Long reservationId);
  Payment save(Payment payment);
  Optional<Invoice> invoice(Long reservationId);
  void issue(Invoice invoice);
}
