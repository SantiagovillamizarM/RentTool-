package com.renttool.infrastructure.billing.external;
import com.renttool.application.billing.port.out.BillingBookings;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.identity.port.out.*;
import com.renttool.domain.reservation.Reservation;
import com.renttool.domain.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
@Component
class BookingBillingAdapter implements BillingBookings {
  private final ReservationRepository bookings;
  private final ToolRepository tools;
  private final AccountRepository accounts;
  private final ProviderProfileRepository profiles;
  BookingBillingAdapter(ReservationRepository bookings, ToolRepository tools, AccountRepository accounts, ProviderProfileRepository profiles) {
    this.bookings=bookings; this.tools=tools; this.accounts=accounts; this.profiles=profiles;
  }
  public Booking find(Long id) { return snapshot(bookings.findById(id).orElseThrow(() -> new NotFoundException("Reserva no encontrada"))); }
  public Booking lock(Long id) { return snapshot(bookings.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Reserva no encontrada"))); }
  private Booking snapshot(Reservation r) {
    var t = tools.findById(r.toolId()).orElseThrow();
    var buyer=accounts.findById(r.clientId()).orElseThrow();
    var seller=accounts.findById(t.providerId()).orElseThrow();
    var profile=profiles.findByAccountId(t.providerId());
    return new Booking(r.id(), r.clientId(),t.providerId(),r.status().name(),r.period().start(),r.period().end(),
        r.quantity(),r.dailyRate(),r.total(),t.name(),buyer.name(),buyer.email(),
        profile.map(p -> p.legalName()).orElse(seller.name()),
        profile.map(p -> p.nit().toString()).orElse("Sin NIT registrado"),
        profile.map(p -> p.address() + ", " + p.city()).orElse("Sin dirección registrada"));
  }
}
