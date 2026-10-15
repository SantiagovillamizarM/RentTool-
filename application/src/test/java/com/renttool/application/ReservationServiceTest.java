package com.renttool.application;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.application.reservation.port.in.ManageReservationsUseCase.RequestCommand;
import com.renttool.application.reservation.port.out.*;
import com.renttool.application.reservation.service.*;
import com.renttool.application.shared.*;
import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Casos de uso de reservas probados sin Spring ni base de datos, con un reloj fijo. */
class ReservationServiceTest {
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
  private final MutableClock clock =
      new MutableClock(TODAY.atTime(9, 0).atZone(BOGOTA).toInstant());
  private final InMemoryReservations reservations = new InMemoryReservations();
  private final FakeInventory inventory = new FakeInventory();
  private final ReservationService service =
      new ReservationService(reservations, inventory, id -> "Persona " + id, clock);
  private final AvailabilityService availability =
      new AvailabilityService(reservations, inventory, clock);

  private final Participant client = new Participant(7L, Participant.Role.CLIENTE);
  private final Participant secondClient = new Participant(8L, Participant.Role.CLIENTE);
  private final Participant provider = new Participant(4L, Participant.Role.PROVEEDOR);

  private RequestCommand days(int from, int to, int quantity) {
    return new RequestCommand(12L, TODAY.plusDays(from), TODAY.plusDays(to), quantity);
  }

  private RentalPeriod period(int from, int to) {
    return new RentalPeriod(TODAY.plusDays(from), TODAY.plusDays(to));
  }

  @Test
  void onlyOperationalUnitsNotHeldCountAsAvailable() {
    // Unidades 1, 2 y 3 operativas; la 4 está en mantenimiento y no se ofrece.
    var first = service.request(client, days(5, 8, 2)).reservation();
    service.accept(provider, first.id());

    assertEquals(1, availability.check(12L, period(6, 7), 1).availableUnits());
    assertEquals(3, availability.check(12L, period(8, 10), 1).availableUnits(), "devolución el 8");
    assertEquals(
        new BigDecimal("150000.00"), availability.check(12L, period(8, 11), 2).total());
  }

  @Test
  void requestFailsWhenThereAreNotEnoughUnits() {
    assertThrows(ConflictException.class, () -> service.request(client, days(5, 8, 4)));
    assertThrows(
        InvalidInputException.class, () -> service.request(client, days(-1, 2, 1)), "pasado");
  }

  @Test
  void acceptRechecksAvailabilityAndAssignsDistinctUnits() {
    // Dos solicitudes compiten por las mismas fechas: la solicitud no aparta unidades.
    var a = service.request(client, days(5, 8, 2)).reservation();
    var b = service.request(secondClient, days(6, 9, 2)).reservation();

    var accepted = service.accept(provider, a.id()).reservation();
    assertEquals(Set.of(1L, 2L), accepted.unitIds());
    assertEquals(1, inventory.locks, "aceptar bloquea la herramienta");
    assertThrows(ConflictException.class, () -> service.accept(provider, b.id()));

    service.cancel(client, a.id());
    assertEquals(
        2, service.accept(provider, b.id()).reservation().unitIds().size(), "cancelar libera");
  }

  @Test
  void onlyTheToolOwnerDecides() {
    var r = service.request(client, days(5, 6, 1)).reservation();
    var stranger = new Participant(50L, Participant.Role.PROVEEDOR);
    assertThrows(ForbiddenOperationException.class, () -> service.accept(stranger, r.id()));
    assertThrows(ForbiddenOperationException.class, () -> service.reject(client, r.id(), "no"));
    assertEquals(
        ReservationStatus.RECHAZADA,
        service.reject(provider, r.id(), "En mantenimiento").reservation().status());
  }

  @Test
  void listingDependsOnRole() {
    service.request(client, days(5, 6, 1));
    service.request(secondClient, days(5, 6, 1));
    var page = new PageQuery(0, 10);
    assertEquals(1, service.list(client, page).totalElements());
    assertEquals(2, service.list(provider, page).totalElements());
    assertEquals(
        0, service.list(new Participant(50L, Participant.Role.PROVEEDOR), page).totalElements());
    assertThrows(
        ForbiddenOperationException.class, () -> service.list(Participant.anonymous(), page));
  }

  @Test
  void unansweredRequestsExpire() {
    var soon = service.request(client, days(0, 2, 1)).reservation();
    var later = service.request(client, days(5, 6, 1)).reservation();
    assertEquals(
        TODAY.plusDays(1).atStartOfDay(BOGOTA).toInstant(),
        soon.respondBy(),
        "si empieza hoy, el plazo termina con el día");

    clock.advance(Duration.ofHours(16));
    assertEquals(1, service.expireOverdue());
    assertEquals(ReservationStatus.VENCIDA, reservations.findById(soon.id()).orElseThrow().status());
    assertTrue(reservations.findById(later.id()).orElseThrow().isPending());

    clock.advance(Duration.ofHours(9));
    assertEquals(1, service.expireOverdue(), "24 horas después de solicitarla");
  }

  private static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    public ZoneId getZone() {
      return BOGOTA;
    }

    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    public Instant instant() {
      return now;
    }
  }

  private static class FakeInventory implements RentalInventory {
    private final RentableTool tool =
        new RentableTool(12L, 4L, "Taladro", new BigDecimal("25000"), true);
    int locks;

    public Optional<RentableTool> find(Long toolId) {
      return Optional.of(tool).filter(t -> t.id().equals(toolId));
    }

    public Optional<RentableTool> lockForAllocation(Long toolId) {
      locks++;
      return find(toolId);
    }

    public List<Long> operationalUnitIds(Long toolId) {
      return List.of(1L, 2L, 3L);
    }

    public List<Long> toolIdsOf(Long providerId) {
      return providerId.equals(4L) ? List.of(12L) : List.of();
    }
  }

  private static class InMemoryReservations implements ReservationRepository {
    private final Map<Long, Reservation> data = new LinkedHashMap<>();
    private long sequence;

    public Optional<Reservation> findById(Long id) {
      return Optional.ofNullable(data.get(id));
    }

    public Optional<Reservation> findByIdForUpdate(Long id) {
      return findById(id);
    }

    public Reservation save(Reservation r) {
      var stored =
          r.id() != null
              ? r
              : new Reservation(
                  ++sequence, r.clientId(), r.toolId(), r.period(), r.quantity(), r.dailyRate(),
                  r.total(), r.status(), r.unitIds(), r.requestedAt(), r.respondBy(), r.note());
      data.put(stored.id(), stored);
      return stored;
    }

    public Set<Long> unitsHeldDuring(Long toolId, RentalPeriod period) {
      var held = new HashSet<Long>();
      data.values().stream()
          .filter(r -> r.toolId().equals(toolId))
          .filter(r -> ReservationStatus.HOLDING_UNITS.contains(r.status()))
          .filter(r -> r.period().overlaps(period))
          .forEach(r -> held.addAll(r.unitIds()));
      return held;
    }

    public PageResult<Reservation> findByClient(Long clientId, PageQuery page) {
      return page(data.values().stream().filter(r -> r.clientId().equals(clientId)).toList());
    }

    public PageResult<Reservation> findByTools(Collection<Long> toolIds, PageQuery page) {
      return page(data.values().stream().filter(r -> toolIds.contains(r.toolId())).toList());
    }

    public PageResult<Reservation> findAll(PageQuery page) {
      return page(List.copyOf(data.values()));
    }

    public List<Long> findPendingIdsDueBy(Instant now) {
      return data.values().stream()
          .filter(Reservation::isPending)
          .filter(r -> !r.respondBy().isAfter(now))
          .map(Reservation::id)
          .toList();
    }

    private static PageResult<Reservation> page(List<Reservation> all) {
      return new PageResult<>(all, all.size(), 1, 0);
    }
  }
}
