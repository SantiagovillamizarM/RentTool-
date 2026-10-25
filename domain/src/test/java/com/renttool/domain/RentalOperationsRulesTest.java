package com.renttool.domain;

import com.renttool.domain.operations.*;
import com.renttool.domain.shared.exception.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RentalOperationsRulesTest {
  final Instant now = Instant.parse("2026-10-20T15:00:00Z");

  @Test void returnRequiresDamageDescription() {
    assertThrows(InvalidInputException.class, () ->
        new UnitReturn(1L, 2L, UnitReturn.Condition.DANADO, ""));
    assertThrows(InvalidInputException.class, () ->
        new UnitReturn(1L, 2L, UnitReturn.Condition.PERDIDO, null));
    assertDoesNotThrow(() -> new UnitReturn(1L, 2L, UnitReturn.Condition.BUENO, null));
  }

  @Test void returnCanOnlyBeRecordedOnceAndAfterDelivery() {
    var operation = new RentalOperation(1L, now, 3L, "Con accesorios", null, null, null);
    assertThrows(InvalidInputException.class, () -> operation.returnEquipment(3L, now.minusSeconds(1), null));
    var returned = operation.returnEquipment(3L, now.plusSeconds(60), "Revisado");
    assertFalse(returned.isOut());
    assertThrows(ConflictException.class, () -> returned.returnEquipment(3L, now.plusSeconds(120), null));
  }

  @Test void lateFeeChargesEachDayAfterTheReturnDate() {
    var due = java.time.LocalDate.of(2026, 10, 22);
    var rate = new java.math.BigDecimal("100");
    assertEquals(LateFee.NONE, LateFee.of(due, due, rate, 2));
    assertEquals(LateFee.NONE, LateFee.of(due, due.minusDays(1), rate, 2));
    var late = LateFee.of(due, due.plusDays(3), rate, 2);
    assertEquals(3, late.days());
    assertEquals(new java.math.BigDecimal("600.00"), late.amount());
  }

  @Test void onlyDamagedOrLostUnitsCanBeCharged() {
    var charge = new java.math.BigDecimal("5000");
    assertThrows(InvalidInputException.class, () ->
        new UnitReturn(1L, 2L, UnitReturn.Condition.BUENO, null, charge));
    assertThrows(InvalidInputException.class, () ->
        new UnitReturn(1L, 2L, UnitReturn.Condition.DANADO, "Golpe", charge.negate()));
    assertEquals(new java.math.BigDecimal("5000.00"),
        new UnitReturn(1L, 2L, UnitReturn.Condition.PERDIDO, "No apareció", charge).charge());
  }

  @Test void reviewRatingIsBetweenOneAndFiveStars() {
    assertThrows(InvalidInputException.class, () ->
        com.renttool.domain.review.Review.create(1L, 2L, 3L, 0, null, now));
    assertThrows(InvalidInputException.class, () ->
        com.renttool.domain.review.Review.create(1L, 2L, 3L, 6, null, now));
    assertNull(com.renttool.domain.review.Review.create(1L, 2L, 3L, 5, "  ", now).comment());
  }

  @Test void damageAndLossIdentifyTheUnit() {
    assertThrows(InvalidInputException.class, () ->
        new Incident(null, 1L, null, Incident.Type.DANO, "Golpe", 3L, now, null, null, null));
    assertDoesNotThrow(() ->
        new Incident(null, 1L, null, Incident.Type.OTRO, "Entrega tardía", 3L, now, null, null, null));
  }

  @Test void resolutionRequiresNotesAndPreservesTheOriginalReport() {
    var incident = new Incident(1L, 2L, 3L, Incident.Type.DANO, "Cable roto", 4L, now, null, null, null);
    assertTrue(incident.blocksUnit());
    assertThrows(InvalidInputException.class, () -> incident.resolve(4L, now, ""));
    var resolved = incident.resolve(4L, now.plusSeconds(60), "Cable reemplazado");
    assertEquals("Cable roto", resolved.description());
    assertFalse(resolved.blocksUnit());
    assertThrows(ConflictException.class, () -> resolved.resolve(4L, now.plusSeconds(120), "Otra vez"));
  }
}
