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
