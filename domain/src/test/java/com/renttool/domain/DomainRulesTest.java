package com.renttool.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.renttool.domain.inventory.*;
import com.renttool.domain.shared.exception.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DomainRulesTest {
  @Test
  void tariffMustBePositive() {
    assertThrows(
        InvalidInputException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.ZERO, true));
    assertThrows(
        InvalidInputException.class,
        () -> new Tool(1L, 1L, 1L, "Taladro", "Detalle", BigDecimal.valueOf(-1), true));
  }

  @Test
  void onlyProvidersCanPublish() {
    var client = new Actor(5L, false, false);
    assertThrows(
        ForbiddenOperationException.class,
        () -> Tool.publish(client, 1L, "Taladro", "Detalle", BigDecimal.TEN));
  }

  @Test
  void onlyOwnerOrAdministratorCanManageTool() {
    var tool = new Tool(1L, 10L, 1L, "Taladro", "Detalle", BigDecimal.TEN, true);
    assertDoesNotThrow(() -> tool.ensureManageableBy(new Actor(10L, true, false)));
    assertDoesNotThrow(() -> tool.ensureManageableBy(new Actor(1L, false, true)));
    assertThrows(
        ForbiddenOperationException.class,
        () -> tool.ensureManageableBy(new Actor(99L, true, false)));
  }

  @Test
  void imageLocationMustBeHttpsOrInternal() {
    assertThrows(
        InvalidInputException.class, () -> ToolImage.attach(1L, "javascript:alert(1)"));
    assertDoesNotThrow(() -> ToolImage.attach(1L, "https://example.com/a.png"));
  }

  @Test
  void newMainImageGoesFirstAndNewExtraImagesGoLast() {
    var existing =
        java.util.List.of(
            new ToolImage(1L, 7L, "/uploads/a.png", 0), new ToolImage(2L, 7L, "/uploads/b.png", 3));
    assertEquals(-1, ToolImage.asMain(7L, "/uploads/c.png", existing).position());
    assertEquals(4, ToolImage.append(7L, "/uploads/d.png", existing).position());
    assertEquals(0, ToolImage.asMain(7L, "/uploads/e.png", java.util.List.of()).position());
  }

  @Test
  void unitCodesComeFromTheToolNameAndSkipTakenOnes() {
    assertEquals("TAL-01", Unit.nextCode("Taladro percutor", java.util.Set.of()));
    assertEquals("CAU-03", Unit.nextCode("Cautín", java.util.Set.of("CAU-01", "CAU-02")));
    assertEquals("NIV-01", Unit.nextCode("Nivel láser", java.util.Set.of()), "sin tildes");
    assertEquals("UNI-01", Unit.nextCode("X1", java.util.Set.of()), "nombre muy corto");
  }
}
