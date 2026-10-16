package com.renttool.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.domain.reservation.*;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReservationRulesTest {
  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
  private final Participant client = new Participant(7L, Participant.Role.CLIENTE);
  private final Participant provider = new Participant(4L, Participant.Role.PROVEEDOR);
  private final RentableTool tool =
      new RentableTool(12L, 4L, "Taladro", new BigDecimal("25000"), true);

  private static RentalPeriod days(int from, int to) {
    return new RentalPeriod(DAY.plusDays(from), DAY.plusDays(to));
  }

  private Reservation pending() {
    return Reservation.request(client, tool, days(0, 3), 2, Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  void returnDayIsFreeForTheNextRental() {
    var tenToTwelve = days(0, 2);
    assertEquals(2, tenToTwelve.days());
    assertFalse(tenToTwelve.overlaps(days(2, 4)), "la devolución no choca con quien recoge");
    assertTrue(tenToTwelve.overlaps(days(1, 3)));
    assertTrue(tenToTwelve.overlaps(days(-1, 5)), "un rango que lo contiene también choca");
  }

  @Test
  void periodNeedsReturnAfterStartAndBoundedLength() {
    assertThrows(InvalidInputException.class, () -> new RentalPeriod(DAY, DAY));
    assertThrows(InvalidInputException.class, () -> new RentalPeriod(DAY, DAY.minusDays(1)));
    assertThrows(
        InvalidInputException.class,
        () -> new RentalPeriod(DAY, DAY.plusDays(RentalPeriod.MAX_DAYS + 1)));
  }

  @Test
  void requestFreezesRateAndComputesTotal() {
    var r = pending();
    assertEquals(ReservationStatus.SOLICITADA, r.status());
    assertEquals(new BigDecimal("150000.00"), r.total(), "25.000 × 3 días × 2 unidades");
  }

  @Test
  void onlyClientsRequestAndOnlyActiveTools() {
    assertThrows(
        ForbiddenOperationException.class,
        () -> Reservation.request(provider, tool, days(0, 1), 1, Instant.EPOCH, Instant.EPOCH));
    var retired = new RentableTool(12L, 4L, "Taladro", BigDecimal.TEN, false);
    assertThrows(
        ConflictException.class,
        () -> Reservation.request(client, retired, days(0, 1), 1, Instant.EPOCH, Instant.EPOCH));
  }

  @Test
  void acceptNeedsExactlyTheRequestedUnitsAndOnlyOnce() {
    assertThrows(ConflictException.class, () -> pending().accept(Set.of(1L)));
    var accepted = pending().accept(Set.of(1L, 2L));
    assertEquals(ReservationStatus.ACEPTADA, accepted.status());
    assertNull(accepted.respondBy());
    assertThrows(ConflictException.class, () -> accepted.accept(Set.of(1L, 2L)));
    assertThrows(ConflictException.class, () -> accepted.reject("tarde"));
  }

  @Test
  void acceptedCanBeCancelledOnlyBeforeItStarts() {
    var accepted = pending().accept(Set.of(1L, 2L));
    var cancelled = accepted.cancel(DAY.minusDays(1));
    assertEquals(ReservationStatus.CANCELADA, cancelled.status());
    assertTrue(cancelled.unitIds().isEmpty(), "cancelar libera las unidades");
    assertThrows(ConflictException.class, () -> accepted.cancel(DAY));
    assertThrows(ConflictException.class, () -> cancelled.cancel(DAY.minusDays(1)));
  }

  @Test
  void onlyPendingRequestsExpire() {
    assertEquals(ReservationStatus.VENCIDA, pending().expire().status());
    assertThrows(ConflictException.class, () -> pending().accept(Set.of(1L, 2L)).expire());
  }

  @Test
  void permissionsFollowOwnership() {
    var r = pending();
    var otherClient = new Participant(99L, Participant.Role.CLIENTE);
    var otherProvider = new Participant(98L, Participant.Role.PROVEEDOR);
    var admin = new Participant(1L, Participant.Role.ADMINISTRADOR);

    assertDoesNotThrow(() -> r.ensureVisibleTo(client, 4L));
    assertDoesNotThrow(() -> r.ensureVisibleTo(provider, 4L));
    assertDoesNotThrow(() -> r.ensureVisibleTo(admin, 4L));
    assertThrows(ForbiddenOperationException.class, () -> r.ensureVisibleTo(otherClient, 4L));
    assertThrows(ForbiddenOperationException.class, () -> r.ensureVisibleTo(otherProvider, 4L));

    assertDoesNotThrow(() -> r.ensureDecidableBy(provider, 4L));
    assertThrows(ForbiddenOperationException.class, () -> r.ensureDecidableBy(otherProvider, 4L));
    assertThrows(ForbiddenOperationException.class, () -> r.ensureDecidableBy(client, 4L));

    assertDoesNotThrow(() -> r.ensureCancellableBy(client));
    assertDoesNotThrow(() -> r.ensureCancellableBy(admin));
    assertThrows(ForbiddenOperationException.class, () -> r.ensureCancellableBy(provider));
  }

  @Test
  void eventsRecordWhoActedAndOnlyTheSystemIsAnonymous() {
    var at = java.time.Instant.parse("2026-10-15T15:00:00Z");
    var admin = new Participant(1L, Participant.Role.ADMINISTRADOR);
    var cancelled = ReservationEvent.by(admin, 9L, ReservationStatus.CANCELADA, at, " ");
    assertEquals(ReservationEvent.ActorRole.ADMINISTRADOR, cancelled.actorRole());
    assertEquals(1L, cancelled.actorId());
    assertNull(cancelled.detail());

    var expired = ReservationEvent.bySystem(9L, ReservationStatus.VENCIDA, at, null);
    assertNull(expired.actorId());
    assertThrows(
        InvalidInputException.class,
        () ->
            new ReservationEvent(
                null, 9L, ReservationStatus.VENCIDA, null, ReservationEvent.ActorRole.CLIENTE, at, null));
    assertThrows(
        ForbiddenOperationException.class,
        () -> ReservationEvent.by(Participant.anonymous(), 9L, ReservationStatus.CANCELADA, at, null));
  }

  @Test
  void forcedCancellationKeepsTheReasonAndNeverTouchesStartedRentals() {
    var today = LocalDate.of(2026, 10, 15);
    var accepted =
        new Reservation(
            1L, 7L, 12L, new RentalPeriod(today.plusDays(2), today.plusDays(4)), 1,
            BigDecimal.TEN, new BigDecimal("20.00"), ReservationStatus.ACEPTADA, Set.of(3L),
            Instant.parse("2026-10-14T10:00:00Z"), null, null);
    assertTrue(accepted.isCancellableToday(today));
    var cancelled = accepted.cancelBecause(today, "El proveedor se retiró");
    assertEquals(ReservationStatus.CANCELADA, cancelled.status());
    assertEquals("El proveedor se retiró", cancelled.note());
    assertTrue(cancelled.unitIds().isEmpty(), "libera las unidades");

    var started = today.plusDays(2);
    assertTrue(accepted.isInProgress(started));
    assertFalse(accepted.isCancellableToday(started));
    assertThrows(ConflictException.class, () -> accepted.cancelBecause(started, "x"));
  }
}
